#requires -Version 7.0
<#
.SYNOPSIS
    Observability drill: break the LLM on purpose and prove the stack reports it.

.DESCRIPTION
    Brings the compose stack up with the monitoring and drill profiles, then drives an LLM
    outage and an exhausted rate limit for real, asserting on metric names, health semantics,
    container health, the alert provisioning, the bridge, and the public side of nginx. Every
    probe's raw answer goes to an evidence file next to the summary.

    It runs as its own compose project (-p nexus-drill) with its own container names and its
    own volumes, so it sits beside a developer's running stack instead of replacing it. That
    also buys what a shared volume never gives: an empty database on first boot, which is the
    case the production seed rules (ADR-0008) are actually written for.

    This is a drill, not a unit test. It builds images, recreates the app container twice to
    move the LLM endpoint, and the recovery step waits out a reconcile cycle plus a health
    probe cache window, so plan for fifteen to twenty minutes. The drill project is torn down
    with its volumes at the end unless -Keep is given. Exit code 0 means every step passed.

    The alerting path is asserted as far as a script can take it: the rules have to load into
    Grafana's engine, the notification has to leave the bridge, and a stand-in receiver
    (benchmark/observability/webhook-sink) recomputes the Feishu signature and answers the way
    Feishu does. What no script can assert is a message appearing in a real Feishu group, which
    still needs one manual send with a live webhook, and the Grafana UI.

    Note on style: every docker argument list is passed as one array. A bare -d at a function
    call is swallowed by PowerShell's common -Debug parameter, which turns `compose up -d`
    into a foreground `compose up` that never returns. -v goes the same way, to -Verbose.

    Note on assertions: they read state, not log prose. Fail-closed has two code paths that
    word their warning lines differently depending on where the cached health probe lands, so
    the drill reads the vibe_post row (status + ai_reviewed) and the Prometheus scrape instead of
    grepping for a sentence. Counters are asserted only after the code path that moves them has
    run, which is why a meter missing from an early scrape is not a failure.

.PARAMETER SkipBuild
    Reuse the images already built. Off by default because the prod-shaped container is part of
    what is being verified.

.PARAMETER Keep
    Leave the drill stack and its volumes running afterwards so they can be poked at by hand.

.PARAMETER WebPort
    Host port nginx is published on. Defaults to 18080 so a developer's 8080 is left alone.

.PARAMETER StartupTimeoutSec
    How long to wait for the app container to answer after a start or a recreate.

.PARAMETER RecoveryTimeoutSec
    How long to wait for the reconcile sweep after the LLM comes back. The breaker cool-down
    and the five-minute health cache are shipped values, so this has to be generous.

.PARAMETER RollbackTag
    The image tag the rollback step swaps to. Set it: a rollback target has to be a build that
    can actually boot against the database this build wrote, and "whatever other tag happens to
    be in the local image store" cannot promise that. Unset, the step takes the newest other tag
    and says which it picked.

.PARAMETER Only
    Run only the named steps. Every other step is still listed, marked SKIP, so the drill's step
    numbering and its full shape stay readable whichever subset ran. CI uses this to run three
    alert steps on every pull request; the rest need a live LLM or a long wait and stay manual.
    Naming a step that does not exist is an error rather than a quiet no-op, because a renamed
    step would otherwise turn the gate into a green run of nothing.

.EXAMPLE
    pwsh -File benchmark/observability/drill.ps1

.EXAMPLE
    pwsh -File benchmark/observability/drill.ps1 -SkipBuild -Keep `
        -Only alert-no-data-policy-is-per-rule, alert-rules-select-real-metrics
#>
[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$Keep,
    [int]$WebPort = 18080,
    [int]$StartupTimeoutSec = 240,
    [int]$RecoveryTimeoutSec = 600,
    [string]$RollbackTag = '',
    [string[]]$Only = @()
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSCommandPath))
Set-Location $repoRoot

# Everything this script reads comes out of `docker exec` as UTF-8: channel descriptions and AI
# review summaries are Chinese. Under an OEM console codepage those bytes arrive mangled, and
# ConvertFrom-Json then fails with "after parsing a value an unexpected character was encountered"
# on a response that is perfectly valid JSON - a step red for a reason that has nothing to do with
# the stack. One run showed this only because it was started detached, where nothing had set the
# console encoding first, which is exactly the kind of host luck a drill must not depend on.
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
    Write-Host 'could not set the console to UTF-8: assertions that parse JSON may fail on Chinese text' -ForegroundColor Yellow
}

# Compose reads WEB_PORT for the published port; a shell value beats .env, so the drill never
# has to edit anyone's .env to probe the public side.
$env:WEB_PORT = "$WebPort"

$evidenceDir = Join-Path $PSScriptRoot 'evidence'
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$evidencePath = Join-Path $evidenceDir "drill-$stamp.log"

# A project of its own: separate containers, separate named volumes. Nothing in here can reach
# the nexus-vibe stack a developer may be running, and `down -v` cannot delete their database.
$composeArgs = @('compose', '-p', 'nexus-drill',
                 '-f', 'docker-compose.yml',
                 '-f', 'benchmark/observability/docker-compose.drill.yml',
                 '--profile', 'monitoring', '--profile', 'drill')

$script:results = New-Object System.Collections.Generic.List[object]

# Step names are compared with -contains, so a comma-separated list from a shell arrives as
# separate elements and a whitespace-padded one still matches after Trim.
$script:only = @($Only | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } |
                 Where-Object { $_ })

# One predicate for "should this step run", shared by Step itself and by the bring-up below. The
# bring-up is not a step, so a filtered run that does not select stack-up would otherwise still
# build and start the whole project -- pulling Elasticsearch and Ollama for two file reads.
function Test-StepSelected {
    param([string]$Name)
    return (-not $script:only.Count) -or ($script:only -contains $Name)
}

# The uids rules.yaml declares, in one place: one step checks the file lists them, another checks
# the alerting engine loaded them, and they must not be able to drift apart.
# nexus-prometheus-scrape-failed is the rule that asks "is anyone still measuring this at all";
# nexus-availability-999-fast-burn is the error-budget form of the 5xx ratio.
$script:ruleUids = @('nexus-llm-breaker-open', 'nexus-ai-review-backlog',
                     'nexus-http-5xx-ratio', 'nexus-rate-limit-spike',
                     'nexus-prometheus-scrape-failed', 'nexus-availability-999-fast-burn')

function Invoke-Docker {
    param([Parameter(Mandatory)][string[]]$Cmd)
    # The docker CLI on Windows occasionally dies mid-drill (Go runtime dump, or a usage error
    # from a child it could not spawn). That is the host, not the stack under test, and it should
    # not cost a fifteen minute run. Retry only those shapes, a couple of times, and let every
    # other non-zero exit surface immediately: an assertion failure is not a flake.
    $attempt = 0
    while ($true) {
        $attempt++
        $output = & docker @Cmd 2>&1 | ForEach-Object { "$_" }
        if ($LASTEXITCODE -eq 0) { return ($output -join "`n") }
        $joined = $output -join "`n"
        $crashed = $joined -match 'runtime\.[a-z_]+\(|goroutine \d+ \[running\]|docker\.exe|Usage:  docker'
        if (-not $crashed -or $attempt -ge 3) {
            $tail = (($output | Where-Object { $_.Trim() }) | Select-Object -Last 2) -join ' | '
            throw "docker $(($Cmd -join ' ')) exited $LASTEXITCODE : $tail"
        }
        Write-Host "    docker CLI crashed (attempt $attempt), retrying" -ForegroundColor DarkYellow
        Start-Sleep -Seconds 5
    }
}

function Invoke-Compose {
    param([Parameter(Mandatory)][string[]]$Cmd)
    return Invoke-Docker -Cmd (@($composeArgs) + @($Cmd))
}

function Invoke-DockerTolerant {
    param([Parameter(Mandatory)][string[]]$Cmd)
    # For the cases where a non-zero exit IS the assertion. Invoke-Docker deliberately turns every
    # non-zero into a thrown failure, which is right for docker and wrong for a process that is
    # supposed to refuse to start.
    $output = & docker @Cmd 2>&1 | ForEach-Object { "$_" }
    return [pscustomobject]@{ Code = $LASTEXITCODE; Out = ($output -join "`n") }
}

function Invoke-ComposeTolerant {
    param([Parameter(Mandatory)][string[]]$Cmd)
    # Same contract, with the project, the two compose files and the profiles attached, so a
    # compose subcommand can fail on purpose without a hand-repeated argument list.
    return Invoke-DockerTolerant -Cmd (@($composeArgs) + @($Cmd))
}

