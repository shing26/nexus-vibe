<#
.SYNOPSIS
  Drives benchmark/jmeter/ai-review-loadtest.jmx and turns the .jtl into a committed summary.

.DESCRIPTION
  The load test answers one question: what does the nexus-async pool do when the LLM is the
  bottleneck? The numbers used to live in prose in docs/research/async-pool-loadtest.md, typed
  by hand from a terminal nobody else could inspect. This script produces them instead, from a
  .jtl, and writes both a machine-readable and a human-readable copy next to it.

  It is deliberately not wired into CI: a faithful run needs a running stack plus a live Ollama,
  and it takes about six minutes. See docs/tickets/evidence-and-gates.md.

  The .jtl and the JMeter HTML report are machine-local (see .gitignore). The two files under
  results/ are the artifacts worth committing: they are small, dated, and cite the command that
  produced them.

.PARAMETER AdminPassword
  Password for the account the run authenticates as. On a stack seeded by the demo data this is
  the demo password; on a production-shaped stack it is BOOTSTRAP_ADMIN_PASSWORD. Read it from
  .env rather than typing it here - it must not land in a committed file.

.EXAMPLE
  pwsh -File benchmark/jmeter/run-loadtest.ps1 -BasePort 8080 -AdminPassword $env:BOOTSTRAP_ADMIN_PASSWORD

