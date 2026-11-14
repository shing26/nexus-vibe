<#
.SYNOPSIS
  Runs every schema migration pair up, down, and up again against a throwaway MySQL.

.DESCRIPTION
  The repository ships migrate-NNNN / rollback-NNNN pairs, and a migration whose rollback has
  never been executed is a guess with a filename.

  The pairs are read from docker/mysql/, not listed here. Three structural rules come out of
  the 2026-11 round, when the script still hardcoded 0005-0007 and 0008/0009 had never been
  rehearsed by anyone:

    1. A migrate-NNNN without a matching rollback-NNNN (or the reverse) fails the run.
    2. Every pair must have a schema-shape declaration in $shapeByNumber below. The rehearsal
       asserts the schema from information_schema, so a pair with no declared shape cannot be
       rehearsed - the run refuses rather than silently asserting an empty shape. This is the
       rule that would have caught the 0008 gap at the 0008 round.
    3. The result file names the migrations it ran, so "a green rehearsal" always says which
       pairs were green.

  The sequence is init.sql, down, up, down, up - not up first. init.sql already contains every
  migration (its header table in docs/plans/pre-deployment-checklist.md says so), so the
  database has to be rewound to the pre-migration shape before "up" means anything. That rewind
  is the rollback, which is convenient: the rehearsal starts by exercising the thing it is
  checking.

  Every step asserts the schema shape from information_schema rather than trusting an exit
  code. A statement can succeed and change nothing.

  It runs against project `-p nexus-migrate` on its own volumes, never the live database, and
  tears the project down with `down -v` at the end. The step-by-step transcript is written to
  benchmark/migrations/evidence/ (gitignored, like every other raw evidence directory here);
  the summary under benchmark/migrations/results/ is the part worth committing.

  The repo's `.env` must exist at the repository root: the db service interpolates
  MYSQL_ROOT_PASSWORD / DB_USERNAME / DB_PASSWORD from it. The rehearsal never writes to the
  live volume, but compose still refuses to start without the values.

.EXAMPLE
  pwsh -File benchmark/migrations/rehearse-migrations.ps1
#>
[CmdletBinding()]
param(
    [string] $ComposeProject = 'nexus-migrate',
    [string] $DbContainer = 'nexus-migrate-db',
    [string] $OutDir = '',
    [string] $EvidenceDir = '',
    [switch] $KeepStack
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Defaults are computed here rather than in the param block: Windows PowerShell 5.1 does not
# evaluate $PSScriptRoot in a param default, which is how the first rehearsal attempt died
# before touching Docker.
if (-not $OutDir) { $OutDir = Join-Path $PSScriptRoot 'results' }
if (-not $EvidenceDir) { $EvidenceDir = Join-Path $PSScriptRoot 'evidence' }

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot '.env'))) {
    throw "No .env at $repoRoot - compose cannot interpolate the db credentials without it."
}

# Every migrate-*.sql under docker/mysql is discovered here and must pair with a
# rollback-*.sql of the same number. Discovery, not enumeration, is the point: the 0008 gap
# survived because the list of rehearsed migrations and the list of shipped migrations were
# two different lists.
$mysqlDir = Join-Path $repoRoot 'docker\mysql'
$migrations = @()
foreach ($file in (Get-ChildItem -LiteralPath $mysqlDir -Filter 'migrate-*.sql' | Sort-Object Name)) {
    if ($file.Name -notmatch '^migrate-(\d{4})-(.+)\.sql$') {
        throw "Unrecognised migration filename: $($file.Name)"
    }
    $number = $Matches[1]
    $rollback = Get-ChildItem -LiteralPath $mysqlDir -Filter "rollback-$number-*.sql" | Select-Object -First 1
    if (-not $rollback) {
        throw "migrate-$number has no rollback-$number-*.sql pair; write the rollback before rehearsing."
    }
    $migrations += [pscustomobject]@{
        number   = $number
        slug     = $Matches[2]
        migrate  = 'docker/mysql/' + $file.Name
        rollback = 'docker/mysql/' + $rollback.Name
    }
}
if ($migrations.Count -eq 0) { throw 'No migrate-*.sql pairs found under docker/mysql/.' }
foreach ($rollback in (Get-ChildItem -LiteralPath $mysqlDir -Filter 'rollback-*.sql')) {
    if ($rollback.Name -notmatch '^rollback-(\d{4})-') { continue }
    if (-not ($migrations | Where-Object number -eq $Matches[1])) {
        throw "$($rollback.Name) has no migrate-$($Matches[1])-*.sql pair."
    }
}