function Get-PlainText {
    param($Content)
    # PowerShell 7 hands back a byte[] for content types it does not recognise as text, and the
    # actuator's media type (application/vnd.spring-boot.actuator.v3+json) is one of them. The
    # assertion below reads JSON, so decode it here rather than trust the status code alone: this
    # script once reported a healthy public endpoint as carrying no status, and the byte array in
    # the evidence file was the only clue.
    if ($Content -is [byte[]]) { return [Text.Encoding]::UTF8.GetString($Content) }
    return [string]$Content
}

function Write-Evidence {
    param([string]$Label, [string]$Text)
    Add-Content -Path $evidencePath -Value "### $Label`n$Text`n"
}

function Step {
    param([string]$Name, [scriptblock]$Body)
    Write-Host "==> $Name"
    if (-not (Test-StepSelected -Name $Name)) {
        # Listed, not omitted: the drill's value is partly that its shape is readable, and a
        # filtered run that printed only two lines would hide which of the twenty-three ran.
        $script:results.Add([pscustomobject]@{ Name = $Name; Status = 'SKIP'; Detail = 'not selected by -Only' })
        Write-Evidence "result $Name" 'SKIP not selected by -Only'
        Write-Host '    SKIP  not selected by -Only' -ForegroundColor DarkGray
        return
    }
    try {
        $detail = & $Body
        $script:results.Add([pscustomobject]@{ Name = $Name; Status = 'PASS'; Detail = "$detail" })
        Write-Evidence "result $Name" "PASS $detail"
        Write-Host "    PASS  $detail" -ForegroundColor Green
    } catch {
        $script:results.Add([pscustomobject]@{ Name = $Name; Status = 'FAIL'; Detail = $_.Exception.Message })
        Write-Evidence "result $Name" "FAIL $($_.Exception.Message)"
        Write-Host "    FAIL  $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Wait-For {
    param([string]$Description, [scriptblock]$Condition, [int]$TimeoutSec = 60, [int]$IntervalSec = 3)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $met = try { & $Condition } catch { $false }
        if ($met) { return }
        Start-Sleep -Seconds $IntervalSec
    }
    throw "timed out after ${TimeoutSec}s waiting for $Description"
}

function Get-Scrape {
    return Invoke-Compose -Cmd @('exec', '-T', 'app', 'curl', '-sS', 'http://localhost:8080/actuator/prometheus')
}

# Prometheus text format: "<name>{labels} <value>" or "<name> <value>". HELP and TYPE comments are
# skipped, so a meter that exists but has never been sampled reads as absent. That is deliberate:
# the drill asserts a counter only after driving the path that increments it.
function Get-MetricSum {
    param([string]$Scrape, [string]$Pattern)
    $sum = 0.0
    $found = $false
    foreach ($line in ($Scrape -split "`n")) {
        if ($line.StartsWith('#')) { continue }
        if ($line -match "^\s*($Pattern)(\{[^}]*\})?\s+([0-9eE.+-]+)\s*$") {
            $sum += [double]$Matches[3]
            $found = $true
        }
    }
    if (-not $found) { return $null }
    return $sum
}

function Test-Metric {
    param([string]$Scrape, [string]$Pattern)
    return $null -ne (Get-MetricSum -Scrape $Scrape -Pattern $Pattern)
}

function Get-LogFile {
    # What the prod container actually wrote to the app-logs volume, not what docker logs shows.
    return Invoke-Compose -Cmd @('exec', '-T', 'app', 'cat', '/app/logs/nexus-vibe.json')
}

# The compose file and this script have to agree on the Grafana admin password: compose resolves
# ${DRILL_GRAFANA_PASSWORD:-drill-grafana}, so the script reads the same variable and default.
$grafanaPassword = if ([string]::IsNullOrWhiteSpace($env:DRILL_GRAFANA_PASSWORD)) { 'drill-grafana' } else { $env:DRILL_GRAFANA_PASSWORD }

function Invoke-GrafanaApi {
    param([string]$Path)
    # Basic auth over loopback inside nexus-net. This is the provisioning API, so what comes back
    # is what the alerting engine loaded, not what a directory listing hopes for.
    $auth = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:$grafanaPassword"))
    return Invoke-Compose -Cmd @('exec', '-T', 'grafana', 'wget', '-qO-', "--header=Authorization: $auth",
                                 ('http://localhost:3000' + $Path))
}

function Invoke-AppHttp {
    param([string]$Method = 'GET', [string]$Path, [string]$Body = '', [string]$Token = '')
    $curl = @('exec', '-T', 'app', 'curl', '-sS', '-w', '\n%{http_code}', '-X', $Method)
    if ($Body) { $curl += @('-H', 'Content-Type: application/json', '--data-binary', $Body) }
    if ($Token) { $curl += @('-H', "Authorization: Bearer $Token") }
    $curl += ('http://localhost:8080' + $Path)
    $raw = Invoke-Compose -Cmd $curl
    $lines = @($raw -split "`n")
    $code = ($lines[-1] -replace '\D', '')
    $payload = if ($lines.Count -gt 1) { ($lines[0..($lines.Count - 2)] -join "`n").Trim() } else { '' }
    return [pscustomobject]@{ Code = $code; Body = $payload }
}

function Get-PostReviewRow {
    param([string]$PostId)
    # Nothing arrives quoted here, so the argument survives PowerShell's native argv handling:
    # the query goes in base64, the password stays inside the db container, where the entrypoint
    # already set MYSQL_ROOT_PASSWORD, and the client's password warning goes to /dev/null.
    $query = "SELECT status, ai_reviewed FROM vibe_post WHERE id=$PostId"
    $b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($query))
    $script = "echo $b64 | base64 -d | MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql -N -s nexus_campus 2>/dev/null"
    return (Invoke-Compose -Cmd @('exec', '-T', 'db', 'sh', '-c', $script)).Trim()
}

# Recreate the app against a different LLM endpoint, which is the only way to move LLM_ENDPOINT:
# it reaches the container through the compose environment, where a host value beats .env.
function Set-LlmEndpoint {
    param([string]$Endpoint)
    $env:DRILL_LLM_ENDPOINT = $Endpoint
    Invoke-Compose -Cmd @('up', '-d', '--no-build', '--no-deps', 'app') | Out-Null
    Wait-For "app answering on /actuator/health with LLM_ENDPOINT=$Endpoint" {
        (Invoke-AppHttp GET '/actuator/health').Code -in @('200', '503')
    } -TimeoutSec $StartupTimeoutSec -IntervalSec 5
}

Write-Host "Nexus-Vibe observability drill - evidence: $evidencePath" -ForegroundColor Yellow

# --------------------------------------------------------------------------- bring it up

# Refuse before touching anything. This project pins container_name for every service, so a second
# drill on the same project does not get a second stack: it gets "Conflict. The container name
# /nexus-drill-db is already in use", halfway through a build, from whichever of the two arrives
# last. That cost a run. Say it here instead, where the fix is one command.
# Only when this run owns the bring-up. Under -Only without stack-up the caller started those
# containers on purpose, and refusing them would make the filtered form unusable.
$leftover = if (Test-StepSelected -Name 'stack-up') {
    Invoke-Docker -Cmd @('ps', '-a', '--filter', 'name=nexus-drill-', '--format', '{{.Names}}')
} else { '' }
if ($leftover.Trim()) {
    throw ("drill containers from an earlier or concurrent run are still on this host:`n$leftover`n" +
           'Tear them down first (they are the drill project''s own, never a deployment):' +
           "`n  docker compose -p nexus-drill -f docker-compose.yml" +
           " -f benchmark/observability/docker-compose.drill.yml" +
           " --profile monitoring --profile drill down -v")
}

# A filtered run owns neither the build nor the bring-up: the caller decided which containers it
# needs and started them, because that is the only way a CI job can skip Elasticsearch, Ollama and
# the rest of the monitoring profile while still exercising the app's own metrics endpoint.
if (Test-StepSelected -Name 'stack-up') {
    if (-not $SkipBuild) {
        Write-Host 'building images (app, web, alert-bridge, llm-mock, webhook-sink)' -ForegroundColor DarkGray
        Invoke-Compose -Cmd @('build') | Out-Null
    }
    Invoke-Compose -Cmd @('up', '-d') | Out-Null
} else {
    Write-Host 'stack-up is not selected: leaving the build and bring-up to the caller' -ForegroundColor DarkGray
}

