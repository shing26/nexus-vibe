#requires -Version 7.0
<#
.SYNOPSIS
    mysqldump plus a named-volume inventory for the nexus-vibe stack, written off the stack's own disk.

.DESCRIPTION
    Ticket E5 (docs/tickets/evidence-credibility.md): single host, single disk, one maintainer,
    no copy of anything. This script makes the one copy the project has, and it refuses to put
    that copy on the disk it is protecting.

    What it writes into -Destination, as one directory per run (named by timestamp, newest first
    for retention):

      db.sql.gz             mysqldump of $Database, --single-transaction --routines --triggers,
                            compressed on the host, not inside the container (see NOTE below).
      app-uploads.tar.gz    the app-uploads volume, unless -SkipUploads. Without this the backup
                            set cannot satisfy E5's own acceptance test ("one uploaded file comes
                            back"), because the dump only holds the /uploads/<name> path string.
      volumes.tsv           every named volume from docker-compose.yml: resolved volume name,
                            driver, mountpoint, size, and what is (or is not) backed up for it.
      manifest.json         the run record: sizes, sha256 of the dump both compressed and
                            uncompressed, statement line count, per-table row counts, the volume
                            inventory, the retention result, and whether the run was complete.

    One level up, in -Destination itself:

      backup-failures.jsonl appended by every failed attempt that could reach the volume. The
                            silent case matters most: a week with neither a run directory nor a
                            failure line means the task itself did not run, which no exit code
                            can report.

    Three things make this more than a pipe to gzip, and they are why it is a script rather than
    a cron line:

      1. The dump is verified before the run counts as good. The gzip is streamed back out, the
         uncompressed sha256 and line count are taken from that pass, and the last line must be
         mysqldump's own "-- Dump completed" marker. A truncated pipe exits 0 more often than
         anyone expects, and a truncated backup is a backup of nothing.
      2. The destination is checked against the source drive. On this machine (C:, D:, E:; the
         repository and every named volume live under D:) a backup written to D: is a second
         copy of the same failure domain, so it is rejected, not warned about.
      3. Failure is loud and terminal: one line on stderr, a record in backup-failures.jsonl, a
         printed "next eyes on this failure" block, and exit code 1. No partial run is ever
         reported as success.

    NOTE on compression: the dump leaves the db container as raw bytes and is gzipped on the host
    through System.IO.Compression.GZipStream. mysql:8.0 ships on a slim base where gzip is not
    guaranteed to exist, and PowerShell decodes a native command's stdout as text, which would
    mangle utf8mb4 post content on its way through a pipe. A byte-level copy into a GZipStream
    avoids both, and it means a restore needs no tool the host does not already have.

    NOTE on style: every docker argument list is passed as one array, and in-container shell
    snippets are single-quoted so PowerShell cannot expand them. Same reasoning as
    benchmark/observability/drill.ps1: a bare -d at a command call is PowerShell's -Debug.

.PARAMETER Destination
    Required. Where the timestamped run directory goes. Must be on a different volume than the
    repository and must not be inside the repository tree. A different drive letter, a junction
    or symlink that escapes to one, or an SMB/NFS share all pass; `\\localhost\...` and
    `\\127.0.0.1\...` do not, because that is the same disk wearing a hat.

.PARAMETER Project
    Compose project name. Matches `name:` in docker-compose.yml, and it is also the prefix
    compose puts on every named volume (nexus-vibe_db-data), which is how the inventory resolves
    declared names to volumes that actually exist.

.PARAMETER Database
    Schema to dump. Compose sets MYSQL_DATABASE=nexus_campus and init.sql creates the same name.
    Restricted to [A-Za-z0-9_] because it is interpolated into a shell string run inside the
    container. Note that the how-to-run comment in docker/mysql/migrate-0005-add-user-email.sql
    says `nexus_vibe`; no such database exists in this stack.

.PARAMETER Keep
    How many run directories to keep. Explicit and small on purpose: 8 weekly sets is about two
    months of history, which is what a one-human project can actually inspect.

.PARAMETER SkipUploads
    Dump the database only. For the case where the app container is legitimately down and a
    database-only set is still worth having. The manifest records complete=false so that a later
    reader cannot mistake the set for a restorable one.

.PARAMETER AlertViaBridge
    Attempt to forward a failure through docker/observability/alert-bridge (the Feishu custom-bot
    bridge) instead of only printing it. Off by default; see the comment above Send-BridgeAlert
    for why defaulting it on would be dishonest.

.PARAMETER AlertBridgeService
    Which running service container to hop through to reach the bridge. The bridge publishes no
    host port, so the only way a host process can POST to it is from inside nexus-net. Defaults
    to grafana because that image carries wget, which drill.ps1 already relies on.

.PARAMETER DryRun
    Print every command the run would execute and change nothing: no docker calls, no writes, no
    retention. Equivalent to -WhatIf; both set the same planning flag, so there is one code path
    to reason about rather than two.

.EXAMPLE
    pwsh -NoProfile -File scripts/backup.ps1 -DryRun -Destination 'E:\nexus-backups'

