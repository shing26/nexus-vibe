<#
.SYNOPSIS
  Repairs the escaped-entity artifacts the old ingress sanitizer (ADR-0021's jsoup wash) wrote
  into vibe_post.content and vibe_comment.content. Read-only unless -Apply is given.

.DESCRIPTION
  Until the 2026-11 core-loop round, XssHttpServletRequestWrapper ran every JSON body string
  through jsoup Safelist.relaxed, which HTML-escaped text as a side effect of parsing it as
  markup: a typed `a < b && c > d` was stored as `a &lt; b &amp;&amp; c &gt; d`. The ingress is
  byte-faithful now (ADR-0021), and this script repairs the rows already written under the old
  path.

  What is repaired - only the unambiguous artifacts, as substring REPLACEs applied in one
  statement per table:

    '&amp;&amp;' -> '&&'    (a typed && ; each & was escaped individually)
    '&lt;'       -> '<'
    '&gt;'       -> '>'

  A standalone '&amp;' is deliberately LEFT ALONE. In markdown it renders identically to '&',
  so unescaping it buys nothing, while unescaping it would corrupt the rows where the entity is
  load-bearing: inside a code span, '`&amp;`' renders as the literal text '&amp;' and would turn
  into '&' - a second, quieter data loss. The same protection makes the script idempotent by
  construction: a row whose author typed a literal '&lt;' is stored as '&amp;lt;', which contains
  none of the three artifacts and is never touched, on any run.

  What is NOT repaired: fragments jsoup deleted outright (a generic like Map<String, Value> whose
  brackets were consumed is gone from storage and unrecoverable), and columns outside the two
  content bodies (titles, prompt_metadata, comments' other text) - the repair scope is the
  frozen one: vibe_post.content and vibe_comment.content only.

  Hard rules, in order:

    1. Backup first. The script refuses to start unless the backup directory exists and holds at
       least one file. The pre-migration backup for this round is E:\nexus-backups\20261114-pre-migrate\
       (docs/runbook/restore.md documents how a backup is verified before it counts).
    2. Read-only by default. Without -Apply the script only counts and samples; nothing is written.
    3. -Apply proves its own idempotence: after the first pass it runs the identical pass again and
       fails if the second pass changed anything.
    4. The live repair is an operations step after merge sign-off, executed against the live
       container by the operator. Rehearsals run against a throwaway container only (see the
       rehearsal note in benchmark/migrations/rehearse-migrations.ps1 for how one is stood up).

  PowerShell 5.1 traps are inherited from rehearse-migrations.ps1: docker's stderr chatter must
  not meet $ErrorActionPreference = 'Stop' (Invoke-Docker relaxes it), and SQL with single quotes
  crosses exactly one boundary - piped through stdin to `docker exec -i`, never as an argument.

.EXAMPLE
  pwsh -File benchmark/migrations/fix-escaped-content-artifacts.ps1 -Container nexus-migrate-db
  pwsh -File benchmark/migrations/fix-escaped-content-artifacts.ps1 -Container nexus-migrate-db -Apply
#>
[CmdletBinding()]
param(
    # The running MySQL container the repair targets. Rehearse against a throwaway
    # container; the live application is the operator's post-merge step.
    [Parameter(Mandatory = $true)]
    [string] $Container,

    # Actually run the UPDATEs. Without this switch the run is read-only.
    [switch] $Apply,

    # The backup precondition. The 2026-11-14 pre-migration backup for this round.
    [string] $BackupDir = 'E:\nexus-backups\20261114-pre-migrate',

    [string] $Database = 'nexus_campus',
    [string] $OutDir = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Computed here, not in the param block: Windows PowerShell 5.1 does not evaluate $PSScriptRoot
# in a param default (the same trap rehearse-migrations.ps1 documents).
if (-not $OutDir) { $OutDir = Join-Path $PSScriptRoot 'results' }

# ---- the docker/SQL helpers, verbatim in spirit from rehearse-migrations.ps1 -------------
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

# SQL crosses one boundary only: piped through stdin, so single quotes survive untouched.
# The sh command is built in an intermediate variable: PS 5.1 passing an inline-concatenated
# string ('...' + $Database) straight into an array literal drops the tail on the way to the
# native command, which reached mysql as "No database selected" on the first rehearsal.
function Invoke-SqlScalar {
    param([string] $Sql)
    $shCommand = 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -N -s -h 127.0.0.1 -uroot --default-character-set=utf8mb4 ' + $Database
    $output = Invoke-Docker -DockerArgs @('exec', '-i', $Container,
        'sh', '-c', $shCommand) -Stdin $Sql
    if ($LASTEXITCODE -ne 0) { throw "SQL failed: $Sql -> $output" }
    return @($output)
}

# ---- rule 1: no repair, not even a rehearsal, without a verified-looking backup ----------
if (-not (Test-Path -LiteralPath $BackupDir)) {
    throw "Backup directory '$BackupDir' does not exist. Take and verify a backup first - see docs/runbook/restore.md. The pre-migration backup for this round is E:\nexus-backups\20261114-pre-migrate\."
}
$backupFiles = @(Get-ChildItem -LiteralPath $BackupDir -File)
if ($backupFiles.Count -eq 0) {
    throw "Backup directory '$BackupDir' is empty. A backup that does not exist cannot be restored from."
}

# ---- container sanity --------------------------------------------------------------------
# The state and the labels are two separate inspect calls: a --format template that embeds
# double quotes ("com.docker.compose.project" as an index key) is re-quoted by PowerShell 5.1
# on its way to docker, and docker then parses 'com' as a template function. {{json}} needs
# no embedded quotes, so the labels come across as JSON and are parsed here instead.
$inspect = Invoke-Docker -DockerArgs @('inspect', '--format', '{{.State.Running}}', $Container)
if ($LASTEXITCODE -ne 0) {
    throw "Container '$Container' not found or not inspectable: $inspect"
}
if ("$inspect".Trim() -ne 'true') {
    throw "Container '$Container' is not running."
}
$labelsJson = Invoke-Docker -DockerArgs @('inspect', '--format', '{{json .Config.Labels}}', $Container)
if ($LASTEXITCODE -ne 0) {
    throw "Could not read labels from container '$Container': $labelsJson"
}
$composeProject = try { (($labelsJson | Select-Object -First 1) | ConvertFrom-Json).'com.docker.compose.project' } catch { $null }
if ($composeProject -eq 'nexus-campus' -and -not $Apply) {
    # Not a refusal: the live application is a legitimate post-merge operator step. But a
    # read-only probe that accidentally names the live container should say so loudly.
    Write-Host "    NOTE: target is the live compose project 'nexus-campus' (read-only run)." -ForegroundColor DarkYellow
}

# ---- the repair, one statement per table -------------------------------------------------
# REPLACE applies innermost-first: &amp;&amp; (both &s escaped) before the single-character
# artifacts. No replacement can create another artifact, so order is safety, not necessity.
$repairSql = @"
UPDATE vibe_post
SET content = REPLACE(REPLACE(REPLACE(content, '&amp;&amp;', '&&'), '&lt;', '<'), '&gt;', '>')
WHERE content LIKE '%&lt;%' OR content LIKE '%&gt;%' OR content LIKE '%&amp;&amp;%';
SELECT ROW_COUNT();
UPDATE vibe_comment
SET content = REPLACE(REPLACE(REPLACE(content, '&amp;&amp;', '&&'), '&lt;', '<'), '&gt;', '>')
WHERE content LIKE '%&lt;%' OR content LIKE '%&gt;%' OR content LIKE '%&amp;&amp;%';
SELECT ROW_COUNT();
"@

$countSql = @"
SELECT 'vibe_post.artifact', COUNT(*) FROM vibe_post
 WHERE content LIKE '%&lt;%' OR content LIKE '%&gt;%' OR content LIKE '%&amp;&amp;%'
 UNION ALL
SELECT 'vibe_comment.artifact', COUNT(*) FROM vibe_comment
 WHERE content LIKE '%&lt;%' OR content LIKE '%&gt;%' OR content LIKE '%&amp;&amp;%'
 UNION ALL
SELECT 'vibe_post.total', COUNT(*) FROM vibe_post
 UNION ALL
SELECT 'vibe_comment.total', COUNT(*) FROM vibe_comment;
"@

$sampleSql = @"
SELECT 'vibe_post', id, LEFT(REPLACE(content, CHAR(10), ' / '), 100) FROM vibe_post
 WHERE content LIKE '%&lt;%' OR content LIKE '%&gt;%' OR content LIKE '%&amp;&amp;%' LIMIT 5;
SELECT 'vibe_comment', id, LEFT(REPLACE(content, CHAR(10), ' / '), 100) FROM vibe_comment
 WHERE content LIKE '%&lt;%' OR content LIKE '%&gt;%' OR content LIKE '%&amp;&amp;%' LIMIT 5;
"@

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$date = Get-Date -Format 'yyyy-MM-dd'

function Convert-CountLines {
    param([string[]] $Lines)
    $map = @{}
    foreach ($line in $Lines) {
        if ($line -match '^([^\t]+)\t(\d+)$') { $map[$Matches[1]] = [int]$Matches[2] }
    }
    return $map
}

Write-Host "==> Backup check: $BackupDir ($($backupFiles.Count) file(s)) - OK"
Write-Host "==> Target container: $Container, mode: $(if ($Apply) { 'APPLY' } else { 'dry-run (read-only)' })"

$before = Convert-CountLines (Invoke-SqlScalar -Sql $countSql)
if ($before.Count -eq 0) { throw "Could not read artifact counts from $Container." }
Write-Host ("    before: vibe_post {0}/{1} rows with artifacts, vibe_comment {2}/{3}" -f `
    $before['vibe_post.artifact'], $before['vibe_post.total'], `
    $before['vibe_comment.artifact'], $before['vibe_comment.total'])

$samplesBefore = @(Invoke-SqlScalar -Sql $sampleSql)

$report = New-Object System.Text.StringBuilder
[void] $report.AppendLine("# Escaped-content artifact repair ($date)")
[void] $report.AppendLine()
[void] $report.AppendLine("Container ``$Container``; mode: **$(if ($Apply) { 'APPLY' } else { 'dry-run (read-only)' })**; backup precondition: ``$BackupDir`` ($($backupFiles.Count) file(s)).")
[void] $report.AppendLine()
[void] $report.AppendLine("Repair set (ADR-0021): ``&amp;&amp;`` -> ``&&``, ``&lt;`` -> ``<``, ``&gt;`` -> ``>``. Standalone ``&amp;`` is left alone - inside a markdown code span it renders as the literal entity, and unescaping it would be a second data loss. That choice also makes the repair idempotent: a literal ``&lt;`` typed by an author is stored as ``&amp;lt;``, matches no artifact pattern, and is never touched.")
[void] $report.AppendLine()
[void] $report.AppendLine('| Metric | vibe_post | vibe_comment |')
[void] $report.AppendLine('| --- | --- | --- |')
[void] $report.AppendLine("| total rows | $($before['vibe_post.total']) | $($before['vibe_comment.total']) |")
[void] $report.AppendLine("| rows with artifacts (before) | $($before['vibe_post.artifact']) | $($before['vibe_comment.artifact']) |")

if ($Apply) {
    Write-Host '==> Applying repair (pass 1)'
    $pass1 = @(Invoke-SqlScalar -Sql $repairSql)
    $changed1Post = [int]$pass1[0]
    $changed1Comment = [int]$pass1[1]
    Write-Host "    pass 1 changed rows: vibe_post $changed1Post, vibe_comment $changed1Comment"

    $after = Convert-CountLines (Invoke-SqlScalar -Sql $countSql)
    [void] $report.AppendLine("| rows changed (pass 1) | $changed1Post | $changed1Comment |")
    [void] $report.AppendLine("| rows with artifacts (after) | $($after['vibe_post.artifact']) | $($after['vibe_comment.artifact']) |")

    Write-Host '==> Applying repair again (pass 2 - the idempotence proof)'
    $pass2 = @(Invoke-SqlScalar -Sql $repairSql)
    $changed2Post = [int]$pass2[0]
    $changed2Comment = [int]$pass2[1]
    Write-Host "    pass 2 changed rows: vibe_post $changed2Post, vibe_comment $changed2Comment"

    if ($changed2Post -ne 0 -or $changed2Comment -ne 0) {
        throw "Idempotence check FAILED: a second pass changed rows ($changed2Post / $changed2Comment). The repair set must converge in one pass; investigate before trusting this run."
    }
    [void] $report.AppendLine()
    [void] $report.AppendLine("Idempotence proof: the identical second pass changed **0** rows (vibe_post) and **0** rows (vibe_comment).")
    [void] $report.AppendLine()
    [void] $report.AppendLine('## What this does not prove')
    [void] $report.AppendLine()
    [void] $report.AppendLine('- Fragments jsoup deleted outright (e.g. `Map<String, Value>` reduced to nothing) are unrecoverable; the repair only unescapes what survived as entities.')
    [void] $report.AppendLine('- A standalone `&amp;` in a row stays escaped on purpose; those rows still render correctly, so they are not damage.')
}
else {
    Write-Host '==> Dry-run: no rows were changed. Re-run with -Apply to repair.'
    [void] $report.AppendLine()
    [void] $report.AppendLine('Dry-run: no rows were changed. The counts above are what `-Apply` would repair.')
}

if ($samplesBefore.Count -gt 0) {
    [void] $report.AppendLine()
    [void] $report.AppendLine('## Sample rows that carried artifacts (before repair, first 5 per table)')
    [void] $report.AppendLine()
    foreach ($line in $samplesBefore) {
        if ($line) {
            $cols = "$line" -split "`t", 3
            if ($cols.Count -ge 3) {
                [void] $report.AppendLine("- ``$($cols[0])`` id ``$($cols[1])``: ``$($cols[2])``")
            }
        }
    }
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$mdPath = Join-Path $OutDir "fix-escaped-content-$date.md"
Set-Content -LiteralPath $mdPath -Value $report.ToString() -Encoding utf8
Write-Host "==> Wrote $mdPath"
Get-Content -LiteralPath $mdPath