Step 'stack-up' {
    Wait-For 'the app to answer /actuator/health' {
        (Invoke-AppHttp GET '/actuator/health').Code -in @('200', '503')
    } -TimeoutSec $StartupTimeoutSec -IntervalSec 5
    $ps = Invoke-Compose -Cmd @('ps', '--format', '{{.Name}} {{.Status}}')
    Write-Evidence 'compose ps' $ps
    $absent = @('nexus-drill-app', 'nexus-drill-prometheus', 'nexus-drill-grafana',
                'nexus-drill-alert-bridge', 'nexus-drill-llm-mock', 'nexus-drill-webhook-sink') |
              Where-Object { $ps -notmatch "(?m)^$_\s+Up" }
    if ($absent) { throw "services not Up: $($absent -join ', ')" }
    return "app, prometheus, grafana, alert-bridge, llm-mock, webhook-sink Up; public side on $WebPort"
}

Step 'first-install-is-empty-and-administrable' {
    # What a production start is now claimed to leave behind: reference data, no demo content,
    # and exactly one account, the bootstrap admin (ADR-0008).
    $posts = Invoke-AppHttp GET '/api/v1/posts'
    if ($posts.Code -ne '200') { throw "post list answered $($posts.Code): $($posts.Body)" }
    $list = @(($posts.Body | ConvertFrom-Json).data.list)
    if ($list.Count -gt 0) {
        throw "a fresh install has $($list.Count) posts in it; init.sql is seeding content again"
    }

    $channels = @((Invoke-AppHttp GET '/api/v1/channels').Body | ConvertFrom-Json).data
    if ($channels.Count -lt 7) { throw "reference channels missing on a fresh install: $($channels.Count)" }

    $admin = Invoke-AppHttp POST '/api/v1/auth/login' -Body (@{
        username = 'admin'
        password = 'DrillAdmin123'
    } | ConvertTo-Json -Compress)
    if ($admin.Code -ne '200') { throw "the bootstrap admin could not log in ($($admin.Code)): $($admin.Body)" }
    $login = $admin.Body | ConvertFrom-Json
    if ($login.data.role -ne 'ADMIN') { throw "admin logged in with role $($login.data.role)" }
    return "$($list.Count) posts, $($channels.Count) channels, bootstrapped admin loginable as ADMIN"
}

Step 'prometheus-scrapes-app' {
    Wait-For 'prometheus to mark the app target up' {
        $targets = Invoke-Compose -Cmd @('exec', '-T', 'prometheus', 'wget', '-qO-', 'http://localhost:9090/api/v1/targets')
        Write-Evidence 'prometheus targets' $targets
        $targets -match 'app:8080' -and $targets -match '"health"\s*:\s*"up"'
    } -TimeoutSec 180 -IntervalSec 5
    return 'app:8080 health=up inside nexus-net'
}

Step 'grafana-provisioning-loaded' {
    # Grafana takes tens of seconds (sqlite migrations, then a "database is locked" retry on a
    # volume it has just created), and compose does not know it is up: healthcheck: none. Probing
    # once means this step races its own startup and loses about as often as it wins, so wait for
    # the listener the same way every other step waits for its service.
    Wait-For 'grafana to answer /api/health' {
        (Invoke-Compose -Cmd @('exec', '-T', 'grafana', 'wget', '-qO-', 'http://localhost:3000/api/health')) -match '"database"\s*:\s*"ok"'
    } -TimeoutSec 240 -IntervalSec 5
    $health = Invoke-Compose -Cmd @('exec', '-T', 'grafana', 'wget', '-qO-', 'http://localhost:3000/api/health')
    Write-Evidence 'grafana health' $health
    if ($health -notmatch '"database"\s*:\s*"ok"') { throw "grafana not ready: $health" }
    # One mount carries datasources, dashboards and alerting, so a listing shows whether the
    # provisioning directory reached the container at all.
    $files = Invoke-Compose -Cmd @('exec', '-T', 'grafana', 'ls', '-R', '/etc/grafana/provisioning')
    Write-Evidence 'grafana provisioning tree' $files
    foreach ($needed in @('rules.yaml', 'contact-points.yaml', 'policies.yaml',
                          'nexus-overview.json', 'nexus-ai-pipeline.json', 'nexus-product-loop.json')) {
        if ($files -notmatch [regex]::Escape($needed)) { throw "$needed not mounted" }
    }

    # Mounted files prove nothing on their own: a rules.yaml the engine rejects still lists fine.
    # Ask the alerting API what it loaded, by the uids the file declares.
    Wait-For 'grafana to load the provisioned alert rules' {
        $rules = Invoke-GrafanaApi '/api/v1/provisioning/alert-rules'
        @($script:ruleUids | Where-Object { $rules -notmatch $_ }).Count -eq 0
    } -TimeoutSec 120 -IntervalSec 5
    $rules = Invoke-GrafanaApi '/api/v1/provisioning/alert-rules'
    Write-Evidence 'grafana alert rules' $rules

    $points = Invoke-GrafanaApi '/api/v1/provisioning/contact-points'
    Write-Evidence 'grafana contact points' $points
    if ($points -notmatch 'nexus-feishu-bridge') { throw 'the Feishu bridge receiver is not registered' }
    # The bridge step hits the URL the engine would notify, not one this script invented.
    $url = [regex]::Match($points, '"url"\s*:\s*"(http://alert-bridge:\d+/[^"]+)"')
    if (-not $url.Success) { throw "no alert-bridge URL on the registered contact point: $points" }
    $script:bridgeUrl = $url.Groups[1].Value

    # The file listing above proves the mount; this proves the engine's own reading of the two
    # state knobs, which is where a misspelled enum value used to disappear. Grafana now exits on a
    # file it cannot parse, so a crash-looping grafana shows up here as /api/health refusing; the
    # counts below catch the milder version, where a value is accepted but not the one intended.
    $loaded = Invoke-GrafanaApi '/api/v1/provisioning/alert-rules'
    $loud = ([regex]::Matches($loaded, '"noDataState"\s*:\s*"Alerting"')).Count
    $quiet = ([regex]::Matches($loaded, '"noDataState"\s*:\s*"OK"')).Count
    Write-Evidence 'engine noDataState counts' "Alerting=$loud OK=$quiet"
    if ($loud -lt 3 -or $quiet -lt 3) {
        throw "the engine loaded Alerting=$loud OK=$quiet, expected 3 and 3 - a rule's no-data policy" +
              ' was dropped, misspelled, or never made it out of the file'
    }

    return "$($script:ruleUids.Count) rules loaded by the engine, receiver registered at $script:bridgeUrl"
}

# --------------------------------------------------------------------------- metric surface

Step 'metrics-registered' {
    Wait-For 'the JVM series to appear' { (Get-Scrape) -match 'jvm_memory_used_bytes' } -TimeoutSec 60
    $scrape = Get-Scrape
    Write-Evidence 'app scrape' $scrape
    # Only meters that exist from construction: the gauges plus the JVM/HTTP series. Counters are
    # asserted below, after the code path that increments them has actually run.
    $required = @(
        'jvm_memory_used_bytes',
        'http_server_requests_seconds_count',
        'llm_circuit_breaker_open',
        'ai_review_pending_posts',
        'llm_chat_completion_duration_seconds_count'
    )
    $missing = @($required | Where-Object { -not (Test-Metric -Scrape $scrape -Pattern $_) })
    if ($missing) { throw "missing series: $($missing -join ', ')" }
    # The SLO buckets are the whole point of the timer; a histogram with no le= lines is a
    # dashboard with no p95. le= comes after the common tags in the exposition format.
    if ($scrape -notmatch 'llm_chat_completion_duration_seconds_bucket\{[^}]*le="30\.0"') {
        throw 'the 30s SLO bucket is missing from llm_chat_completion_duration_seconds'
    }
    # Every alert expression selects on this tag; without it they match nothing and sit quietly
    # in their noDataState.
    if ($scrape -notmatch 'application="nexus-vibe"') { throw 'no application="nexus-vibe" tag on the scrape' }
    return "$($required.Count) series present, SLO buckets and application tag in place"
}

Step 'os-metrics-in-prod-container' {
    $scrape = Get-Scrape
    $missing = @('system_cpu_usage', 'disk_free_bytes', 'process_uptime_seconds') |
               Where-Object { -not (Test-Metric -Scrape $scrape -Pattern $_) }
    Write-Evidence 'os metric probe' ("missing: " + ($missing -join ', '))
    if ($missing) {
        $tail = Invoke-Compose -Cmd @('logs', '--tail', '300', 'app')
        if ($tail -match 'CgroupV2Subsystem') {
            throw "cgroup detection crashed the JVM again: restore the SystemMetricsAutoConfiguration exclude and record T2 as blocked ($($missing -join ', '))"
        }
        throw "no OS/disk metrics in the prod container: $($missing -join ', ')"
    }
    return 'system_cpu_usage, disk_free_bytes, process_uptime present without the exclude'
}