.EXAMPLE
  # A 90-second rehearsal instead of the full six-minute ladder.
  pwsh -File benchmark/jmeter/run-loadtest.ps1 -Wave1Seconds 60 -Wave2Seconds 30 -Wave3Seconds 30 `
    -ListSeconds 90 -Wave2Delay 20 -Wave3Delay 40 -Tag rehearsal
#>
[CmdletBinding()]
param(
    [string] $BaseHost = 'localhost',
    [int]    $BasePort = 18080,
    [string] $AdminUser = 'admin',
    [string] $AdminPassword = '123456',

    [string] $JmeterHome,
    [string] $Plan = (Join-Path $PSScriptRoot 'ai-review-loadtest.jmx'),
    [string] $OutDir = (Join-Path $PSScriptRoot 'results'),
    [string] $Tag = 'loadtest',

    [int] $Wave1Seconds = 300,
    [int] $Wave2Seconds = 240,
    [int] $Wave3Seconds = 180,
    [int] $ListSeconds  = 360,
    [int] $Wave2Delay   = 60,
    [int] $Wave3Delay   = 120,

    # Best-effort: reads the terminal review state out of the running database. Skipped when the
    # stack is not reachable through docker compose, because a summary without this column is
    # still a summary and a failed exec is not a failed load test.
    [string] $ComposeProject = 'nexus-vibe',
    [switch] $SkipDbCheck,
    [switch] $KeepArtifacts
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Write-Step { param([string] $Message) Write-Host "==> $Message" -ForegroundColor Cyan }
function Write-Fail { param([string] $Message) Write-Host "FAIL: $Message" -ForegroundColor Red }

function Resolve-Jmeter {
    param([string] $Explicit)
    $candidates = @()
    if ($Explicit) { $candidates += (Join-Path $Explicit 'bin\jmeter.bat'); $candidates += (Join-Path $Explicit 'bin/jmeter') }
    if ($env:JMETER_HOME) { $candidates += (Join-Path $env:JMETER_HOME 'bin\jmeter.bat'); $candidates += (Join-Path $env:JMETER_HOME 'bin/jmeter') }
    $candidates += 'C:\Users\Shing\jmeter-test\tools\apache-jmeter-5.6.3\bin\jmeter.bat'
    foreach ($c in $candidates) { if ($c -and (Test-Path -LiteralPath $c)) { return (Resolve-Path -LiteralPath $c).Path } }
    $onPath = Get-Command jmeter -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    throw "JMeter not found. Pass -JmeterHome, set JMETER_HOME, or put jmeter on PATH."
}

function Invoke-Json {
    param([string] $Method, [string] $Uri, [hashtable] $Headers, [string] $Body)
    try {
        $params = @{ Method = $Method; Uri = $Uri; TimeoutSec = 20; ErrorAction = 'Stop' }
        if ($Headers) { $params.Headers = $Headers }
        if ($Body) { $params.Body = $Body; $params.ContentType = 'application/json' }
        return Invoke-RestMethod @params
    } catch {
        return $null
    }
}

function Get-Percentile {
    param([double[]] $Sorted, [double] $Percent)
    if (-not $Sorted -or $Sorted.Count -eq 0) { return $null }
    $index = [Math]::Floor(($Percent / 100.0) * ($Sorted.Count - 1))
    return [int] $Sorted[[int] $index]
}

function Get-CsvPercentile {
    param($Rows, [string] $Column, [double] $Percent)
    $values = @($Rows | ForEach-Object { [double] $_.$Column } | Sort-Object)
    return Get-Percentile -Sorted $values -Percent $Percent
}

function Format-Rate {
    param([int] $Numerator, [int] $Denominator)
    if ($Denominator -eq 0) { return '0.0%' }
    return ('{0:N1}%' -f (100.0 * $Numerator / $Denominator))
}

# ---------------------------------------------------------------- environment

$baseUrl = "http://${BaseHost}:${BasePort}"
$jmeter = Resolve-Jmeter -Explicit $JmeterHome
Write-Step "JMeter: $jmeter"
Write-Step "Target: $baseUrl"

if (-not (Test-Path -LiteralPath $Plan)) { throw "Test plan not found: $Plan" }

Write-Step 'Preflight: /actuator/health'
$health = Invoke-Json -Method 'GET' -Uri "$baseUrl/actuator/health"
if (-not $health) {
    throw "No answer from $baseUrl/actuator/health. Start the stack (or point -BasePort at it) before running a load test."
}
Write-Host "    health=$($health.status)"

Write-Step 'Preflight: the account the run authenticates as'
$loginBody = @{ username = $AdminUser; password = $AdminPassword } | ConvertTo-Json -Compress
$login = Invoke-Json -Method 'POST' -Uri "$baseUrl/api/v1/auth/login" -Body $loginBody
if (-not $login -or -not $login.data -or -not $login.data.token) {
    throw "Login as '$AdminUser' did not return a token. A run without a token measures 401s, not the async pool. Check -AdminUser/-AdminPassword."
}
$authToken = $login.data.token
Write-Host "    token acquired for role=$($login.data.role)"

# ---------------------------------------------------------------- run

$stamp = Get-Date -Format 'yyyyMMdd'
$startedAt = (Get-Date).ToUniversalTime()
$runDir = Join-Path $OutDir 'raw'
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$jtl = Join-Path $runDir "$Tag-$stamp.jtl"
$reportDir = Join-Path $runDir "$Tag-$stamp-report"
if (Test-Path -LiteralPath $reportDir) { Remove-Item -LiteralPath $reportDir -Recurse -Force }
if (Test-Path -LiteralPath $jtl) { Remove-Item -LiteralPath $jtl -Force }

$jmeterArgs = @(
    '-n',
    '-t', $Plan,
    '-l', $jtl,
    '-e', '-o', $reportDir,
    "-JBASE_HOST=$BaseHost",
    "-JBASE_PORT=$BasePort",
    "-JADMIN_USER=$AdminUser",
    "-JADMIN_PASSWORD=$AdminPassword",
    "-JauthToken=$authToken",
    "-JWAVE1_SECONDS=$Wave1Seconds",
    "-JWAVE2_SECONDS=$Wave2Seconds",
    "-JWAVE3_SECONDS=$Wave3Seconds",
    "-JLIST_SECONDS=$ListSeconds",
    "-JWAVE2_DELAY=$Wave2Delay",
    "-JWAVE3_DELAY=$Wave3Delay"
)

$plannedSeconds = [Math]::Max(
    [Math]::Max($Wave1Seconds, $Wave2Delay + $Wave2Seconds),
    [Math]::Max($Wave3Delay + $Wave3Seconds, $ListSeconds))
Write-Step "Running the ladder (about $plannedSeconds s of wall clock)"

& $jmeter @jmeterArgs
$jmeterExit = $LASTEXITCODE
if ($jmeterExit -ne 0) {
    Write-Fail "JMeter exited $jmeterExit; the .jtl may be incomplete and no summary was written."
    exit $jmeterExit
}
if (-not (Test-Path -LiteralPath $jtl)) { throw "JMeter reported success but $jtl does not exist." }

# ---------------------------------------------------------------- parse

Write-Step 'Parsing the .jtl'
$rows = Import-Csv -LiteralPath $jtl
if (-not $rows -or $rows.Count -eq 0) { throw "The .jtl is empty; nothing to summarise." }

$total = $rows.Count
$failed = @($rows | Where-Object { $_.success -eq 'false' }).Count
$elapsedAll = @($rows | ForEach-Object { [double] $_.elapsed } | Sort-Object)

$statusHistogram = [ordered]@{}
foreach ($code in ($rows | Group-Object responseCode | Sort-Object { [int] $_.Name })) {
    $statusHistogram[[string] $code.Name] = $code.Count
}

$byLabel = @()
foreach ($group in ($rows | Group-Object label)) {
    $ok = @($group.Group | Where-Object { $_.success -eq 'true' })
    $byLabel += [ordered]@{
        label   = $group.Name
        samples = $group.Count
        errors  = $group.Count - $ok.Count
        errorRate = Format-Rate -Numerator ($group.Count - $ok.Count) -Denominator $group.Count
        successP50 = Get-CsvPercentile -Rows $ok -Column 'elapsed' -Percent 50
        successP99 = Get-CsvPercentile -Rows $ok -Column 'elapsed' -Percent 99
    }
}

# A 500 on POST /posts is counted but NOT attributed to the async pool. A saturated pool does not
# surface as a 500: the publisher catches the rejection and answers 200 with the post marked FAILED
# for reconciliation (verified 2026-09-23 - thousands of rejections, every one a 200). So a 500 here
# is an unhandled error, and the old "pool rejection path" label was withdrawn in
# docs/tickets/like-count-convergence.md. The rate limiter's 429s are counted separately on purpose -
# folding them in would let an edge-capped run look like pool saturation.
$postRows = @($rows | Where-Object { $_.label -like 'POST /posts*' })
$serverErrors = @($postRows | Where-Object { $_.responseCode -eq '500' }).Count
$rateLimited = @($rows | Where-Object { $_.responseCode -eq '429' }).Count

$dbTerminal = $null
if (-not $SkipDbCheck) {
    Write-Step 'Reading terminal review state from the database (best effort)'
    $since = $startedAt.ToString('yyyy-MM-dd HH:mm:ss')
    $sql = "SELECT ai_reviewed, COUNT(*) AS n FROM vibe_post WHERE create_time >= '$since' GROUP BY ai_reviewed ORDER BY ai_reviewed"
    $out = & docker compose -p $ComposeProject exec -T db sh -c "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql -N -s -h 127.0.0.1 -uroot nexus_campus -e `"$sql`"" 2>$null
    if ($LASTEXITCODE -eq 0 -and $out) {
        $dbTerminal = [ordered]@{}
        foreach ($line in $out) {
            if (-not $line) { continue }
            $parts = $line -split "`t"
            if ($parts.Count -ge 2) { $dbTerminal[$parts[0]] = [int] $parts[1] }
        }
    } else {
        Write-Host '    (skipped: docker compose exec did not answer)' -ForegroundColor DarkYellow
    }
}