# What each migration introduces, as `column:table.column` / `index:name` probes. A new pair
# without an entry here fails the run instead of being rehearsed against an empty assertion.
$shapeByNumber = @{
    '0005' = @('column:sys_user.email')
    '0006' = @('index:idx_post_ai_sort')
    '0007' = @('column:vibe_post.review_attempts', 'column:vibe_post.review_lock_until', 'column:vibe_post.review_owner')
    '0008' = @('column:ai_review_log.comment_id', 'index:idx_ai_review_log_comment')
    '0009' = @('column:vibe_comment.sentiment')
}
foreach ($mig in $migrations) {
    if (-not $shapeByNumber[$mig.number]) {
        throw "No schema shape declared for migration $($mig.number). Add it to `$shapeByNumber so the rehearsal asserts what it applies."
    }
}
$allObjects = @($migrations | ForEach-Object { $shapeByNumber[$_.number] })

# One information_schema round trip per assertion, built from the declarations above. The
# column side uses OR pairs rather than a row-value IN, which not every MySQL 8 point release
# optimises; DISTINCT on the index side keeps idx_post_ai_sort (three columns in STATISTICS)
# from reading as three indexes.
$columnPredicates = @()
$indexNames = @()
foreach ($object in $allObjects) {
    if ($object -like 'column:*') {
        $parts = $object.Substring(7).Split('.')
        if ($parts.Count -ne 2) { throw "Shape object must be column:table.column: $object" }
        $columnPredicates += "(TABLE_NAME='$($parts[0])' AND COLUMN_NAME='$($parts[1])')"
    }
    elseif ($object -like 'index:*') {
        $indexNames += "'" + $object.Substring(6) + "'"
    }
    else {
        throw "Shape object must start with column: or index: : $object"
    }
}
$shapeSql = "SELECT CONCAT('column:', TABLE_NAME, '.', COLUMN_NAME) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA='nexus_campus' AND (" + ($columnPredicates -join ' OR ') + ')' +
            " UNION ALL SELECT DISTINCT CONCAT('index:', INDEX_NAME) FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA='nexus_campus' AND INDEX_NAME IN (" + ($indexNames -join ',') + ') ORDER BY 1'

$composeFiles = @('-f', 'docker-compose.yml', '-f', 'benchmark/migrations/docker-compose.migrations.yml')

function Write-Step { param([string] $Message) Write-Host "==> $Message" -ForegroundColor Cyan }
function Write-Log {
    param([string] $Message)
    Add-Content -LiteralPath $script:logPath -Value $Message
}

# Every docker call goes through here. Under $ErrorActionPreference = 'Stop', PowerShell 5.1
# turns each stderr line of a native command into an ErrorRecord and throws on the first one -
# and docker writes ordinary progress lines ("Network ... Creating") to stderr, so an up can be
# aborted mid-flight by its own chatter while the child process carries on. The preference is
# relaxed around the call and the exit code is checked instead, which is the actual contract.
function Invoke-Docker {
    param([string[]] $DockerArgs, [string] $Stdin = '')
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($Stdin) { return ($Stdin | & docker @DockerArgs 2>&1) }
        return (& docker @DockerArgs 2>&1)
    }
    finally { $ErrorActionPreference = $prev }
}

function Invoke-Compose {
    param([string[]] $Arguments)
    $full = @('compose', '-p', $ComposeProject) + $composeFiles + $Arguments
    $output = Invoke-Docker -DockerArgs $full
    if ($LASTEXITCODE -ne 0) { throw "docker compose $($Arguments -join ' ') failed: $output" }
    return $output
}

# SQL arrives inside the container as a file, then through the container's own shell redirect.
# Passing a script through PowerShell -> docker exec -> sh mangles quotes; a file crosses one
# boundary. Same lesson as the restore runbook.
function Invoke-SqlFile {
    param([string] $RelativePath)
    $hostPath = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $hostPath)) { throw "SQL file not found: $hostPath" }
    $target = '/tmp/' + (Split-Path $hostPath -Leaf)
    $output = Invoke-Docker -DockerArgs @('cp', $hostPath, "${DbContainer}:$target")
    if ($LASTEXITCODE -ne 0) { throw "docker cp failed for $RelativePath : $output" }
    $run = Invoke-Docker -DockerArgs @('exec', $DbContainer, 'sh', '-c', "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql -h 127.0.0.1 -uroot nexus_campus < $target")
    $exit = $LASTEXITCODE
    Write-Log "--- applied $RelativePath (exit $exit)"
    if ($run) { Write-Log ($run -join "`n") }
    if ($exit -ne 0) { throw "Applying $RelativePath failed: $run" }
}