Step 'product-loop-drives-the-counters' {
    # R4 claims the meters move when the product is used, so this uses the product:
    # register, publish, moderate, reply. Nothing else in a drill run registers an
    # account and the container has been up since the stack came up, so each of these
    # counters is read from zero and an exact number means something.
    $suffix = Get-Random -Maximum 99999
    $username = "drill$suffix"

    $registered = Invoke-AppHttp POST '/api/v1/auth/register' -Body (@{
        username = $username
        password = 'Drill1234x'
        email    = "$username@drill.local"
        nickname = "Drill $suffix"
    } | ConvertTo-Json -Compress)
    if ($registered.Code -ne '200') { throw "register answered $($registered.Code): $($registered.Body)" }
    $token = ($registered.Body | ConvertFrom-Json).data.token
    if (-not $token) { throw "register returned no token: $($registered.Body)" }

    $created = Invoke-AppHttp POST '/api/v1/posts' -Token $token -Body (@{
        title      = "Drill post $suffix"
        content    = 'A drill post carrying a python block: int x = 1'
        categoryId = 2
    } | ConvertTo-Json -Compress)
    if ($created.Code -ne '200') { throw "create post answered $($created.Code): $($created.Body)" }
    $postId = (($created.Body | ConvertFrom-Json).data).postId

    $commented = Invoke-AppHttp POST '/api/v1/comments' -Token $token -Body (@{
        postId  = [long]$postId
        content = 'a drill comment on the drill post'
    } | ConvertTo-Json -Compress)
    if ($commented.Code -ne '200') { throw "create comment answered $($commented.Code): $($commented.Body)" }

    $adminLogin = Invoke-AppHttp POST '/api/v1/auth/login' -Body (@{
        username = 'admin'
        password = 'DrillAdmin123'
    } | ConvertTo-Json -Compress)
    if ($adminLogin.Code -ne '200') { throw "admin login answered $($adminLogin.Code): $($adminLogin.Body)" }
    $adminToken = ($adminLogin.Body | ConvertFrom-Json).data.token
    $approved = Invoke-AppHttp POST "/api/v1/admin/audit/posts/$postId/approve" -Token $adminToken
    if ($approved.Code -ne '200') { throw "approve answered $($approved.Code): $($approved.Body)" }

    $scrape = Get-Scrape
    $observed = [ordered]@{
        user_registered_total   = Get-MetricSum -Scrape $scrape -Pattern 'user_registered_total'
        post_submitted_total    = Get-MetricSum -Scrape $scrape -Pattern 'post_submitted_total'
        post_audited_total      = Get-MetricSum -Scrape $scrape -Pattern 'post_audited_total'
        comment_submitted_total = Get-MetricSum -Scrape $scrape -Pattern 'comment_submitted_total'
    }
    Write-Evidence 'product-loop counters' (($observed.GetEnumerator() |
        ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "`n")
    # Absent and zero are different failures and used to read as one: `-not 0` is true in
    # PowerShell, so a meter exported at 0 was reported as absent. The first run of this step hit
    # the other case for a reason nothing above the scrape could see - the process really did
    # count the events, but it published them as `post_total` and `comment_total`, because the
    # Prometheus naming convention eats a trailing `created` token and a SimpleMeterRegistry
    # keeps names verbatim. Presence is $null; movement is the sum.
    $absent = @($observed.Keys | Where-Object { $null -eq $observed[$_] })
    if ($absent) { throw "counters absent from the scrape under these names: $($absent -join ', ')" }
    $still = @($observed.Keys | Where-Object { [double]$observed[$_] -le 0 })
    if ($still) {
        throw 'counters present but never moved: ' +
              (($still | ForEach-Object { "$_=$($observed[$_])" }) -join ', ')
    }

    # The ratios come from MySQL rather than from these counters, so they need the sweep
    # to have run since. docker-compose.drill.yml moves it from production's 03:17 to
    # every minute; the wait is one cycle plus a scrape interval, not a guess.
    Wait-For 'the funnel sweep to publish a non-zero activation ratio' {
        $value = Get-MetricSum -Scrape (Get-Scrape) -Pattern 'funnel_activation_ratio'
        $null -ne $value -and $value -gt 0
    } -TimeoutSec 150 -IntervalSec 10
    $scrape = Get-Scrape
    $activation = Get-MetricSum -Scrape $scrape -Pattern 'funnel_activation_ratio'
    $activeD7 = Get-MetricSum -Scrape $scrape -Pattern 'funnel_active_content_d7_ratio'
    Write-Evidence 'funnel gauges' "activation=$activation active_content_d7=$activeD7"
    foreach ($pair in @(@('activation', $activation), @('active_content_d7', $activeD7))) {
        if ($pair[1] -lt 0 -or $pair[1] -gt 1) { throw "$($pair[0]) ratio outside 0..1: $($pair[1])" }
    }
    return "4 counters moved; activation=$activation, active_content_d7=$activeD7"
}

Step 'structured-json-log-lands-on-the-volume' {
    # T1 claims prod writes JSON to a named volume; nothing but a running prod container proves
    # that. Tying the check to a trace id earned from a real request covers T6's claim in the
    # same shot: the MDC value has to survive the async appender and reach the file.
    $answer = Invoke-WebRequest -Uri "http://localhost:$WebPort/api/v1/posts?page=1&size=1" -SkipHttpErrorCheck
    $trace = ($answer.Headers['X-Trace-Id'] | Select-Object -First 1)
    if (-not $trace) { throw 'the request meant for the log file carried no X-Trace-Id' }

    # AsyncAppender plus the file appender's own buffer: poll, never assume a line is on disk.
    Wait-For "trace $trace to reach /app/logs/nexus-vibe.json" {
        ((Get-LogFile) -split "`n") -match $trace
    } -TimeoutSec 90 -IntervalSec 3

    $line = @((Get-LogFile) -split "`n" | Where-Object { $_ -match $trace -and $_.StartsWith('{') }) | Select-Object -First 1
    Write-Evidence 'json log line' $line
    $doc = $line | ConvertFrom-Json
    $absent = @('@timestamp', 'level', 'logger_name', 'message', 'traceId', 'app') |
              Where-Object { $traceField = $_; -not $doc.PSObject.Properties[$traceField] }
    if ($absent) { throw "the JSON line has no field(s): $($absent -join ', ')" }
    if ($doc.traceId -ne $trace) { throw "the JSON line says traceId=$($doc.traceId), the response header said $trace" }
    return "one JSON object per event on app-logs, traceId=$trace from header to disk"
}

# --------------------------------------------------------------------------- the outage

$script:token = ''

Step 'llm-outage-parks-and-degrades' {
    Set-LlmEndpoint 'http://127.0.0.1:1/v1'

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $registered = Invoke-AppHttp POST '/api/v1/auth/register' -Body (@{
        username = "drill$now"
        email    = "drill$now@example.com"
        password = 'DrillPass123'
        nickname = 'Drill Runner'
    } | ConvertTo-Json -Compress)
    if ($registered.Code -ne '200') { throw "register returned $($registered.Code): $($registered.Body)" }
    $script:token = ($registered.Body | ConvertFrom-Json).data.token
    if (-not $script:token) { throw "no token in the register body: $($registered.Body)" }

    # Three posts, not one: the review each one dispatches burns its retries and re-opens the
    # breaker for the shipped 60s, so the outage holds the breaker open long enough for a scrape
    # to catch it. That is also what the 5-minute breaker alert needs in the field.
    $fence = [string]([char]0x60) * 3
    $script:postIds = @()
    foreach ($n in 1..3) {
        $created = Invoke-AppHttp POST '/api/v1/posts' -Token $script:token -Body (@{
            title      = "Drill outage post $now-$n"
            content    = "Review this please:`n`n$fence`njava`nSystem.out.println(1);`n$fence"
            categoryId = 2
        } | ConvertTo-Json -Compress)
        if ($created.Code -ne '200') { throw "post $n returned $($created.Code): $($created.Body)" }
        $script:postIds += ($created.Body | ConvertFrom-Json).data.postId
    }
    Write-Evidence 'outage posts' ($script:postIds -join "`n")

    # Fail-closed is a state, so read the row rather than the prose: which code path writes it
    # depends on where the cached health probe lands when the post arrives, and the two enqueue
    # paths (VibePostServiceImpl, AiSafetyCheckListener) word their log lines differently.
    # status=2 is PENDING_REVIEW, ai_reviewed=3 is FAILED awaiting retry.
    foreach ($postId in $script:postIds) {
        Wait-For "post $postId to sit in PENDING_REVIEW with the review parked" {
            (Get-PostReviewRow -PostId $postId) -match '^2\s+3$'
        } -TimeoutSec 150 -IntervalSec 5
    }
    Wait-For 'the circuit breaker gauge to read 1' {
        (Get-MetricSum -Scrape (Get-Scrape) -Pattern 'llm_circuit_breaker_open') -eq 1
    } -TimeoutSec 150 -IntervalSec 5

    $scrape = Get-Scrape
    $completions = (($scrape -split "`n") | Where-Object { $_ -match '^llm_chat_completions_total' }) -join "`n"
    Write-Evidence 'llm completion outcomes' $completions
    if ($completions -notmatch 'outcome="failure"') {
        throw "the outage produced no failure outcome on llm_chat_completions_total:`n$completions"
    }
    # Shedding is only observable if a call enters while the breaker is open, so it is recorded
    # rather than required.
    $shed = $completions -match 'outcome="circuit_open"'

    # The backlog gauge is written by the reconcile sweep, which runs at :03/:08/... and refreshes
    # the count ahead of its health gate, so the honest wait here is one cron cycle.
    Wait-For 'ai_review_pending_posts to report the parked post' {
        (Get-MetricSum -Scrape (Get-Scrape) -Pattern 'ai_review_pending_posts') -gt 0
    } -TimeoutSec 420 -IntervalSec 20

    $pending = Get-MetricSum -Scrape (Get-Scrape) -Pattern 'ai_review_pending_posts'
    $health = Invoke-AppHttp GET '/actuator/health'
    if ($health.Code -ne '200') { throw "health answered $($health.Code) with only the LLM down: $($health.Body)" }
    if ($health.Body -notmatch '"status":"DEGRADED"') { throw "expected DEGRADED, got $($health.Body)" }
    Write-Evidence 'health during outage' "$($health.Code) $($health.Body)"
    $breakerWord = if ($shed) { 'and shedding' } else { 'not yet shedding' }
    return "$($script:postIds.Count) posts parked, breaker=1 $breakerWord, backlog=$pending, health 200 DEGRADED"
}

Step 'container-not-killed-while-degraded' {
    # One full healthcheck interval past the outage: the Dockerfile polls /actuator/health every
    # 30s with 3 retries, so a DEGRADED-maps-to-503 regression shows up here as Docker itself
    # judging a serving container fatal.
    Start-Sleep -Seconds 70
    $inspect = Invoke-Docker -Cmd @('inspect', '--format', '{{.State.Health.Status}} {{.RestartCount}}', 'nexus-drill-app')
    Write-Evidence 'docker inspect nexus-drill-app' $inspect
    $parts = $inspect -split '\s+'
    if ($parts[0] -ne 'healthy') { throw "docker did not stay on healthy while the app served degraded: $inspect" }
    if ([int]$parts[1] -gt 0) { throw "the app container restarted $($parts[1]) times during the drill" }
    return "health=$($parts[0]) restarts=$($parts[1]) while DEGRADED"
}

# --------------------------------------------------------------------------- rate limit

Step 'rate-limit-rejects-and-counts' {
    # 10 requests per minute per path per IP. A credential-stuffing shape is the honest way to
    # reach it, and /auth/login is rate limited precisely so that this stays cheap.
    $codes = @()
    for ($i = 0; $i -lt 13; $i++) {
        $response = Invoke-AppHttp POST '/api/v1/auth/login' -Body (@{
            username = 'nobody'
            password = 'wrong-password'
        } | ConvertTo-Json -Compress)
        $codes += $response.Code
    }
    $rejected = @($codes | Where-Object { $_ -eq '429' }).Count
    $scrape = Get-Scrape
    $sum = Get-MetricSum -Scrape $scrape -Pattern 'rate_limit_rejected_total'
    Write-Evidence 'rate limit flood' ("codes: " + ($codes -join ',') + "`n" +
        ((($scrape -split "`n") | Where-Object { $_ -match 'rate_limit' }) -join "`n"))
    if ($rejected -eq 0) { throw "no 429 among $($codes -join ',')" }
    if (-not $sum) { throw "rate_limit_rejected_total absent from the scrape despite $rejected rejections" }
    return "$rejected of $($codes.Count) refused, rate_limit_rejected_total=$sum"
}

Step 'alert-rules-select-real-metrics' {
    $rules = Get-Content -Raw 'docker/observability/grafana/provisioning/alerting/rules.yaml'
    $absent = @($script:ruleUids | Where-Object { $rules -notmatch $_ })
    if ($absent) { throw "rules missing from provisioning: $($absent -join ', ')" }

    $exprs = @([regex]::Matches($rules, "expr:\s*'([^']+)'") | ForEach-Object { $_.Groups[1].Value })
    # `application=` selects our own series. The scrapability rule is the exception it cannot avoid:
    # `up` is synthesized by Prometheus from the scrape, so it carries job= and never could carry an
    # application tag. Every other expression must still name the application, or a second deployment
    # on the same Prometheus would answer for this one.
    $untagged = @($exprs | Where-Object { $_ -notmatch 'application="nexus-vibe"' -and $_ -notmatch 'job="nexus-vibe"' })
    if ($untagged) { throw "expressions with no selector: $($untagged -join ' ;; ')" }

    # A name inside an expression has to be a name this build exposes, or the rule is decoration.
    $scrape = Get-Scrape
    $names = @([regex]::Matches(($exprs -join ' '), '([a-z_]+)\{(?:application|job)') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique)
    # `up` belongs to Prometheus, not to the app, so it cannot be checked against the app's scrape.
    $names = @($names | Where-Object { $_ -ne 'up' })
    $unknown = @($names | Where-Object {
        $escaped = [regex]::Escape($_)
        -not (Test-Metric -Scrape $scrape -Pattern $escaped) -and
        -not (Test-Metric -Scrape $scrape -Pattern "${escaped}_total") -and
        -not (Test-Metric -Scrape $scrape -Pattern "${escaped}_count")
    })
    if ($unknown) { throw "alert expressions select on metrics that do not exist: $($unknown -join ', ')" }

    $contact = Get-Content -Raw 'docker/observability/grafana/provisioning/alerting/contact-points.yaml'
    if ($contact -notmatch 'alert-bridge') { throw 'no contact point aimed at the alert bridge' }
    return "$($script:ruleUids.Count) rules, $($exprs.Count) expressions, metrics named: $($names -join ', ')"
}

Step 'alert-no-data-policy-is-per-rule' {
    # Four of these rules once sat in noDataState OK, so an app that stopped being scraped reported
    # healthy. That is not one policy for every rule: a gauge that exists from the moment the process
    # registers it means "no data" is "no process", while a ratio needs traffic to mean anything, so
    # no data there is usually quiet rather than broken.
    $rules = Get-Content -Raw 'docker/observability/grafana/provisioning/alerting/rules.yaml'
    $blocks = @([regex]::Matches($rules, "(?ms)- uid:\s*(\S+)(.*?)(?=\r?\n\s*- uid:|\z)"))
    if ($blocks.Count -lt 5) { throw "only $($blocks.Count) rule blocks parsed from rules.yaml" }
    $expected = @{
        'nexus-llm-breaker-open'           = 'Alerting'
        'nexus-ai-review-backlog'          = 'Alerting'
        'nexus-prometheus-scrape-failed'   = 'Alerting'
        'nexus-http-5xx-ratio'             = 'OK'
        'nexus-rate-limit-spike'           = 'OK'
        'nexus-availability-999-fast-burn' = 'OK'
    }
    $seen = @()
    foreach ($block in $blocks) {
        $uid = $block.Groups[1].Value
        if (-not $expected.ContainsKey($uid)) { throw "rule $uid has no no-data policy stated in this drill" }
        foreach ($key in @('noDataState', 'execErrState')) {
            $value = [regex]::Match($block.Groups[2].Value, ($key + ':\s*(\S+)')).Groups[1].Value
            # Grafana resolves these four spellings and nothing else; ALERTING and alerting both abort
            # startup, which took the dashboard and the notifier down with it. The engine check above
            # is the real gate; this one says which rule is unwritable before the stack ever comes up.
            if ($value -notin @('Alerting', 'NoData', 'OK', 'KeepState')) {
                throw "$uid sets ${key}: $value, which is not one of Alerting | NoData | OK | KeepState"
            }
        }
        $state = [regex]::Match($block.Groups[2].Value, 'noDataState:\s*(\S+)').Groups[1].Value
        $seen += "$uid=$state"
        if ($state -ne $expected[$uid]) {
            throw "$uid reports noDataState $state, expected $($expected[$uid])"
        }
    }
    Write-Evidence 'no-data policy per rule' ($seen -join "`n")
    $loud = @($blocks | Where-Object { $_.Groups[2].Value -match 'noDataState:\s*Alerting\b' }).Count
    return "$($seen -join '  ')  ($loud treat missing data as an incident)"
}

Step 'bridge-refuses-to-start-without-a-target' {
    # E3 replaced "start anyway and answer 502 to every alert" with "do not start at all", so the
    # proof is an exit code and a reason, not a response body. The running service carries the
    # drill's webhook, so this clears it on a throwaway container of the same image: the tag is the
    # one docker-compose.yml pins for alert-bridge, and a rename there has to be followed here.
    $run = Invoke-DockerTolerant -Cmd @('run', '--rm', '-e', 'FEISHU_ALERT_WEBHOOK=', 'nexus-alert-bridge:local')
    Write-Evidence 'bridge with no webhook' "exit=$($run.Code)`n$($run.Out)"
    if ($run.Code -eq 0) { throw 'the bridge started happily with FEISHU_ALERT_WEBHOOK cleared' }
    if ($run.Out -notmatch 'refusing to start') { throw "it exited $($run.Code) without saying why: $($run.Out)" }
    if ($run.Out -notmatch 'FEISHU_ALERT_WEBHOOK is not set') { throw 'the refusal never named the missing variable' }
    return "exited $($run.Code) naming FEISHU_ALERT_WEBHOOK as the reason"
}

Step 'alert-delivers-to-the-far-side' {
    # Everything above proves a rule can fire. This proves the notification has somewhere to go and
    # that the bytes that arrive are the ones Feishu will accept: the sink recomputes the signature
    # from the same secret the bridge signs with, and answers 200-with-an-error-code on a mismatch,
    # which is the shape that used to let a refused alert read as delivered.
    $payload = (@{
        status            = 'firing'
        commonAnnotations = @{ description = 'drill alert, not a real outage' }
        alerts            = @(@{
            status      = 'firing'
            labels      = @{ alertname = 'DrillProbe'; severity = 'critical' }
            annotations = @{ description = 'drill alert, not a real outage' }
            # A real Grafana payload always carries this map, as plain numbers keyed by refId, and
            # leaving it out is how the bridge's assumption about its shape survived this step: the
            # bridge raised AttributeError on `1` before it ever called out, and a drill that never
            # sent a value could not see it. `1` is the interesting one -- `0` is falsy and used to
            # fall through to "no reading" (see docs/research/alert-bridge-values-shape-2026-09.md).
            values      = @{ A = 1 }
        })
    } | ConvertTo-Json -Compress -Depth 6)

    # The app container is the courier: it can reach the bridge over nexus-net and it has curl,
    # while the bridge's own busybox wget cannot POST a body. The URL is the one the Grafana engine
    # registered, so this is the request a real alert would make.
    if (-not $script:bridgeUrl) { throw 'no bridge URL was captured from the Grafana contact point' }
    $curl = @('exec', '-T', 'app', 'curl', '-sS', '-w', '\n%{http_code}', '-X', 'POST',
              '-H', 'Content-Type: application/json', '--data-binary', $payload,
              $script:bridgeUrl)
    $raw = Invoke-Compose -Cmd $curl
    $code = ((@($raw -split "`n")[-1]) -replace '\D', '')
    $bridgeLogs = Invoke-Compose -Cmd @('logs', '--tail', '40', 'alert-bridge')
    $sinkLogs = Invoke-Compose -Cmd @('logs', '--since', '15m', 'webhook-sink')
    Write-Evidence 'delivery' "bridge answer: $raw`n--- alert-bridge ---`n$bridgeLogs`n--- webhook-sink ---`n$sinkLogs"
    if ($code -ne '200') { throw "the bridge answered $code instead of forwarding: $raw" }
    if ($bridgeLogs -notmatch '\[alert-bridge\] notify state=') { throw 'the bridge logged no notification at all' }
    if ($sinkLogs -notmatch 'signature=ok') { throw "the far side never accepted a signed alert: $sinkLogs" }
    if ($sinkLogs -notmatch 'DrillProbe') { throw 'the alert title never reached the far side' }
    return 'Grafana-shaped notification reached the bridge, signed, and one receiver accepted it'
}

# --------------------------------------------------------------------------- recovery

Step 'reconcile-repairs-after-llm-returns' {
    Set-LlmEndpoint 'http://llm-mock:8000/v1'
    # Recreating the container zeroes the meters, so a repair counter in the scrape can only have
    # come from this sweep. What has to happen first is shipped timing, not a drill knob: the
    # breaker cools off, the cached health probe expires, the stale window passes, and the cron
    # gets a turn.
    Wait-For 'the reconcile sweep to repair a parked review' {
        (Get-MetricSum -Scrape (Get-Scrape) -Pattern 'ai_review_reconcile_repairs_total') -gt 0
    } -TimeoutSec $RecoveryTimeoutSec -IntervalSec 20

    $scrape = Get-Scrape
    $repairs = Get-MetricSum -Scrape $scrape -Pattern 'ai_review_reconcile_repairs_total'
    $breaker = Get-MetricSum -Scrape $scrape -Pattern 'llm_circuit_breaker_open'
    $backlog = Get-MetricSum -Scrape $scrape -Pattern 'ai_review_pending_posts'
    $logs = Invoke-Compose -Cmd @('logs', '--since', '30m', 'app')
    Write-Evidence 'reconcile log lines' ((($logs -split "`n") | Where-Object { $_ -match 'AI-RECONCILE' }) -join "`n")
    Write-Evidence 'post-repair review rows' (@($script:postIds | ForEach-Object {
        "$_ -> $(Get-PostReviewRow -PostId $_)"
    }) -join "`n")
    return "repairs=$repairs, breaker=$breaker, backlog=$backlog"
}

Step 'deps-group-names-every-component' {
    $deps = Invoke-AppHttp GET '/actuator/health/deps'
    Write-Evidence 'health deps' "$($deps.Code) $($deps.Body)"
    if ($deps.Code -ne '200') { throw "the deps group answered $($deps.Code)" }
    $absent = @('db', 'redis', 'elasticsearch', 'llm') | Where-Object { $deps.Body -notmatch "`"$_`"" }
    if ($absent) { throw "missing from the deps group: $($absent -join ', ')" }
    return 'db, redis, elasticsearch and llm all named with detail'
}

# --------------------------------------------------------------------------- public side

Step 'public-nginx-denies-actuator' {
    $probes = [ordered]@{
        '/actuator/health'      = 200
        '/actuator/prometheus'  = 404
        '/actuator/health/deps' = 404
        '/actuator/env'         = 404
        '/actuator/metrics'     = 404
    }
    $seen = @()
    foreach ($path in $probes.Keys) {
        $answer = Invoke-WebRequest -Uri "http://localhost:$WebPort$path" -SkipHttpErrorCheck
        $seen += "$path=$($answer.StatusCode)"
        if ($answer.StatusCode -ne $probes[$path]) {
            throw "$path answered $($answer.StatusCode), expected $($probes[$path]) ($($seen -join ' '))"
        }
    }
    Write-Evidence 'public probes' ($seen -join "`n")
    # The other half of E6's acceptance: metrics is a dev tool, so it must be gone at the prod
    # container's own port as well as at the edge. The edge denying it proves nothing about the
    # exposure list, and the exposure contract test proves nothing about a running prod JVM.
    $metrics = Invoke-AppHttp GET '/actuator/metrics'
    Write-Evidence 'prod container metrics' "$($metrics.Code)"
    if ($metrics.Code -ne '404') {
        throw "the prod container still answers /actuator/metrics with $($metrics.Code) - exposure is not prod's allowlist"
    }
    return ($seen -join '  ')
}

Step 'trace-id-visible-to-a-user' {
    $response = Invoke-WebRequest -Uri "http://localhost:$WebPort/api/v1/channels" -SkipHttpErrorCheck
    $trace = ($response.Headers['X-Trace-Id'] | Select-Object -First 1)
    Write-Evidence 'trace header' "$trace"
    if (-not $trace) { throw 'no X-Trace-Id on a response through nginx' }
    $logs = Invoke-Compose -Cmd @('logs', '--since', '20m', 'app')
    if ($logs -notmatch [regex]::Escape($trace)) { throw "trace $trace never appears in the application log" }
    return "$trace reached the browser and the log line"
}

Step 'public-health-answers-only-servability' {
    # Same URL, narrower document: the public /actuator/health is proxied onto the servable group, so
    # it may carry a verdict and nothing else. If components, the group list or a dependency name ever
    # appear here, an outside probe is being told which dependency is unhappy and ADR-0007's two
    # audiences have collapsed back into one.
    $public = Get-PlainText (Invoke-WebRequest -Uri "http://localhost:$WebPort/actuator/health" -SkipHttpErrorCheck).Content
    $internal = Invoke-AppHttp GET '/actuator/health/deps'
    Write-Evidence 'public vs internal health' "public: $public`ninternal deps: $($internal.Body)"
    if ($public -notmatch '"status"') { throw 'public health carries no status' }
    foreach ($leak in @('components', 'groups', 'llm', 'elasticsearch')) {
        if ($public -match "`"$leak`"") { throw "public health leaks dependency detail: $leak" }
    }
    if ($internal.Body -notmatch '"llm"') { throw 'the internal deps group stopped naming llm, so this comparison proves nothing' }
    return 'public answers servability only; dependency detail stays on the internal group'
}

Step 'app-death-is-not-reported-as-health' {
    # Everything above proves the app can be degraded and stay servable. This is the other half of E3:
    # when the app stops answering, the stack has to say so instead of settling into the no-data
    # corner it used to call healthy.
    Invoke-Compose -Cmd @('stop', 'app') | Out-Null
    try {
        Wait-For 'Prometheus to notice the app is gone' {
            $q = Invoke-Compose -Cmd @('exec', '-T', 'prometheus', 'wget', '-qO-',
                'http://localhost:9090/api/v1/query?query=min_over_time(up%7Bjob%3D%22nexus-vibe%22%7D%5B1m%5D)')
            Write-Evidence 'up query' $q
            $q -match '"value":\[[0-9.]+,"0"\]'
        } -TimeoutSec 180 -IntervalSec 5 | Out-Null

        # And the public edge agrees by failing rather than serving a cheerful 200.
        $answer = Invoke-WebRequest -Uri "http://localhost:$WebPort/actuator/health" -SkipHttpErrorCheck
        Write-Evidence 'public health while app down' "$($answer.StatusCode)"
        if ($answer.StatusCode -eq 200) { throw 'public health still answered 200 with the app stopped' }
        $detail = "up=0 observed; public /actuator/health=$($answer.StatusCode)"
    } finally {
        Invoke-Compose -Cmd @('start', 'app') | Out-Null
        Wait-For 'the app to be scraped again' {
            $t = Invoke-Compose -Cmd @('exec', '-T', 'prometheus', 'wget', '-qO-', 'http://localhost:9090/api/v1/targets')
            $t -match 'app:8080' -and $t -match '"health"\s*:\s*"up"'
        } -TimeoutSec 300 -IntervalSec 5 | Out-Null
    }
    return $detail
}

Step 'non-root-app-and-the-root-owned-volume-upgrade' {
    # R5 has two claims. The app runs as uid 10001, and a host that already has the
    # old root-owned volumes can still upgrade. The second is the one that cannot be
    # checked by a fresh install: Docker seeds a named volume from the image only
    # while the volume is empty, so every machine that ran this stack before R5 keeps
    # root:root directories that a non-root JVM cannot write into.
    $uid = (Invoke-Compose -Cmd @('exec', '-T', 'app', 'id', '-u')).Trim()
    Write-Evidence 'container uid' $uid
    if ($uid -ne '10001') { throw "the app process runs as uid $uid, expected 10001" }

    # Locate the volumes themselves rather than guessing the names: a wrong guess makes
    # `docker run -v` create an empty volume and the rest of the step would prove
    # nothing at all while passing.
    $volumes = @{}
    foreach ($mount in @(@('uploads', 'app-uploads'), @('logs', 'app-logs'))) {
        $found = @((Invoke-Docker -Cmd @('volume', 'ls', '--filter', "name=$($mount[1])",
                                         '--format', '{{.Name}}')) -split "`n" |
                   ForEach-Object { $_.Trim() } | Where-Object { $_ -match "^nexus-drill_$($mount[1])$" })
        if ($found.Count -ne 1) { throw "expected exactly one nexus-drill_$($mount[1]) volume, found $($found.Count)" }
        $volumes[$mount[0]] = $found[0]
    }

    # Rewrite both volumes. The sentinel file matters: Docker seeds a named volume from
    # the image only while it is empty, so a volume with content in it is guaranteed to
    # keep whatever ownership it is given, which is the state a machine that ran this
    # stack before R5 is actually in.
    function Repair-VolumeOwner {
        param([string]$Spec)
        Invoke-Docker -Cmd @('run', '--rm',
            '-v', "$($volumes['uploads']):/uploads",
            '-v', "$($volumes['logs']):/logs",
            'alpine', 'sh', '-c',
            "chown -R $Spec /uploads /logs && touch /uploads/pre-r5-sentinel /logs/pre-r5-sentinel") | Out-Null
    }

    Invoke-Compose -Cmd @('stop', 'app') | Out-Null
    Repair-VolumeOwner -Spec '0:0'
    Invoke-Compose -Cmd @('up', '-d', '--no-build', '--no-deps', 'app') | Out-Null
    Wait-For 'app answering /actuator/health against root-owned volumes' {
        (Invoke-AppHttp GET '/actuator/health').Code -in @('200', '503')
    } -TimeoutSec $StartupTimeoutSec -IntervalSec 5

    # A volume it cannot write must not take the container down. Until 2026-09-16 it did: Spring
    # Boot turns logback's failed openFile into an IllegalStateException during
    # prepareEnvironment, so the app restart-looped here and never answered /actuator/health at
    # all - ten restarts in four minutes, and the public site down for a reason no operator would
    # read as "the log directory is root-owned". The entry point probes the directory now, so the
    # two failures left are the honest ones: uploads break, and file logging moves somewhere
    # non-durable. Both are asserted below, because an operator only gets one chance to notice
    # before this becomes the production host.
    $probe = Invoke-ComposeTolerant -Cmd @('exec', '-T', 'app', 'touch', '/app/uploads/r5-probe')
    Write-Evidence 'write probe on a root-owned volume' "exit=$($probe.Code) $($probe.Out)"
    if ($probe.Code -eq 0) {
        throw 'a non-root app wrote to a root-owned uploads volume; the ownership was never actually root,' +
              ' so this step proved nothing about the upgrade path'
    }

    # The fallback has to be loud, or a deployment loses its durable logs quietly.
    $startup = Invoke-Compose -Cmd @('logs', '--tail', '60', 'app')
    Write-Evidence 'entry-point warning' (($startup -split "`n" |
        Where-Object { $_ -match 'not writable by uid' } | Select-Object -First 1))
    if ($startup -notmatch 'not writable by uid 10001') {
        throw 'the app came up against a root-owned /app/logs without warning about it'
    }
    $fallback = Invoke-ComposeTolerant -Cmd @('exec', '-T', 'app', 'tail', '-n', '1',
                                              '/tmp/nexus-logs/nexus-vibe.json')
    Write-Evidence 'fallback json log' "exit=$($fallback.Code)"
    if ($fallback.Code -ne 0) {
        throw 'the JSON log did not land in the fallback directory either; file logging is simply off'
    }

    # The documented migration from docs/plans/pre-deployment-checklist.md, run while the
    # app is up, because that is how an operator reaches for it: the container is already
    # running and failing, and a share-able named volume can be rewritten underneath it.
    Repair-VolumeOwner -Spec '10001:10001'

    # Uploads recover without a restart: a fresh FileOutputStream only needs a writable
    # directory. The JSON log is the opposite case - logback opens its file once, at
    # configuration, and the entry point had already chosen /tmp - so it needs the restart.
    # The mtime comparison is the load-bearing part: the file exists from the first boot, so a
    # bare read would pass on a line written before the migration happened.
    Invoke-Compose -Cmd @('exec', '-T', 'app', 'touch', '/app/uploads/r5-probe') | Out-Null
    $logMtime = (Invoke-Compose -Cmd @('exec', '-T', 'app', 'stat', '-c', '%Y',
                                       '/app/logs/nexus-vibe.json')).Trim()
    Invoke-Compose -Cmd @('restart', 'app') | Out-Null
    Wait-For 'app answering after the chown migration' {
        (Invoke-AppHttp GET '/actuator/health').Code -in @('200', '503')
    } -TimeoutSec $StartupTimeoutSec -IntervalSec 5
    Wait-For 'the JSON log to be written again on the volume, by uid 10001' {
        $now = (Invoke-ComposeTolerant -Cmd @('exec', '-T', 'app', 'stat', '-c', '%Y',
                                              '/app/logs/nexus-vibe.json')).Out.Trim()
        ($now -match '^\d+$') -and ([long]$now -gt [long]$logMtime)
    } -TimeoutSec 90 -IntervalSec 5

    $sentinel = Invoke-Compose -Cmd @('exec', '-T', 'app', 'ls', '/app/uploads')
    if ($sentinel -notmatch 'pre-r5-sentinel') { throw 'the pre-R5 sentinel is gone; the migration touched the wrong volume' }
    $uidAfter = (Invoke-Compose -Cmd @('exec', '-T', 'app', 'stat', '-c', '%u:%g', '/app/uploads')).Trim()
    Write-Evidence 'after migration' "uid=$uidAfter uploads listing: $($sentinel -replace "`n", ' ')"
    return "uid 10001; root-owned /app/logs serves with a warning and logs in /tmp (proven), uploads fail; checklist chown restores uploads without a restart and the JSON log with one (mtime advanced)"
}

Step 'rollback-swaps-between-two-real-image-tags' {
    # E4's acceptance line, and the one thing a named tag has to be able to do. Two genuinely
    # different images are required: retagging one build twice would prove that compose can
    # interpolate a string, which nobody doubted — so the ids behind the two tags are compared
    # now, not just the strings. The assertion is what the running container reports as its
    # image plus the API answering twice a few seconds apart, so "rolled back" cannot mean
    # "compose printed the old tag and left the new container in place" and cannot mean "the
    # container answered once and then exited". The second shape is what this step hit on
    # 2026-09-16 with a pre-ADR-0008 tag: Tomcat starts before CommandLineRunner finishes, so
    # /actuator/health answered 200, the old build's demo seeder then collided on username
    # `admin` with the row BootstrapAdminInitializer had already written, and the next request
    # was refused because the context had closed. A swap that lands on a container that dies is
    # not a rollback, which is why the target is a parameter rather than a leftover tag.
    $tagA = ''
    foreach ($line in (Get-Content '.env' -ErrorAction SilentlyContinue)) {
        if ($line -match '^APP_TAG=(.+)$') { $tagA = $Matches[1].Trim() }
    }
    if (-not $tagA) { throw 'no APP_TAG in .env, so there is no release tag to roll back to' }
    $tags = @((Invoke-Docker -Cmd @('images', 'nexus-vibe-app', '--format', '{{.Tag}}')) -split "`n" |
              ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -ne '<none>' })
    if ($RollbackTag) {
        $tagB = $RollbackTag
        if ($tags -notcontains $tagB) {
            throw "rollback target '$tagB' is not in the local image store (have: $($tags -join ', '))"
        }
    } else {
        # `docker images` lists newest first, so this is the most recent other build rather
        # than an accident of dictionary order.
        $tagB = "$(@($tags | Where-Object { $_ -ne $tagA } | Select-Object -First 1))"
        if (-not $tagB) {
            throw "only tag '$tagA' exists for nexus-vibe-app; build a second one or pass -RollbackTag ($($tags -join ', '))"
        }
    }
    if ($tagB -eq $tagA) { throw 'the rollback target is the release tag itself' }
    $ids = [ordered]@{}
    foreach ($t in @($tagA, $tagB)) {
        $ids[$t] = (Invoke-Docker -Cmd @('images', "nexus-vibe-app:$t", '--format', '{{.ID}}')).Trim()
    }
    Write-Evidence 'rollback tags' "A=$tagA ($($ids[$tagA])) B=$tagB ($($ids[$tagB])) present=$(($tags | Sort-Object) -join ', ')"
    if ($ids[$tagA] -eq $ids[$tagB]) {
        throw "$tagA and $tagB are the same image id ($($ids[$tagA])); the swap would prove tag interpolation, not rollback"
    }

    $seen = @()
    foreach ($tag in @($tagB, $tagA)) {
        $env:APP_TAG = $tag
        Invoke-Compose -Cmd @('up', '-d', '--no-build', '--no-deps', 'app') | Out-Null
        Wait-For "app answering after APP_TAG=$tag" {
            (Invoke-AppHttp GET '/actuator/health').Code -in @('200', '503')
        } -TimeoutSec $StartupTimeoutSec -IntervalSec 5
        # Config.Image is what the container was created from, not what the host now calls latest.
        $running = (Invoke-Docker -Cmd @('inspect', '-f', '{{.Config.Image}}', 'nexus-drill-app')).Trim()
        if ($running -ne "nexus-vibe-app:$tag") { throw "asked for $tag, the container reports $running" }
        # Two probes with a gap: the first one a runner-killed process can still win.
        $codes = @()
        foreach ($attempt in 1..2) {
            $answer = Invoke-ComposeTolerant -Cmd @('exec', '-T', 'app', 'curl', '-sS', '-o', '/dev/null',
                                                    '-w', '%{http_code}',
                                                    'http://localhost:8080/api/v1/posts?page=1&size=1')
            $codes += "$($answer.Code)/$($answer.Out.Trim())"
            if ($attempt -eq 1) { Start-Sleep -Seconds 12 }
        }
        $seen += "APP_TAG=$tag -> $running, /api/v1/posts exit/code = $($codes -join ' , ')"
        if (@($codes | Where-Object { $_ -ne '0/200' }).Count) {
            $tail = Invoke-ComposeTolerant -Cmd @('logs', '--tail', '12', 'app')
            Write-Evidence "rollback failure on $tag" $tail.Out
            throw "the post list did not answer twice on $tag (tries: $($codes -join ' , ')); " +
                  "the container's last log lines are in the evidence file"
        }
    }
    Write-Evidence 'rollback sequence' ($seen -join "`n")
    if ($seen.Count -ne 2) { throw 'the A-B-A sequence did not run twice' }
    return "$tagA ($($ids[$tagA])) -> $tagB ($($ids[$tagB])) -> $tagA by container image, two different ids, post list answering twice on both; $tagA last"
}