# ---------------------------------------------------------------- write

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$jsonPath = Join-Path $OutDir "$Tag-$stamp.json"
$mdPath   = Join-Path $OutDir "$Tag-$stamp.md"

$summary = [ordered]@{
    tag           = $Tag
    date          = $stamp
    startedAtUtc  = $startedAt.ToString('o')
    finishedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    target        = $baseUrl
    plan          = 'benchmark/jmeter/ai-review-loadtest.jmx'
    jmeter        = (& $jmeter --version 2>&1 | Select-Object -First 1)
    ladder        = [ordered]@{
        wave1Seconds = $Wave1Seconds
        wave2Seconds = $Wave2Seconds
        wave2Delay   = $Wave2Delay
        wave3Seconds = $Wave3Seconds
        wave3Delay   = $Wave3Delay
        listSeconds  = $ListSeconds
    }
    totals        = [ordered]@{
        samples   = $total
        failed    = $failed
        errorRate = Format-Rate -Numerator $failed -Denominator $total
    }
    latencyMs     = [ordered]@{
        p50 = Get-Percentile -Sorted $elapsedAll -Percent 50
        p90 = Get-Percentile -Sorted $elapsedAll -Percent 90
        p99 = Get-Percentile -Sorted $elapsedAll -Percent 99
    }
    responseCodes = $statusHistogram
    serverErrors  = [ordered]@{
        http500OnPostPosts = $serverErrors
        note = '500 on POST /posts is an unhandled error, not the ExecutorService rejection path: a saturated pool answers 200 with the post marked FAILED for reconciliation. The earlier pool-rejection attribution was withdrawn in docs/tickets/like-count-convergence.md.'
    }
    rateLimited429 = $rateLimited
    byLabel        = $byLabel
    dbTerminalStates = $dbTerminal
    artifacts     = [ordered]@{
        jtl = if ($KeepArtifacts) { $jtl } else { 'not committed (see .gitignore)' }
        html = if ($KeepArtifacts) { $reportDir } else { 'not committed (see .gitignore)' }
    }
}

