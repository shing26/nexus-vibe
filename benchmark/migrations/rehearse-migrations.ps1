<#
.SYNOPSIS
  Runs the three schema migrations up, down, and up again against a throwaway MySQL.

.DESCRIPTION
  The repository ships migrate-0005/0006/0007 and now ships a rollback for each, and nothing had
  ever run the pair together. A migration whose rollback has never been executed is a guess with a
  filename.

  The sequence is init.sql, down, up, down, up - not up first. init.sql already contains all three
  changes (the runbook's section 4 table says so), so the database has to be rewound to the
  pre-migration shape before "up" means anything. That rewind is the rollback, which is convenient:
  the rehearsal starts by exercising the thing it is checking.

  Every step asserts the schema shape from information_schema rather than trusting an exit code.
  A statement can succeed and change nothing.

  It runs against `-p nexus-migrate` on its own volumes, never the live database, and tears the
  project down with `down -v` at the end. The step-by-step transcript is written to
  benchmark/migrations/evidence/ (gitignored, like every other raw evidence directory here); the
  summary under benchmark/migrations/results/ is the part worth committing.

.EXAMPLE
  pwsh -File benchmark/migrations/rehearse-migrations.ps1
#>
[CmdletBinding()]
param(
    [string] $ComposeProject = 'nexus-migrate',
    [string] $DbContainer = 'nexus-migrate-db',
    [string] $OutDir = (Join-Path $PSScriptRoot 'results'),
    [string] $EvidenceDir = (Join-Path $PSScriptRoot 'evidence'),
    [switch] $KeepStack
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$composeFiles = @('-f', 'docker-compose.yml', '-f', 'benchmark/migrations/docker-compose.migrations.yml')

function Write-Step { param([string] $Message) Write-Host "==> $Message" -ForegroundColor Cyan }
function Write-Log {
    param([string] $Message)
    Add-Content -LiteralPath $script:logPath -Value $Message
}

function Invoke-Compose {
    param([string[]] $Arguments)
    $full = @('compose', '-p', $ComposeProject) + $composeFiles + $Arguments
    $output = & docker @full 2>&1
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
    $output = & docker cp $hostPath "${DbContainer}:$target" 2>&1
    if ($LASTEXITCODE -ne 0) { throw "docker cp failed for $RelativePath : $output" }
    $run = & docker exec $DbContainer sh -c "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql -h 127.0.0.1 -uroot nexus_campus < $target" 2>&1
    $exit = $LASTEXITCODE
    Write-Log "--- applied $RelativePath (exit $exit)"
    if ($run) { Write-Log ($run -join "`n") }
    if ($exit -ne 0) { throw "Applying $RelativePath failed: $run" }
}

function Invoke-SqlScalar {
    param([string] $Sql)
    $output = & docker exec $DbContainer sh -c "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql -N -s -h 127.0.0.1 -uroot nexus_campus -e `"$Sql`"" 2>&1
    if ($LASTEXITCODE -ne 0) { throw "SQL failed: $Sql -> $output" }
    return @($output)
}

function Wait-ForTcp {
    for ($i = 1; $i -le 60; $i++) {
        & docker exec $DbContainer sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysqladmin ping -h 127.0.0.1 -uroot --silent' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 2
    }
    throw "MySQL never answered over TCP. The image healthcheck can be healthy while TCP lags it; see docs/runbook/restore.md section 2."
}

$shapeSql = "SELECT CONCAT('column:', TABLE_NAME, '.', COLUMN_NAME) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA='nexus_campus' AND COLUMN_NAME IN ('email','review_lock_until','review_owner','review_attempts') " +
            # DISTINCT: STATISTICS has one row per index column, and idx_post_ai_sort covers three,
            # so without it the shape reads as three separate indexes.
            "UNION ALL SELECT DISTINCT CONCAT('index:', INDEX_NAME) FROM information_schema.STATISTICS " +
            "WHERE TABLE_SCHEMA='nexus_campus' AND INDEX_NAME='idx_post_ai_sort' ORDER BY 1"

$presentObjects = @(
    'column:sys_user.email',
    'column:vibe_post.review_attempts',
    'column:vibe_post.review_lock_until',
    'column:vibe_post.review_owner',
    'index:idx_post_ai_sort'
)

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

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$date = Get-Date -Format 'yyyyMMdd'
$script:logPath = Join-Path $EvidenceDir "rehearse-$stamp.log"
Set-Content -LiteralPath $script:logPath -Value "migration rehearsal $stamp" -Encoding utf8

$allPassed = $false
try {
    Write-Step "Starting $ComposeProject (db only)"
    Invoke-Compose @('up', '-d', 'db') | Out-Null
    Wait-ForTcp

    Write-Step 'init.sql state: all three migrations are already folded in'
    $ok1 = Assert-Shape -Step 'init.sql (fresh volume)' -Expected $presentObjects

    Write-Step 'Rewinding to the pre-migration schema with the rollback scripts'
    Invoke-SqlFile 'docker/mysql/rollback-0007-drop-review-lease.sql'
    Invoke-SqlFile 'docker/mysql/rollback-0006-drop-ai-sort-index.sql'
    Invoke-SqlFile 'docker/mysql/rollback-0005-drop-user-email.sql'
    $ok2 = Assert-Shape -Step 'after rollback 0007/0006/0005' -Expected @()

    Write-Step 'Applying the three migrations'
    Invoke-SqlFile 'docker/mysql/migrate-0005-add-user-email.sql'
    Invoke-SqlFile 'docker/mysql/migrate-0006-add-ai-sort-index.sql'
    Invoke-SqlFile 'docker/mysql/migrate-0007-add-review-lease.sql'
    $ok3 = Assert-Shape -Step 'after migrate 0005/0006/0007 (up)' -Expected $presentObjects

    Write-Step 'Rolling back again'
    Invoke-SqlFile 'docker/mysql/rollback-0007-drop-review-lease.sql'
    Invoke-SqlFile 'docker/mysql/rollback-0006-drop-ai-sort-index.sql'
    Invoke-SqlFile 'docker/mysql/rollback-0005-drop-user-email.sql'
    $ok4 = Assert-Shape -Step 'after rollback 0007/0006/0005 (down)' -Expected @()

    Write-Step 'Applying them once more - the sequence has to survive being run twice'
    Invoke-SqlFile 'docker/mysql/migrate-0005-add-user-email.sql'
    Invoke-SqlFile 'docker/mysql/migrate-0006-add-ai-sort-index.sql'
    Invoke-SqlFile 'docker/mysql/migrate-0007-add-review-lease.sql'
    $ok5 = Assert-Shape -Step 'after migrate 0005/0006/0007 (up again)' -Expected $presentObjects

    $allPassed = $ok1 -and $ok2 -and $ok3 -and $ok4 -and $ok5
}
finally {
    if (-not $KeepStack) {
        Write-Step "Tearing down $ComposeProject and its volumes"
        try { Invoke-Compose @('down', '-v', '--remove-orphans') | Out-Null } catch { Write-Host "    teardown: $($_.Exception.Message)" -ForegroundColor DarkYellow }
        $left = & docker volume ls --format '{{.Name}}' | Select-String $ComposeProject
        if ($left) { Write-Host "    WARNING: volumes still present: $($left -join ', ')" -ForegroundColor DarkYellow }
    }
}

$summary = [ordered]@{
    date = $date
    composeProject = $ComposeProject
    sequence = @('init.sql', 'rollback 0007/0006/0005', 'migrate 0005/0006/0007', 'rollback 0007/0006/0005', 'migrate 0005/0006/0007')
    lossyScripts = @(
        'docker/mysql/rollback-0005-drop-user-email.sql',
        'docker/mysql/rollback-0007-drop-review-lease.sql'
    )
    losslessScripts = @('docker/mysql/rollback-0006-drop-ai-sort-index.sql')
    steps = $steps
    passed = $allPassed
    evidence = "benchmark/migrations/evidence/rehearse-$stamp.log (not committed)"
}

$jsonPath = Join-Path $OutDir "migrations-$date.json"
$mdPath = Join-Path $OutDir "migrations-$date.md"
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$md = New-Object System.Text.StringBuilder
[void] $md.AppendLine("# Migration rollback rehearsal ($date)")
[void] $md.AppendLine()
[void] $md.AppendLine("Produced by ``pwsh -File benchmark/migrations/rehearse-migrations.ps1`` against the throwaway project ``$ComposeProject`` (its own volumes, removed at the end).")
[void] $md.AppendLine()
[void] $md.AppendLine('Sequence: ``init.sql`` -> rollback 0007/0006/0005 -> migrate 0005/0006/0007 -> rollback again -> migrate again.')
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
[void] $md.AppendLine('- ``rollback-0006-drop-ai-sort-index.sql`` is lossless: an index covers data, it does not hold it.')
[void] $md.AppendLine('- ``rollback-0005-drop-user-email.sql`` destroys every stored address, and ``rollback-0007-drop-review-lease.sql`` destroys the review attempt budget (a post at 4 of 5 attempts becomes indistinguishable from a fresh one). Both say so in their own header and both are preceded by a backup in docs/runbook/restore.md.')
[void] $md.AppendLine()
[void] $md.AppendLine('## What this does not prove')
[void] $md.AppendLine()
[void] $md.AppendLine('- The data loss is stated, not measured. The rehearsal runs on an empty schema, so nothing is actually lost; the lossy headers describe what a populated database would lose.')
[void] $md.AppendLine('- It does not prove the application still works against the rewound schema. A build that predates the lease columns is not started here.')
[void] $md.AppendLine('- It is not a migration ledger. There is still no schema-version table (DB-1 in docs/tickets/next-cycle-backlog.md stays deferred); the operator decides which migrations a volume has had, from the dump date and a live probe.')

Set-Content -LiteralPath $mdPath -Value $md.ToString() -Encoding utf8
Write-Step "Wrote $jsonPath"
Write-Step "Wrote $mdPath"
Get-Content -LiteralPath $mdPath

if (-not $allPassed) { exit 1 }
exit 0