# --------------------------------------------------------------------------- report

# A filtered run did not start the stack, so it has nothing to tear down; the caller that started
# the project owns bringing it down. Without this, `-Only <file-only step>` would print a compose
# error over an otherwise green run and read as a failure of the thing under test.
if (-not $Keep -and (Test-StepSelected -Name 'stack-up')) {
    # Safe to purge: these are the drill project's own volumes, never a deployment's.
    Invoke-Compose -Cmd @('down', '-v') | Out-Null
}

$unknown = @($script:only | Where-Object { $_ -notin @($script:results | ForEach-Object { $_.Name }) })
if ($unknown) {
    # Not a warning: a typo or a renamed step would otherwise run zero of the assertions the
    # caller believes it asked for, and exit 0.
    Write-Host "no such step: $($unknown -join ', ')" -ForegroundColor Red
    exit 2
}

$failed = @($script:results | Where-Object { $_.Status -eq 'FAIL' })
$skipped = @($script:results | Where-Object { $_.Status -eq 'SKIP' })
$ran = @($script:results | Where-Object { $_.Status -ne 'SKIP' })
$lines = @('= Nexus-Vibe observability drill', "run: $stamp  web port: $WebPort", '')
$lines += @($script:results | ForEach-Object { "[$($_.Status)] $($_.Name)`n    $($_.Detail)" })
$tally = if ($skipped.Count) {
    "$($script:results.Count) steps, $($ran.Count) run ($($skipped.Count) skipped by -Only), $($failed.Count) failed"
} else {
    "$($script:results.Count) steps, $($failed.Count) failed"
}
$lines += @('', $tally, "evidence: $evidencePath")
$summary = $lines -join "`n"

Write-Host ''
Write-Host $summary
Set-Content -Path (Join-Path $evidenceDir "drill-$stamp-summary.md") -Value $summary

if ($failed) { exit 1 }
if ($Keep) {
    Write-Host 'stack left running: docker compose -p nexus-drill --profile monitoring --profile drill down -v' -ForegroundColor Yellow
}