$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$md = New-Object System.Text.StringBuilder
[void] $md.AppendLine("# Load-test summary: $Tag ($stamp)")
[void] $md.AppendLine()
[void] $md.AppendLine("Produced by ``pwsh -File benchmark/jmeter/run-loadtest.ps1`` on $stamp, against ``$baseUrl``.")
[void] $md.AppendLine("Ladder: wave1 ${Wave1Seconds}s, wave2 ${Wave2Seconds}s after ${Wave2Delay}s, wave3 ${Wave3Seconds}s after ${Wave3Delay}s, list queries ${ListSeconds}s.")
[void] $md.AppendLine()
[void] $md.AppendLine('| Metric | Value |')
[void] $md.AppendLine('| --- | --- |')
[void] $md.AppendLine("| Total samples | $total |")
[void] $md.AppendLine("| Errors | $failed ($(Format-Rate -Numerator $failed -Denominator $total)) |")
[void] $md.AppendLine("| Latency p50 / p90 / p99 (ms) | $(Get-Percentile -Sorted $elapsedAll -Percent 50) / $(Get-Percentile -Sorted $elapsedAll -Percent 90) / $(Get-Percentile -Sorted $elapsedAll -Percent 99) |")
[void] $md.AppendLine("| POST /posts answered 500 (unattributed - see note) | $serverErrors |")
[void] $md.AppendLine("| Requests answered 429 (rate limiter) | $rateLimited |")
if ($dbTerminal) {
    $pairs = ($dbTerminal.GetEnumerator() | ForEach-Object { "ai_reviewed=$($_.Key): $($_.Value)" }) -join ', '
    [void] $md.AppendLine("| Terminal review state | $pairs |")
} else {
    [void] $md.AppendLine('| Terminal review state | not measured (docker compose exec unavailable) |')
}
[void] $md.AppendLine()
[void] $md.AppendLine('> A 500 on `POST /posts` is **not** the async-pool rejection path. The publisher catches a')
[void] $md.AppendLine('> saturated pool and answers 200 with the post marked FAILED for reconciliation; a re-run on')
[void] $md.AppendLine('> 2026-09-23 logged thousands of rejections and every one answered 200. A 500 here is an')
[void] $md.AppendLine('> unhandled error, so it is counted but not attributed.')
[void] $md.AppendLine()
[void] $md.AppendLine('## Response codes')
[void] $md.AppendLine()
[void] $md.AppendLine('| Code | Count |')
[void] $md.AppendLine('| --- | --- |')
foreach ($kv in $statusHistogram.GetEnumerator()) { [void] $md.AppendLine("| $($kv.Key) | $($kv.Value) |") }
[void] $md.AppendLine()
[void] $md.AppendLine('## By sampler')
[void] $md.AppendLine()
[void] $md.AppendLine('| Sampler | Samples | Errors | Error rate | Success p50 | Success p99 |')
[void] $md.AppendLine('| --- | --- | --- | --- | --- | --- |')
foreach ($row in $byLabel) {
    [void] $md.AppendLine("| $($row.label) | $($row.samples) | $($row.errors) | $($row.errorRate) | $($row.successP50) | $($row.successP99) |")
}
[void] $md.AppendLine()
[void] $md.AppendLine('## Reproduce')
[void] $md.AppendLine()
[void] $md.AppendLine('```powershell')
[void] $md.AppendLine("# The same ladder this file came from. ADMIN_PASSWORD is read from .env, never typed into the repo.")
[void] $md.AppendLine('pwsh -File benchmark/jmeter/run-loadtest.ps1 -BaseHost localhost -BasePort 8080 -AdminPassword $env:BOOTSTRAP_ADMIN_PASSWORD')
[void] $md.AppendLine('```')
[void] $md.AppendLine()
[void] $md.AppendLine('The ``.jtl`` and the JMeter HTML report stay on the machine that ran the test (see ``.gitignore``);')
[void] $md.AppendLine('this file and its ``.json`` twin are the artifacts that are committed.')

Set-Content -LiteralPath $mdPath -Value $md.ToString() -Encoding utf8

if (-not $KeepArtifacts) {
    if (Test-Path -LiteralPath $jtl) { Remove-Item -LiteralPath $jtl -Force }
    if (Test-Path -LiteralPath $reportDir) { Remove-Item -LiteralPath $reportDir -Recurse -Force }
}

Write-Step "Wrote $jsonPath"
Write-Step "Wrote $mdPath"
Write-Host ''
Get-Content -LiteralPath $mdPath
exit 0