.EXAMPLE
    pwsh -NoProfile -File scripts/backup.ps1 -Destination 'E:\nexus-backups'

.EXAMPLE
    # Weekly, off the stack's own disk; the scheduled task is what keeps the exit code.
    schtasks /create /tn Nexus-Vibe-Backup /sc weekly /d SUN /st 03:15 /f `
      /tr "pwsh -NoProfile -File D:\Nexus-Campus\scripts\backup.ps1 -Destination E:\nexus-backups"
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
    [string]$Destination,

    [string]$Project = 'nexus-vibe',

    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$Database = 'nexus_campus',

    [ValidateRange(1, 200)]
    [int]$Keep = 8,

    [switch]$SkipUploads,

    [switch]$AlertViaBridge,

    [string]$AlertBridgeService = 'grafana',

    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

# -WhatIf (common parameter, via SupportsShouldProcess) and -DryRun mean the same thing here:
# one planning flag, one code path, so the dry run cannot drift away from the real run.
$script:Plan = [bool]$DryRun -or [bool]$WhatIfPreference
$script:repoRoot = (Split-Path -Parent $PSScriptRoot).TrimEnd('\')
$script:composeFile = Join-Path $script:repoRoot 'docker-compose.yml'
$script:envFile = Join-Path $script:repoRoot '.env'
$script:stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$script:runDir = $null
$script:failureLog = $null
$script:dockerExe = $null
$script:dumpWarning = $null
$script:tarWarning = $null

# Every volume in the `volumes:` block of docker-compose.yml, in that order, with what this
# script does about it. Listed even where nothing is copied, because an inventory that quietly
# omits a volume is how a restore finds out it was never backing that one up.
$script:volumePlan = @(
    [pscustomobject]@{ Declared = 'db-data';         Service = 'db';            Mount = '/var/lib/mysql';            Action = 'dump';            Note = 'mysqldump in this set; the volume itself is never copied' },
    [pscustomobject]@{ Declared = 'redis-data';      Service = 'redis';         Mount = '/data';                     Action = 'inventory-only';  Note = 'cache + like counters; LikeSyncTask flushes counters to MySQL every 5 min, so up to one window is derivative' },
    [pscustomobject]@{ Declared = 'es-data';         Service = 'elasticsearch'; Mount = '/usr/share/elasticsearch/data'; Action = 'inventory-only'; Note = 'GAP: nexus_posts is filled per post by PostSearchService.indexPost and there is no bulk reindex path, so a restored empty index makes search quietly return nothing' },
    [pscustomobject]@{ Declared = 'app-uploads';     Service = 'app';           Mount = '/app/uploads';              Action = 'tar';             Note = 'uploaded avatars/images, UUID.ext names; not reproducible from the dump' },
    [pscustomobject]@{ Declared = 'app-logs';        Service = 'app';           Mount = '/app/logs';                 Action = 'inventory-only';  Note = 'JSON logs, logback caps them at 100MB/7d/1GB by design; not worth a second disk' },
    [pscustomobject]@{ Declared = 'ollama-data';     Service = 'ollama';        Mount = '/root/.ollama';             Action = 'inventory-only';  Note = 'model weights, re-downloadable and by far the largest; E5 omits it from the ticket list' },
    [pscustomobject]@{ Declared = 'prometheus-data'; Service = 'prometheus';    Mount = '/prometheus';               Action = 'inventory-only';  Note = '15d TSDB, monitoring profile only; absent unless the stack was started with --profile monitoring' },
    [pscustomobject]@{ Declared = 'grafana-data';    Service = 'grafana';       Mount = '/var/lib/grafana';          Action = 'inventory-only';  Note = 'dashboards and alert rules live in git under docker/observability/grafana/provisioning; the volume holds the admin password and UI state' }
)

# Tables whose row counts are asserted by docs/runbook/restore.md. From init.sql's schema.
$script:countTables = @('sys_user', 'vibe_post', 'vibe_comment', 'vibe_post_like',
                        'vibe_post_tag', 'vibe_tag', 'vibe_channel', 'sys_message',
                        'ai_review_log', 'vibe_prompt_version')

function Write-Line {
    param([string]$Text, [string]$Color = 'Gray')
    Write-Host $Text -ForegroundColor $Color
}

function Stop-Backup {
    param([string]$Message)
    throw $Message
}

function Format-Argv {
    param([string[]]$Args_)
    return (@($Args_ | ForEach-Object {
        if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ }
    })) -join ' '
}

function Step {
    param(
        [string]$Name,
        [string]$Plan,
        [scriptblock]$Body
    )
    if ($script:Plan) {
        Write-Line "[plan] $Name" 'Cyan'
        foreach ($line in @($Plan -split "`n")) { Write-Line "       $line" 'DarkGray' }
        return $null
    }
    Write-Line "> $Name" 'Green'
    return & $Body
}