function Invoke-SqlScalar {
    param([string] $Sql)
    # The shape SQL is full of single quotes, which do not survive an -e "..." argument through
    # the PowerShell -> docker -> sh quoting chain (the first assert run died on a mangled 1064).
    # Piped through stdin there is one boundary: docker -i hands the text to mysql untouched.
    $output = Invoke-Docker -DockerArgs @('exec', '-i', $DbContainer, 'sh', '-c', 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -N -s -h 127.0.0.1 -uroot nexus_campus') -Stdin $Sql
    if ($LASTEXITCODE -ne 0) { throw "SQL failed: $Sql -> $output" }
    return @($output)
}

function Wait-ForTcp {
    for ($i = 1; $i -le 60; $i++) {
        Invoke-Docker -DockerArgs @('exec', $DbContainer, 'sh', '-c', 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysqladmin ping -h 127.0.0.1 -uroot --silent') | Out-Null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 2
    }
    throw "MySQL never answered over TCP. The image healthcheck can be healthy while TCP lags it; see docs/runbook/restore.md section 2."
}

function Get-SchemaShape {
    return @(Invoke-SqlScalar -Sql $shapeSql) | Where-Object { $_ }
}

$steps = New-Object System.Collections.Generic.List[object]
function Assert-Shape {
    param([string] $Step, [string[]] $Expected)
    $observed = @(Get-SchemaShape)
    $sortedExpected = @($Expected | Sort-Object)
    $sortedObserved = @($observed | Sort-Object)
    $ok = ($sortedExpected -join '|') -eq ($sortedObserved -join '|')
    $steps.Add([ordered]@{
        step     = $Step
        expected = $sortedExpected
        observed = $sortedObserved
        passed   = $ok
    })
    Write-Log "=== $Step"
    Write-Log ("expected: " + ($sortedExpected -join ', '))
    Write-Log ("observed: " + ($sortedObserved -join ', '))
    Write-Log ("result:   " + $(if ($ok) { 'PASS' } else { 'FAIL' }))
    if ($ok) {
        Write-Host "    PASS  $Step" -ForegroundColor Green
    } else {
        Write-Host "    FAIL  $Step (expected $($sortedExpected -join ', '); observed $($sortedObserved -join ', '))" -ForegroundColor Red
    }
    return $ok
}

function Invoke-Migrations {
    foreach ($mig in ($migrations | Sort-Object number)) { Invoke-SqlFile $mig.migrate }
}

function Invoke-Rollbacks {
    foreach ($mig in ($migrations | Sort-Object number -Descending)) { Invoke-SqlFile $mig.rollback }
}

# The rollback files declare their own cost in their headers; read it rather than restate it.
function Get-LossClassification {
    param([string] $RelativePath)
    $hit = Select-String -LiteralPath (Join-Path $repoRoot $RelativePath) -Pattern 'LOSSY|LOSSLESS|Lossy|Lossless' | Select-Object -First 1
    if (-not $hit) { return [ordered]@{ kind = 'undeclared'; line = '' } }
    $kind = if ($hit.Line -match '(?i)lossless') { 'lossless' } else { 'lossy' }
    return [ordered]@{ kind = $kind; line = $hit.Line.TrimStart('-').Trim() }
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$date = Get-Date -Format 'yyyyMMdd'
$script:logPath = Join-Path $EvidenceDir "rehearse-$stamp.log"
Set-Content -LiteralPath $script:logPath -Value "migration rehearsal $stamp" -Encoding utf8

$migrationNames = @($migrations | ForEach-Object { "$($_.number)-$($_.slug)" })
$numberList = ($migrations.number | Sort-Object) -join '/'

Push-Location $repoRoot
$allPassed = $false
try {
    Write-Step "Starting $ComposeProject (db only)"
    Invoke-Compose @('up', '-d', 'db') | Out-Null
    Wait-ForTcp

    Write-Step "init.sql state: all $($migrations.Count) migrations are already folded in"
    $ok1 = Assert-Shape -Step 'init.sql (fresh volume)' -Expected $allObjects

    Write-Step "Rewinding to the pre-migration schema with the rollback scripts"
    Invoke-Rollbacks
    $ok2 = Assert-Shape -Step "after rollback $numberList" -Expected @()

    Write-Step "Applying the $($migrations.Count) migrations"
    Invoke-Migrations
    $ok3 = Assert-Shape -Step "after migrate $numberList (up)" -Expected $allObjects

    Write-Step 'Rolling back again'
    Invoke-Rollbacks
    $ok4 = Assert-Shape -Step "after rollback $numberList (down)" -Expected @()

    Write-Step 'Applying them once more - the sequence has to survive being run twice'
    Invoke-Migrations
    $ok5 = Assert-Shape -Step "after migrate $numberList (up again)" -Expected $allObjects

    $allPassed = $ok1 -and $ok2 -and $ok3 -and $ok4 -and $ok5
}
finally {
    Pop-Location
    if (-not $KeepStack) {
        Write-Step "Tearing down $ComposeProject and its volumes"
        try { Invoke-Compose @('down', '-v', '--remove-orphans') | Out-Null } catch { Write-Host "    teardown: $($_.Exception.Message)" -ForegroundColor DarkYellow }
        $left = Invoke-Docker -DockerArgs @('volume', 'ls', '--format', '{{.Name}}') | Select-String $ComposeProject
        if ($left) { Write-Host "    WARNING: volumes still present: $($left -join ', ')" -ForegroundColor DarkYellow }
    }
}

$rollbacks = @($migrations | ForEach-Object {
    $classification = Get-LossClassification -RelativePath $_.rollback
    [ordered]@{ file = $_.rollback; declared = $classification.kind; headerLine = $classification.line }
})

$summary = [ordered]@{
    date           = $date
    composeProject = $ComposeProject
    migrations     = $migrationNames
    sequence       = @('init.sql', "rollback $numberList", "migrate $numberList", "rollback $numberList", "migrate $numberList")
    rollbacks      = $rollbacks
    steps          = $steps
    passed         = $allPassed
    evidence       = "benchmark/migrations/evidence/rehearse-$stamp.log (not committed)"
}

$jsonPath = Join-Path $OutDir "migrations-$date.json"
$mdPath = Join-Path $OutDir "migrations-$date.md"
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$md = New-Object System.Text.StringBuilder
[void] $md.AppendLine("# Migration rollback rehearsal ($date)")
[void] $md.AppendLine()
[void] $md.AppendLine("Produced by ``pwsh -File benchmark/migrations/rehearse-migrations.ps1`` against the throwaway project ``$ComposeProject`` (its own volumes, removed at the end).")
[void] $md.AppendLine()
[void] $md.AppendLine("Migrations rehearsed: $($migrationNames -join ', ').")
[void] $md.AppendLine()
[void] $md.AppendLine("Sequence: ``init.sql`` -> rollback $numberList -> migrate $numberList -> rollback again -> migrate again.")
[void] $md.AppendLine()
[void] $md.AppendLine('| Step | Expected objects | Observed | Result |')
[void] $md.AppendLine('| --- | --- | --- | --- |')
foreach ($step in $steps) {
    $expected = if ($step.expected.Count -eq 0) { '(none)' } else { $step.expected -join '<br>' }
    $observed = if ($step.observed.Count -eq 0) { '(none)' } else { $step.observed -join '<br>' }
    [void] $md.AppendLine("| $($step.step) | $expected | $observed | $(if ($step.passed) { 'PASS' } else { 'FAIL' }) |")
}
[void] $md.AppendLine()
[void] $md.AppendLine("Overall: **$(if ($allPassed) { 'PASS' } else { 'FAIL' })**")
[void] $md.AppendLine()
[void] $md.AppendLine('## Lossy versus lossless')
[void] $md.AppendLine()
foreach ($rollback in $rollbacks) {
    [void] $md.AppendLine("- ``$($rollback.file)`` declares itself **$($rollback.declared)**: ``$($rollback.headerLine)``")
}
[void] $md.AppendLine()
[void] $md.AppendLine('## What this does not prove')
[void] $md.AppendLine()
[void] $md.AppendLine('- The data loss is stated, not measured. The rehearsal runs on an empty schema, so nothing is actually lost; the lossy headers describe what a populated database would lose.')
[void] $md.AppendLine('- It does not prove the application still works against the rewound schema. A build that predates a migration is not started here.')
[void] $md.AppendLine('- It is not a migration ledger. There is still no schema-version table (DB-1 in docs/tickets/next-cycle-backlog.md stays deferred); the operator decides which migrations a volume has had, from the dump date and a live probe.')

Set-Content -LiteralPath $mdPath -Value $md.ToString() -Encoding utf8
Write-Step "Wrote $jsonPath"
Write-Step "Wrote $mdPath"
Get-Content -LiteralPath $mdPath

if (-not $allPassed) { exit 1 }
exit 0