function Invoke-DockerText {
    # Read-only or small-output docker calls. Throws with the CLI's own words on non-zero, which
    # is the shape every caller here wants: a failed backup must say why.
    param([Parameter(Mandatory)][string[]]$Cmd)
    $output = & $script:dockerExe @Cmd 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        $tail = (($output | Where-Object { $_.Trim() }) | Select-Object -Last 2) -join ' | '
        Stop-Backup "docker $(Format-Argv $Cmd) exited $code : $tail"
    }
    return ($output -join "`n")
}

function Invoke-DockerGzipToFile {
    # Stream a native command's raw stdout bytes into a gzip file. PowerShell's own pipeline would
    # decode that stream as text and rewrite it, which is how a backup of Chinese post content
    # arrives as question marks. Everything below is byte-level on purpose.
    param([Parameter(Mandatory)][string[]]$Cmd, [Parameter(Mandatory)][string]$Path)
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $script:dockerExe
    $psi.WorkingDirectory = $script:repoRoot
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    foreach ($a in $Cmd) { $psi.ArgumentList.Add($a) }

    $proc = $null
    $stderr = ''
    try {
        $proc = [System.Diagnostics.Process]::Start($psi)
        $errTask = $proc.StandardError.ReadToEndAsync()
        $fs = [System.IO.File]::Create($Path)
        try {
            $gz = [System.IO.Compression.GZipStream]::new($fs, [System.IO.Compression.CompressionLevel]::Optimal, $true)
            try { $proc.StandardOutput.BaseStream.CopyTo($gz) } finally { $gz.Dispose() }
        } finally { $fs.Dispose() }
        $proc.WaitForExit()
        [void]$errTask.Wait(5000)
        $stderr = $errTask.Result
        $code = $proc.ExitCode
    } finally {
        if ($proc) { $proc.Dispose() }
    }
    if ($code -ne 0) {
        Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
        $tail = (($stderr -split "`n" | Where-Object { $_.Trim() }) | Select-Object -Last 2) -join ' | '
        Stop-Backup "docker $(Format-Argv $Cmd) exited $code : $tail"
    }
    return ($stderr -split "`n" | Where-Object { $_.Trim() }) -join ' | '
}

function Get-ComposeArgs {
    param([string[]]$Tail)
    return @('compose', '-p', $Project, '-f', 'docker-compose.yml') + @($Tail)
}

function Get-VolumeSizeFromDf {
    # Best effort, and labelled as such: `docker system df -v` has no machine-readable format for
    # its verbose tables, so the size column is found by shape (a number carrying a unit) rather
    # than by index, which is the only way to stay right when the column order moves between CLI
    # versions. A size that will not parse stays empty instead of guessing at a link count.
    # The row still records that the volume exists, which is the part a restore depends on.
    param([string]$DfOutput, [string]$VolumeName)
    $line = @($DfOutput -split "`n" |
              Where-Object { $_ -match "(^|\s)$([regex]::Escape($VolumeName))(\s|$)" } |
              Select-Object -First 1)
    if (-not $line) { return '' }
    $fields = @(((('' + $line[0]).Trim() -replace '\s{2,}', '|') -split '\|') |
                ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })
    # A number with a unit Docker actually prints: 0B, 89.1kB, 1.5 MB, 48.3MB, 1.234GB, or the
    # binary spellings. `2.1GB (46%)` from a Reclaimable column deliberately does not match, and
    # neither does a bare link count.
    $sized = @($fields | Where-Object {
        $_ -match '^[0-9]+(\.[0-9]+)?\s?(B|[kKmMgGTtPp]i?B|bytes)$'
    })
    if ($sized) { return '' + $sized[-1] }
    return ''
}

function Test-GzipArtifact {
    # Prove the archive is readable, count its statements, and hash what a restore would actually
    # apply - one pass, and no uncompressed copy ever lands on disk.
    #
    # The retained tail is the last 128 bytes of the stream rather than "the last line": mysqldump
    # writes extended INSERTs that can run to a megabyte, so holding a line is not bounded, and
    # the marker that matters ("-- Dump completed on ...") is the last thing in the file anyway.
    # Counting newlines by jumping between them with IndexOf keeps this cheap on exactly those
    # dumps with very long lines; a byte-by-byte walk of a 200MB dump in PowerShell is minutes.
    param(
        [Parameter(Mandatory)][string]$Path,
        [string]$MustEndWith = '-- Dump completed',
        [ValidateRange(32, 1MB)]
        [int]$WindowBytes = 128
    )

    $sha = [System.Security.Cryptography.SHA256]::Create()
    $buffer = New-Object byte[] 131072
    $window = New-Object byte[] $WindowBytes
    $windowLen = 0
    $lines = 0
    $bytes = 0L
    $digest = ''

    try {
        $fs = [System.IO.File]::OpenRead($Path)
        try {
            $gz = New-Object System.IO.Compression.GZipStream($fs, [System.IO.Compression.CompressionMode]::Decompress)
            try {
                while ($true) {
                    $read = $gz.Read($buffer, 0, $buffer.Length)
                    if ($read -le 0) { break }
                    $bytes += $read
                    $at = 0
                    while ($true) {
                        $hit = [Array]::IndexOf($buffer, [byte]10, $at, $read - $at)
                        if ($hit -lt 0) { break }
                        $lines++
                        $at = $hit + 1
                    }
                    [void]$sha.TransformBlock($buffer, 0, $read, $null, 0)
                    if ($read -ge $WindowBytes) {
                        [Array]::Copy($buffer, $read - $WindowBytes, $window, 0, $WindowBytes)
                        $windowLen = $WindowBytes
                    } else {
                        $room = $WindowBytes - $read
                        if ($windowLen -gt $room) {
                            $drop = $windowLen - $room
                            [Array]::Copy($window, $drop, $window, 0, $room)
                            $windowLen = $room
                        }
                        [Array]::Copy($buffer, 0, $window, $windowLen, $read)
                        $windowLen += $read
                    }
                }
            } finally { $gz.Dispose() }
        } finally { $fs.Dispose() }
        [void]$sha.TransformFinalBlock($buffer, 0, 0)
        $digest = ([BitConverter]::ToString($sha.Hash) -replace '-', '').ToLowerInvariant()
    } finally { $sha.Dispose() }

    if ($bytes -eq 0) { Stop-Backup "artifact is empty: $Path" }
    # Only the tail is decoded, and only what sits after its last newline. A block boundary may
    # have split a multi-byte character, so the front of this window is decoration; the marker
    # being looked for is ASCII and at the end, which is what the comparison actually reads.
    $tailText = [System.Text.Encoding]::UTF8.GetString($window, 0, $windowLen).TrimEnd()
    $cut = $tailText.LastIndexOf("`n")
    if ($cut -ge 0) { $tailText = $tailText.Substring($cut + 1) }
    $lastLine = $tailText.Trim()
    if ($MustEndWith -and ($lastLine -notlike "*$MustEndWith*")) {
        Stop-Backup "artifact does not end with '$MustEndWith' - truncated or incomplete dump. tail: $lastLine"
    }
    return [pscustomobject]@{
        Sha256Plain = $digest
        PlainBytes  = $bytes
        Lines       = $lines
        LastLine    = $lastLine
    }
}

function Resolve-BackupDestination {
    # The whole point of E5 is that the copy must not live where the original does. Everything in
    # here exists to make "it is on another disk" a checked claim rather than an intention.
    param([string]$Path, [string]$SourceRoot)

    $full = [System.IO.Path]::GetFullPath($Path)
    if ($full.StartsWith('\\localhost\') -or $full.StartsWith('\\127.0.0.1\') -or
        $full.StartsWith('\\.\')) {
        Stop-Backup "destination $full is a loopback share: same disk, different spelling."
    }

    # Tree containment first, whatever the drive: D:\Nexus-Campus\backups is a backup that dies
    # with the repository, and so is anything that would contain the repository.
    $srcTrim = $SourceRoot.TrimEnd('\') + '\'
    if ($full.StartsWith($srcTrim, [StringComparison]::OrdinalIgnoreCase) -or
        $srcTrim.StartsWith($full.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
        Stop-Backup "destination $full is inside the repository tree ($SourceRoot); it is the same data, copied."
    }

    $source = New-Object System.IO.DriveInfo($SourceRoot)

    if ($full.StartsWith('\\')) {
        # A remote share is a different failure domain by construction. Whether it is really
        # another physical disk is the operator's claim to keep; the run records it verbatim.
        return [pscustomobject]@{ Path = $full; Kind = 'network-share';
                                  Volume = $full.Split('\')[2]; SourceVolume = "$($source.Name)";
                                  Distinct = $true }
    }

    $probe = $full
    while ($probe -and -not (Test-Path -LiteralPath $probe)) {
        $parent = Split-Path -Parent $probe
        if (-not $parent -or $parent -eq $probe) { break }
        $probe = $parent
    }
    if (-not (Test-Path -LiteralPath $probe)) {
        Stop-Backup "cannot resolve a volume for destination $full; no existing ancestor of the path."
    }

    $dest = New-Object System.IO.DriveInfo($probe)
    $same = ($dest.Name -ieq $source.Name)
    $kind = 'directory'
    if ($same) {
        # A junction or symlink can point somewhere else entirely; only that escape lets the run
        # through. Volume mount points registered as an empty folder are NOT detected here, which
        # is why the help says to use a second drive letter or a share.
        $item = Get-Item -LiteralPath $probe -Force
        if ($item.LinkType -and $item.Target) {
            $target = [System.IO.Path]::GetFullPath($item.Target[0])
            $tDrive = (New-Object System.IO.DriveInfo($target)).Name
            if ($tDrive -ine $source.Name) {
                $same = $false
                $kind = "$($item.LinkType) -> $target"
                $dest = New-Object System.IO.DriveInfo($target)
            }
        }
    }
    if ($same) {
        Stop-Backup ("destination $full is on $($source.Name) - the same volume as $SourceRoot. " +
                     'E5 exists because this box has one disk; give -Destination a second volume: ' +
                     'another drive letter, or a share like \\server\backups.')
    }
    return [pscustomobject]@{ Path = $full; Kind = $kind; Volume = "$($dest.Name)";
                              SourceVolume = "$($source.Name)"; Distinct = $true }
}

# TODO(E5 follow-up, blocked on E3): the alert channel exists but is not a dependable one, so
# -AlertViaBridge is opt-in rather than the default. Reasons, all of them verifiable in the tree:
#   * alert-bridge sits behind `profiles: ["monitoring"]` in docker-compose.yml, so a stack that
#     was started with a plain `docker compose up` has no listener at all. A weekly task that
#     quietly no-ops is the exact failure E3 was written about.
#   * FEISHU_ALERT_WEBHOOK defaults to empty (`${FEISHU_ALERT_WEBHOOK:-}`), and the bridge answers
#     502 with "FEISHU_ALERT_WEBHOOK is not set" when it fires unconfigured.
#   * The bridge publishes no host port (only `expose: 8080`, on nexus-net), which is why this
#     function has to reach it by exec-ing into another container. Adding a host mapping is a
#     docker-compose.yml change, and that file is not this ticket's to touch.
#   * Worst of all for a backup alert: it shares a failure domain with the thing that failed. A
#     dead Docker daemon or a full disk takes the notification with it. The durable signal is the
#     scheduled task's exit code plus backup-failures.jsonl, and this script makes sure both exist.
# The payload shape below is what alert_bridge.py's render_text() reads: state, title, and
# alerts[] with labels.alertname / labels.severity / annotations.summary. The bridge adds the
# Feishu HMAC signature itself, so no secret ever reaches this script.
function Send-BridgeAlert {
    param([string]$Summary)

    $payload = [ordered]@{
        state  = 'alerting'
        title  = "Nexus-Vibe backup failed at $script:stamp"
        alerts = @([ordered]@{
            labels      = [ordered]@{ alertname = 'NexusBackupFailed'; severity = 'critical' }
            annotations = [ordered]@{ summary = $Summary }
        })
    } | ConvertTo-Json -Depth 8 -Compress

    # The payload cannot go on the command line as JSON: it is full of double quotes, and a
    # quoted string that has to survive both PowerShell's argv handling and sh inside the
    # container is how a failure alert ends up as a syntax error. Base64 is quote-free, so the
    # body crosses the boundary intact, lands in a file, and busybox wget posts the file.
    # (If -AlertBridgeService is ever pointed at a GNU-wget image, --post-file becomes
    # --body-file; the grafana image this defaults to is Alpine.)
    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($payload))
    $inner = "echo $b64 | base64 -d > /tmp/nexus-backup-alert.json" +
             ' && wget -qO- --post-file=/tmp/nexus-backup-alert.json http://alert-bridge:8080/' +
             ' ; rm -f /tmp/nexus-backup-alert.json'
    $cmd = Get-ComposeArgs @('exec', '-T', $AlertBridgeService, 'sh', '-c', $inner)
    try {
        $reply = Invoke-DockerText -Cmd $cmd
        Write-Line "  alert bridge replied: $($reply.Trim())" 'DarkGray'
        return $true
    } catch {
        Write-Line "  alert bridge unreachable: $($_.Exception.Message)" 'DarkYellow'
        return $false
    }
}

function Show-NextEyes {
    param([string]$FailureMessage)
    Write-Line ''
    Write-Line 'NEXT EYES ON THIS FAILURE' 'Yellow'
    Write-Line "  who    shing26 - sole maintainer, and the only on-call this project has"
    Write-Line '  where  this terminal, the scheduled task exit code, and'
    Write-Line "         $script:failureLog"
    Write-Line '  when   before the next `docker compose up`; the deploy checklist has no'
    Write-Line '         backup gate yet (adding one is E4/E5 landing together, not this run).'
    Write-Line '  proof  a backup nobody restored is not a backup - docs/runbook/restore.md'
    if ($AlertViaBridge) {
        Write-Line '  alert  -AlertViaBridge was given: delivery result is printed above.' 'DarkGray'
    } else {
        Write-Line '  alert  NOT DELIVERED. The Feishu bridge is monitoring-profile only and has no' 'DarkGray'
        Write-Line '         host port; re-run the failure with -AlertViaBridge, or fix E3 first.' 'DarkGray'
    }
    Write-Line "  detail $FailureMessage" 'DarkGray'
}

function Write-FailureRecord {
    param([string]$FailureMessage)
    try {
        if (-not (Test-Path -LiteralPath $Destination)) { return }
        $record = [ordered]@{
            at       = (Get-Date).ToString('o')
            project  = $Project
            runDir   = $script:runDir
            error    = $FailureMessage
            dryRun   = [bool]$script:Plan
        } | ConvertTo-Json -Compress
        Add-Content -LiteralPath $script:failureLog -Value $record
        Write-Line "  recorded in $script:failureLog" 'DarkGray'
    } catch {
        # If the destination is unwritable that is itself part of the incident, and the stderr
        # line the operator sees has to stay a single readable sentence.
        Write-Line "  could not record the failure: $($_.Exception.Message)" 'DarkYellow'
    }
}

# --------------------------------------------------------------------------- preflight

try {
    $script:failureLog = Join-Path ([System.IO.Path]::GetFullPath($Destination)) 'backup-failures.jsonl'

    Write-Line "Nexus-Vibe backup - project '$Project', database '$Database', plan=$($script:Plan)" 'Yellow'

    # Deliberately not a Step: every check here is a file read or a PATH lookup, none of them
    # touches the daemon, so a dry run can and should prove them too. A -DryRun that skipped the
    # .env check would tell you next Wednesday that the weekly task never had a chance.
    Write-Line '> preflight-host' 'Green'
    if (-not (Test-Path -LiteralPath $script:composeFile)) {
        Stop-Backup "docker-compose.yml not found at $script:composeFile - run this from the repository."
    }
    if (-not (Test-Path -LiteralPath $script:envFile)) {
        # compose interpolates ${DB_PASSWORD:?...} for db and app while loading the file, so a
        # missing .env kills `compose exec` before mysqldump ever starts. Say that plainly
        # instead of relaying compose's abort message.
        Stop-Backup "no .env at $script:envFile - docker compose cannot even resolve the db service."
    }
    if (-not (Select-String -Path $script:envFile -Pattern '^\s*DB_PASSWORD\s*=\s*\S' -Quiet)) {
        Stop-Backup '.env has no non-empty DB_PASSWORD; compose will refuse to load the stack.'
    }
    $command = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $command) { Stop-Backup 'docker CLI not found on PATH.' }
    $script:dockerExe = $command.Source

    # Runs in planning mode too: it is pure path arithmetic, and a dry run that skipped the check
    # the real run depends on would not be proving anything.
    $dest = Resolve-BackupDestination -Path $Destination -SourceRoot $script:repoRoot
    Write-Line "  destination  $($dest.Path)" 'DarkGray'
    Write-Line "               volume $($dest.Volume), source volume $($dest.SourceVolume), kind $($dest.Kind)" 'DarkGray'

    if (-not $script:Plan) {
        $script:runDir = Join-Path $dest.Path $script:stamp
        New-Item -ItemType Directory -Path $script:runDir -Force | Out-Null
    } else {
        $script:runDir = Join-Path $dest.Path $script:stamp
    }
    $script:failureLog = Join-Path $dest.Path 'backup-failures.jsonl'

    # ---------------------------------------------------------------------- stack reachability

    # `compose ls` takes no -p; it is the one call that answers "is this project up at all".
    $projectArgs = @('compose', 'ls', '--format', '{{.Name}}')
    $serviceArgs = Get-ComposeArgs @('ps', '--status', 'running', '--services')
    $running = Step 'preflight-stack' "docker $(Format-Argv $projectArgs)`ndocker $(Format-Argv $serviceArgs)" {
        $projects = Invoke-DockerText -Cmd $projectArgs
        if (@($projects -split "`n" | Where-Object { $_.Trim() -ieq $Project }).Count -eq 0) {
            Stop-Backup "compose project '$Project' is not running; there is nothing to dump."
        }
        $services = @(Invoke-DockerText -Cmd $serviceArgs) -split "`n" |
                    ForEach-Object { $_.Trim() } | Where-Object { $_ }
        if ($services -notcontains 'db') { Stop-Backup "service 'db' is not running in project '$Project'." }
        if (-not $SkipUploads -and $services -notcontains 'app') {
            Stop-Backup "service 'app' is not running, so app-uploads cannot be copied. Either start it or pass -SkipUploads; a set without the uploads volume is not restorable per E5's acceptance test, and this script will not call one complete."
        }
        return $services
    }

    # ---------------------------------------------------------------------- database dump

    # MYSQL_PWD keeps the secret off the command line, and $MYSQL_ROOT_PASSWORD is expanded by sh
    # inside the container, where the entrypoint put it - never by PowerShell, never on the host.
    # -h 127.0.0.1 matches the form docs/runbook/restore.md uses on the load side, so the two
    # halves of the backup story cannot drift into "the dump command worked, the load command
    # didn't" over a client option. MySQL 8's bootstrap grants root@127.0.0.1 as well as
    # root@localhost, so socket and loopback-TCP both authenticate.
    # Parentheses matter: in PowerShell the comma binds tighter than +, so an unparenthesized
    # concatenation inside @() arrives as four argv elements and sh sees only the first one -
    # a mysqldump with no arguments, which is a usage error, not a backup.
    $dumpShell = ('MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump' +
                  ' --single-transaction --routines --triggers --set-gtid-purged=OFF' +
                  ' --default-character-set=utf8mb4 -h 127.0.0.1 -uroot ' + $Database)
    $dumpArgs = Get-ComposeArgs @('exec', '-T', 'db', 'sh', '-c', $dumpShell)
    $dumpPath = if ($script:runDir) { Join-Path $script:runDir 'db.sql.gz' } else { 'db.sql.gz' }
    $script:dumpWarning = Step 'db-dump' "docker $(Format-Argv $dumpArgs) | gzip on the host -> $dumpPath" {
        return Invoke-DockerGzipToFile -Cmd $dumpArgs -Path $dumpPath
    }

    # ---------------------------------------------------------------------- row counts

    # Evidence for the restore assertion, taken a moment after the dump rather than inside its
    # transaction: these are "as of" numbers, not a consistent snapshot. The runbook compares with
    # that in mind.
    # Single-quoted template: the backticks are MySQL identifier quotes, and in a double-quoted
    # PowerShell string a backtick is an escape character rather than a backtick.
    $unionTemplate = 'SELECT ''{0}'', COUNT(*) FROM `{1}`'
    $union = ($script:countTables | ForEach-Object { $unionTemplate -f $_, $_ }) -join ' UNION ALL '
    $unionB64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($union))
    $countArgs = Get-ComposeArgs @('exec', '-T', 'db', 'sh', '-c',
        "echo $unionB64 | base64 -d | MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql -N -s -h 127.0.0.1 -uroot $Database 2>/dev/null")
    $countsRaw = Step 'db-row-counts' "docker $(Format-Argv $countArgs)" {
        return Invoke-DockerText -Cmd $countArgs
    }
    $counts = [ordered]@{}
    foreach ($line in @("$countsRaw" -split "`n")) {
        $parts = $line -split "`t"
        if ($parts.Count -eq 2 -and $parts[1] -match '^\d+$') { $counts[$parts[0]] = [long]$parts[1] }
    }
    if (-not $script:Plan -and $counts.Count -eq 0) { Stop-Backup 'no row counts came back; refusing a backup set that cannot be verified later.' }

    # ---------------------------------------------------------------------- volume inventory

    $volumeArgs = @('volume', 'ls', '--format', '{{.Name}}')
    $dfArgs = @('system', 'df', '-v')
    $inventory = Step 'volume-inventory' "docker $(Format-Argv $volumeArgs)`ndocker $(Format-Argv $dfArgs)" {
        $present = @(Invoke-DockerText -Cmd $volumeArgs) -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ }
        $df = Invoke-DockerText -Cmd $dfArgs
        $rows = @()
        foreach ($declared in $script:volumePlan) {
            $candidates = @("$($Project)_$($declared.Declared)", $declared.Declared)
            $resolved = @($present | Where-Object { $_ -in $candidates } | Select-Object -First 1)
            $name = if ($resolved) { $resolved[0] } else { '<absent>' }
            $row = [ordered]@{
                declared = $declared.Declared
                volume   = $name
                service  = $declared.Service
                mount    = $declared.Mount
                backup   = if ($declared.Action -eq 'dump') { 'db.sql.gz' }
                           elseif ($declared.Action -eq 'tar') { 'app-uploads.tar.gz' }
                           else { 'inventory only' }
                driver   = ''
                created  = ''
                mountpoint = ''
                size     = ''
                note     = $declared.Note
            }
            if ($name -ne '<absent>') {
                $inspect = Invoke-DockerText -Cmd @('volume', 'inspect', '--format', '{{json .}}', $name) |
                           Select-Object -First 1 | ConvertFrom-Json
                $row.driver = "$($inspect.Driver)"
                $row.created = "$($inspect.CreatedAt)"
                $row.mountpoint = "$($inspect.Mountpoint)"
                $row.size = Get-VolumeSizeFromDf -DfOutput $df -VolumeName $name
            }
            $rows += [pscustomobject]$row
        }
        return $rows
    }

    # ---------------------------------------------------------------------- uploads

    $uploadsPath = if ($script:runDir) { Join-Path $script:runDir 'app-uploads.tar.gz' } else { 'app-uploads.tar.gz' }
    $uploadsSkipped = [bool]$SkipUploads
    if (-not $uploadsSkipped) {
        $tarArgs = Get-ComposeArgs @('exec', '-T', 'app', 'tar', '-C', '/app/uploads', '-cf', '-', '.')
        $script:tarWarning = Step 'uploads-tar' "docker $(Format-Argv $tarArgs) | gzip on the host -> $uploadsPath" {
            return Invoke-DockerGzipToFile -Cmd $tarArgs -Path $uploadsPath
        }
    }

    # ---------------------------------------------------------------------- verify

    $verifyPlan = "stream each artifact back out of gzip on the host: hash it, count its lines, check the last line"
    $verified = Step 'verify-artifacts' $verifyPlan {
        $out = [ordered]@{}
        $db = Test-GzipArtifact -Path $dumpPath -MustEndWith '-- Dump completed'
        $out.db = [ordered]@{
            path        = $dumpPath
            gzBytes     = (Get-Item -LiteralPath $dumpPath).Length
            gzSha256    = (Get-FileHash -LiteralPath $dumpPath -Algorithm SHA256).Hash.ToLowerInvariant()
            plainBytes  = $db.PlainBytes
            plainSha256 = $db.Sha256Plain
            statements  = $db.Lines
            lastLine    = $db.LastLine
        }
        if (-not $uploadsSkipped) {
            # tar has no completion marker, so the only honest check is that it decompresses and
            # is not empty; a zero-length tar.gz here would mean the volume itself was empty.
            $up = Test-GzipArtifact -Path $uploadsPath -MustEndWith ''
            $out.uploads = [ordered]@{
                path        = $uploadsPath
                gzBytes     = (Get-Item -LiteralPath $uploadsPath).Length
                gzSha256    = (Get-FileHash -LiteralPath $uploadsPath -Algorithm SHA256).Hash.ToLowerInvariant()
                plainBytes  = $up.PlainBytes
                plainSha256 = $up.Sha256Plain
            }
        }
        return $out
    }

    # ---------------------------------------------------------------------- inventory file

    $inventoryPath = if ($script:runDir) { Join-Path $script:runDir 'volumes.tsv' } else { 'volumes.tsv' }
    Step 'write-volumes-tsv' "tab-separated inventory -> $inventoryPath" {
        $header = 'declared', 'volume', 'service', 'mount', 'backup', 'size', 'driver', 'created', 'mountpoint', 'note'
        $lines = @(($header -join "`t"))
        foreach ($r in $inventory) {
            $lines += @(($r.declared, $r.volume, $r.service, $r.mount, $r.backup, $r.size,
                         $r.driver, $r.created, $r.mountpoint, ($r.note -replace "`t", ' ')) -join "`t")
        }
        Set-Content -LiteralPath $inventoryPath -Value $lines -Encoding utf8NoBOM
    } | Out-Null

    # ---------------------------------------------------------------------- manifest

    $manifestPath = if ($script:runDir) { Join-Path $script:runDir 'manifest.json' } else { 'manifest.json' }
    $manifest = [ordered]@{
        schemaVersion = 1
        takenAt       = (Get-Date).ToString('o')
        hostName      = $env:COMPUTERNAME
        repository    = $script:repoRoot
        composeProject = $Project
        database      = $Database
        complete      = (-not $uploadsSkipped)
        uploadsIncluded = (-not $uploadsSkipped)
        rowCounts     = $counts
        volumes       = $inventory
        verified      = $verified
        warnings      = @(@($script:dumpWarning, $script:tarWarning) | Where-Object { $_ })
        retentionKeep = $Keep
    }
    Step 'write-manifest' "run record -> $manifestPath" {
        $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM
    } | Out-Null

    # ---------------------------------------------------------------------- retention

    $retention = Step 'retention' "keep the newest $Keep run directories under $($dest.Path); delete the rest, including their manifest.json" {
        $sets = @(Get-ChildItem -LiteralPath $dest.Path -Directory -ErrorAction SilentlyContinue |
                  Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'manifest.json') } |
                  Sort-Object Name -Descending)
        $pruned = @()
        if ($sets.Count -gt $Keep) {
            foreach ($old in $sets[$Keep..($sets.Count - 1)]) {
                Remove-Item -LiteralPath $old.FullName -Recurse -Force
                $pruned += $old.Name
            }
        }
        return [ordered]@{ kept = [Math]::Min($sets.Count, $Keep); deleted = $pruned }
    }

    # ---------------------------------------------------------------------- summary

    Write-Line ''
    if ($script:Plan) {
        Write-Line 'DRY RUN - nothing was executed, nothing was written.' 'Yellow'
        Write-Line "  destination  $($dest.Path)  (volume $($dest.Volume), source $($dest.SourceVolume))"
        Write-Line "  run dir      $script:runDir"
        Write-Line "  artifacts    db.sql.gz, $(if ($uploadsSkipped) { 'app-uploads.tar.gz skipped' } else { 'app-uploads.tar.gz' }), volumes.tsv, manifest.json"
        Write-Line "  retention    keep $Keep"
        Write-Line "  tables       $($script:countTables.Count) row counts would be captured"
        exit 0
    }

    Write-Line 'BACKUP OK' 'Green'
    Write-Line "  run dir      $script:runDir"
    Write-Line "  dump         $($verified.db.path)"
    Write-Line "               $($verified.db.gzBytes) bytes gz, $($verified.db.plainBytes) plain, $($verified.db.statements) lines"
    Write-Line "               sha256(plain) $($verified.db.plainSha256)"
    Write-Line "  uploads      $(if ($uploadsSkipped) { 'SKIPPED - this set is not restorable on its own' } else { $verified.uploads.path })"
    Write-Line "  tables       $(($counts.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ' ')"
    Write-Line "  volumes      $(@($inventory | Where-Object { $_.volume -ne '<absent>' }).Count)/$($script:volumePlan.Count) present"
    Write-Line "  retention    kept $($retention.kept)$(if ($retention.deleted) { ", deleted $($retention.deleted -join ', ')" })"
    Write-Line ''
    Write-Line "  Next: restore this set per docs/runbook/restore.md. Until that has been done with" 'DarkGray'
    Write-Line '        these exact files, this is a file that looks like a backup.' 'DarkGray'
    exit 0
}
catch {
    $message = $_.Exception.Message
    [Console]::Error.WriteLine("BACKUP FAILED: $message")
    Write-FailureRecord -FailureMessage $message
    if ($AlertViaBridge -and -not $script:Plan) {
        [void](Send-BridgeAlert -Summary $message)
    }
    Show-NextEyes -FailureMessage $message
    exit 1
}
