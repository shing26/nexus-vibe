<#
.SYNOPSIS
  Fires N simultaneous likes at one post and checks that the three views of that like agree.

.DESCRIPTION
  "N threads, no lost update" is a claim about a race, so it has to be run as one. The script
  builds its own fixtures - one probe post and N probe users - then releases N likes at the same
  instant through a barrier, and compares:

    * HTTP: every request answered 200 (a 429 means the limiter, not the like path)
    * Redis: SCARD post:like:<id>          (the low-latency source of truth)
    * MySQL: COUNT(*) FROM vibe_post_like  (the durable membership mirror)
    * MySQL: vibe_post.like_count          (the denormalised counter)

  The first two have to agree the moment the burst returns: persistMembership() mirrors every
  successful Lua toggle. like_count does not - it is written by LikeSyncTask on a five-minute
  cron, so the script polls for it and reports how long convergence took rather than pretending
  the counter is synchronous. That gap is the finding, not a flaw in the probe.

  Then it does the same in reverse (concurrent unlikes) and asserts the post is back at zero, so a
  "toggle" that only ever adds cannot pass.

  Fixtures are created with SQL and the tokens are minted locally, because /api/v1/auth/register
  is capped at 10 requests per minute per address and 50 registrations would spend five minutes
  proving the limiter works. Everything the script creates, it deletes.

.EXAMPLE
  pwsh -File benchmark/concurrency/like-concurrency.ps1

.EXAMPLE
  # Point it at an already-running stack and keep the fixtures for inspection.
  pwsh -File benchmark/concurrency/like-concurrency.ps1 -SkipStackCheck -SkipCleanup
#>
[CmdletBinding()]
param(
    [string] $BaseUrl = 'http://localhost:18081',
    [int]    $Threads = 50,
    [int]    $FlushTimeoutSeconds = 330,

    [string] $ComposeProject = 'nexus-conc',
    [string] $DbContainer = 'nexus-conc-db',
    [string] $RedisContainer = 'nexus-conc-redis',
    [string] $EnvFile = (Join-Path $PSScriptRoot '..\..\.env'),
    [string] $OutDir = (Join-Path $PSScriptRoot 'results'),

    [switch] $SkipStackCheck,
    [switch] $SkipCleanup
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Write-Step { param([string] $Message) Write-Host "==> $Message" -ForegroundColor Cyan }
function Write-Fail { param([string] $Message) Write-Host "FAIL: $Message" -ForegroundColor Red }

$failures = New-Object System.Collections.Generic.List[string]
function Assert-Equal {
    param([string] $What, $Expected, $Actual)
    if ("$Expected" -ne "$Actual") {
        $failures.Add("$What expected $Expected, observed $Actual")
        Write-Fail "$What expected $Expected, observed $Actual"
    } else {
        Write-Host "    OK  $What = $Actual"
    }
}

# ---------------------------------------------------------------- helpers

function Invoke-Sql {
    param([string] $Sql)
    $out = & docker exec $DbContainer sh -c "MYSQL_PWD=`$MYSQL_ROOT_PASSWORD mysql -N -s -h 127.0.0.1 -uroot nexus_campus -e `"$Sql`""
    if ($LASTEXITCODE -ne 0) { throw "SQL failed ($LASTEXITCODE): $Sql" }
    return $out
}

function Get-SqlScalar {
    param([string] $Sql)
    $out = @(Invoke-Sql -Sql $Sql)
    if ($out.Count -eq 0) { return $null }
    return $out[0]
}

function Get-RedisScalar {
    param([string[]] $Arguments)
    $out = & docker exec $RedisContainer redis-cli @Arguments
    if ($LASTEXITCODE -ne 0) { throw "redis-cli failed ($LASTEXITCODE): $($Arguments -join ' ')" }
    return ($out | Select-Object -First 1)
}

function ConvertTo-Base64Url {
    param([byte[]] $Bytes)
    return ([Convert]::ToBase64String($Bytes)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-ProbeJwt {
    param([long] $UserId, [string] $Username, [string] $Role, [byte[]] $Key)
    $header = '{"alg":"HS256","typ":"JWT"}'
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $payload = [ordered]@{
        sub      = "$UserId"
        username = $Username
        role     = $Role
        iat      = $now
        exp      = $now + 7200
    } | ConvertTo-Json -Compress

    $segments = (ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($header))) + '.' +
                (ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payload)))

    $hmac = [System.Security.Cryptography.HMACSHA256]::new($Key)
    try {
        $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($segments)))
    } finally {
        $hmac.Dispose()
    }
    return "$segments.$signature"
}

function Get-Percentile {
    param([double[]] $Sorted, [double] $Percent)
    if (-not $Sorted -or $Sorted.Count -eq 0) { return $null }
    return [int] $Sorted[[int] [Math]::Floor(($Percent / 100.0) * ($Sorted.Count - 1))]
}

# Fires one POST per token, all released together. The barrier is the point: without it this is a
# loop with extra steps, and a lost update needs simultaneous writers to show up at all.
function Invoke-LikeBurst {
    param([string] $Url, [string[]] $Tokens)

    $count = $Tokens.Count
    $barrier = [System.Threading.Barrier]::new($count)
    try {
        return 1..$count | ForEach-Object -Parallel {
            $index = $_
            $sharedBarrier = $using:barrier
            # $using: only accepts a bare variable name, so the array is bound first and indexed here.
            $tokenList = $using:Tokens
            $token = $tokenList[$index - 1]
            $uri = $using:Url
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            try {
                $sharedBarrier.SignalAndWait()
                $response = Invoke-WebRequest -Method POST -Uri $uri -TimeoutSec 60 -SkipHttpErrorCheck `
                    -Headers @{ Authorization = "Bearer $token"; 'X-Real-IP' = "10.99.$index.1" }
                [pscustomobject]@{ index = $index; status = [int] $response.StatusCode; ms = $sw.ElapsedMilliseconds }
            } catch {
                [pscustomobject]@{ index = $index; status = 0; ms = $sw.ElapsedMilliseconds; error = $_.Exception.Message }
            } finally {
                $sw.Stop()
            }
        } -ThrottleLimit $count
    } finally {
        $barrier.Dispose()
    }
}

function Wait-ForLikeCount {
    param([long] $PostId, [int] $Expected, [int] $TimeoutSeconds)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $waited = 0
    while ((Get-Date) -lt $deadline) {
        $current = [int] (Get-SqlScalar -Sql "SELECT like_count FROM vibe_post WHERE id=$PostId")
        if ($current -eq $Expected) { return [ordered]@{ converged = $true; seconds = $waited; value = $current } }
        Start-Sleep -Seconds 5
        $waited += 5
        Write-Host "    ... like_count=$current, waiting for LikeSyncTask (${waited}s)" -ForegroundColor DarkYellow
    }
    return [ordered]@{ converged = $false; seconds = $waited; value = [int] (Get-SqlScalar -Sql "SELECT like_count FROM vibe_post WHERE id=$PostId") }
}

# ---------------------------------------------------------------- environment

if (-not (Test-Path -LiteralPath $EnvFile)) { throw "Cannot read $EnvFile for JWT_SECRET." }
$envMap = @{}
Get-Content -LiteralPath $EnvFile | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
    $split = $_.IndexOf('=')
    $envMap[$_.Substring(0, $split)] = $_.Substring($split + 1)
}
if (-not $envMap.ContainsKey('JWT_SECRET')) { throw "JWT_SECRET is not set in $EnvFile." }
$jwtKey = [Convert]::FromBase64String($envMap['JWT_SECRET'])

Write-Step "Target: $BaseUrl"
Write-Step "Threads: $Threads"

if (-not $SkipStackCheck) {
    $health = $null
    try { $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 15 } catch { }
    if (-not $health) {
        throw "No answer from $BaseUrl/actuator/health. Bring up the scratch stack first (see docker-compose.concurrency.yml)."
    }
    Write-Host "    health=$($health.status)"
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$date = Get-Date -Format 'yyyyMMdd'
$probePostId = 900000000000000900
$userIdBase = 900000000000100000
$likeUrl = "$BaseUrl/api/v1/posts/$probePostId/like"
$redisSetKey = "post:like:$probePostId"

$startedAt = (Get-Date).ToUniversalTime()
$baseline = $null
$afterLike = $null
$afterUnlike = $null
$likeBurst = @()
$unlikeBurst = @()
$likeConvergence = $null
$unlikeConvergence = $null

try {
    # ------------------------------------------------------------ fixtures
    Write-Step 'Creating the probe fixtures (one post, N users)'
    Invoke-Sql -Sql "DELETE FROM vibe_post_like WHERE post_id=$probePostId" | Out-Null
    Invoke-Sql -Sql "DELETE FROM vibe_post WHERE id=$probePostId" | Out-Null
    Invoke-Sql -Sql "DELETE FROM sys_user WHERE id BETWEEN $userIdBase AND $($userIdBase + $Threads - 1)" | Out-Null

    Invoke-Sql -Sql ("INSERT INTO vibe_post (id, title, content, user_id, category_id, status, like_count, comment_count, view_count, is_pinned, post_type, create_time) " +
        "VALUES ($probePostId, 'like-concurrency probe', 'Probe post created and deleted by benchmark/concurrency/like-concurrency.ps1.', 1, 6, 1, 0, 0, 0, 0, 'post', NOW())") | Out-Null

    $userValues = @()
    for ($i = 1; $i -le $Threads; $i++) {
        $id = $userIdBase + $i - 1
        $userValues += "($id, 'probe_like_${stamp}_$i', 'PROBE_NO_LOGIN', 'Probe $i', 'default_avatar.png', 'USER', 0, 1, 1, NOW(), NOW())"
    }
    Invoke-Sql -Sql ("INSERT INTO sys_user (id, username, password, nickname, avatar, role, core_power, level, status, create_time, update_time) VALUES " + ($userValues -join ', ')) | Out-Null

    $tokens = @()
    for ($i = 1; $i -le $Threads; $i++) {
        $tokens += New-ProbeJwt -UserId ($userIdBase + $i - 1) -Username "probe_like_${stamp}_$i" -Role 'USER' -Key $jwtKey
    }
    Write-Host "    created $Threads users and post $probePostId"

    $baseline = [ordered]@{
        redisScard = [int] (Get-RedisScalar -Arguments @('SCARD', $redisSetKey))
        membershipRows = [int] (Get-SqlScalar -Sql "SELECT COUNT(*) FROM vibe_post_like WHERE post_id=$probePostId")
        likeCount = [int] (Get-SqlScalar -Sql "SELECT like_count FROM vibe_post WHERE id=$probePostId")
    }
    Write-Host "    baseline: SCARD=$($baseline.redisScard) rows=$($baseline.membershipRows) like_count=$($baseline.likeCount)"

    # ------------------------------------------------------------ like burst
    Write-Step "Releasing $Threads simultaneous likes"
    $likeBurst = @(Invoke-LikeBurst -Url $likeUrl -Tokens $tokens)
    $likeStatuses = $likeBurst | Group-Object status | ForEach-Object { "$($_.Name)x$($_.Count)" }
    Write-Host "    statuses: $($likeStatuses -join ' ')"

    $afterLike = [ordered]@{
        redisScard = [int] (Get-RedisScalar -Arguments @('SCARD', $redisSetKey))
        membershipRows = [int] (Get-SqlScalar -Sql "SELECT COUNT(*) FROM vibe_post_like WHERE post_id=$probePostId")
    }
    Write-Host "    after likes: SCARD=$($afterLike.redisScard) rows=$($afterLike.membershipRows)"

    $httpFailures = @($likeBurst | Where-Object { $_.status -ne 200 })
    Assert-Equal 'HTTP 200 responses' $Threads ($Threads - $httpFailures.Count)
    Assert-Equal 'Redis SCARD after like burst' $Threads $afterLike.redisScard
    Assert-Equal 'vibe_post_like rows after like burst' $Threads $afterLike.membershipRows

    Write-Step "Waiting for LikeSyncTask to flush like_count (up to ${FlushTimeoutSeconds}s)"
    $likeConvergence = Wait-ForLikeCount -PostId $probePostId -Expected $Threads -TimeoutSeconds $FlushTimeoutSeconds
    Assert-Equal 'vibe_post.like_count after flush' $Threads $likeConvergence.value

    # ------------------------------------------------------------ unlike burst
    Write-Step "Releasing $Threads simultaneous unlikes"
    $unlikeBurst = @(Invoke-LikeBurst -Url $likeUrl -Tokens $tokens)
    $unlikeStatuses = $unlikeBurst | Group-Object status | ForEach-Object { "$($_.Name)x$($_.Count)" }
    Write-Host "    statuses: $($unlikeStatuses -join ' ')"

    $afterUnlike = [ordered]@{
        redisScard = [int] (Get-RedisScalar -Arguments @('SCARD', $redisSetKey))
        membershipRows = [int] (Get-SqlScalar -Sql "SELECT COUNT(*) FROM vibe_post_like WHERE post_id=$probePostId")
    }
    Write-Host "    after unlikes: SCARD=$($afterUnlike.redisScard) rows=$($afterUnlike.membershipRows)"

    $unlikeHttpFailures = @($unlikeBurst | Where-Object { $_.status -ne 200 })
    Assert-Equal 'HTTP 200 responses on unlike' $Threads ($Threads - $unlikeHttpFailures.Count)
    Assert-Equal 'Redis SCARD after unlike burst' 0 $afterUnlike.redisScard
    Assert-Equal 'vibe_post_like rows after unlike burst' 0 $afterUnlike.membershipRows

    Write-Step "Waiting for LikeSyncTask to flush like_count back to zero"
    $unlikeConvergence = Wait-ForLikeCount -PostId $probePostId -Expected 0 -TimeoutSeconds $FlushTimeoutSeconds
    Assert-Equal 'vibe_post.like_count back at zero' 0 $unlikeConvergence.value
}
finally {
    if (-not $SkipCleanup) {
        Write-Step 'Cleaning up the probe fixtures'
        try {
            Invoke-Sql -Sql "DELETE FROM vibe_post_like WHERE post_id=$probePostId" | Out-Null
            Invoke-Sql -Sql "DELETE FROM vibe_post WHERE id=$probePostId" | Out-Null
            Invoke-Sql -Sql "DELETE FROM sys_user WHERE id BETWEEN $userIdBase AND $($userIdBase + $Threads - 1)" | Out-Null
            Get-RedisScalar -Arguments @('DEL', $redisSetKey) | Out-Null
            Get-RedisScalar -Arguments @('SREM', 'post:like:dirty', "$probePostId") | Out-Null
            Get-RedisScalar -Arguments @('ZREM', 'post:ranking:likes', "$probePostId") | Out-Null
            $leftover = Get-SqlScalar -Sql "SELECT COUNT(*) FROM vibe_post WHERE id=$probePostId"
            Write-Host "    leftover probe posts: $leftover"
        } catch {
            Write-Fail "cleanup did not finish: $($_.Exception.Message)"
        }
    }
}

# ---------------------------------------------------------------- summary

$likeLatencies = @($likeBurst | ForEach-Object { [double] $_.ms } | Sort-Object)
$unlikeLatencies = @($unlikeBurst | ForEach-Object { [double] $_.ms } | Sort-Object)

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$jsonPath = Join-Path $OutDir "like-concurrency-$date.json"
$mdPath = Join-Path $OutDir "like-concurrency-$date.md"

$summary = [ordered]@{
    date = $date
    startedAtUtc = $startedAt.ToString('o')
    target = $BaseUrl
    threads = $Threads
    probePostId = $probePostId
    baseline = $baseline
    afterLikeBurst = $afterLike
    afterUnlikeBurst = $afterUnlike
    likeBurst = [ordered]@{
        http200 = @($likeBurst | Where-Object { $_.status -eq 200 }).Count
        statuses = @($likeBurst | Group-Object status | ForEach-Object { [ordered]@{ status = [int] $_.Name; count = $_.Count } })
        latencyMs = [ordered]@{
            p50 = Get-Percentile -Sorted $likeLatencies -Percent 50
            p99 = Get-Percentile -Sorted $likeLatencies -Percent 99
        }
    }
    unlikeBurst = [ordered]@{
        http200 = @($unlikeBurst | Where-Object { $_.status -eq 200 }).Count
        statuses = @($unlikeBurst | Group-Object status | ForEach-Object { [ordered]@{ status = [int] $_.Name; count = $_.Count } })
        latencyMs = [ordered]@{
            p50 = Get-Percentile -Sorted $unlikeLatencies -Percent 50
            p99 = Get-Percentile -Sorted $unlikeLatencies -Percent 99
        }
    }
    likeCountConvergence = $likeConvergence
    unlikeCountConvergence = $unlikeConvergence
    failures = @($failures)
}

$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$md = New-Object System.Text.StringBuilder
[void] $md.AppendLine("# Like concurrency summary ($date)")
[void] $md.AppendLine()
[void] $md.AppendLine("Produced by ``pwsh -File benchmark/concurrency/like-concurrency.ps1`` against ``$BaseUrl``, $Threads threads released through a shared barrier.")
[void] $md.AppendLine()
[void] $md.AppendLine('| Check | Expected | Observed |')
[void] $md.AppendLine('| --- | --- | --- |')
[void] $md.AppendLine("| HTTP 200 on like burst | $Threads | $(@($likeBurst | Where-Object { $_.status -eq 200 }).Count) |")
[void] $md.AppendLine("| Redis SCARD after like burst | $Threads | $($afterLike.redisScard) |")
[void] $md.AppendLine("| vibe_post_like rows after like burst | $Threads | $($afterLike.membershipRows) |")
[void] $md.AppendLine("| vibe_post.like_count after LikeSyncTask | $Threads | $($likeConvergence.value) |")
[void] $md.AppendLine("| HTTP 200 on unlike burst | $Threads | $(@($unlikeBurst | Where-Object { $_.status -eq 200 }).Count) |")
[void] $md.AppendLine("| Redis SCARD after unlike burst | 0 | $($afterUnlike.redisScard) |")
[void] $md.AppendLine("| vibe_post_like rows after unlike burst | 0 | $($afterUnlike.membershipRows) |")
[void] $md.AppendLine("| vibe_post.like_count back at zero | 0 | $($unlikeConvergence.value) |")
[void] $md.AppendLine()
[void] $md.AppendLine("Latency: like p50=$($summary.likeBurst.latencyMs.p50)ms p99=$($summary.likeBurst.latencyMs.p99)ms; unlike p50=$($summary.unlikeBurst.latencyMs.p50)ms p99=$($summary.unlikeBurst.latencyMs.p99)ms.")
[void] $md.AppendLine()
[void] $md.AppendLine('## What this run does not show')
[void] $md.AppendLine()
[void] $md.AppendLine("- ``like_count`` is written by LikeSyncTask, not by the request path, so the value above is the state after the five-minute cron, not at response time. This run waited $($likeConvergence.seconds)s for the like flush and $($unlikeConvergence.seconds)s for the unlike flush.")
[void] $md.AppendLine('- It runs against a scratch stack (benchmark/concurrency/docker-compose.concurrency.yml), not the live deployment. The limiter is active there; the script sends one X-Real-IP per probe user so 50 simultaneous likes are 50 buckets rather than one.')
[void] $md.AppendLine('- It proves the three views agree for one post. It does not sweep for drift; that is DriftReconcileTask''s job and is covered by its own tests.')
[void] $md.AppendLine()
# The unlike leg is the interesting one, but the prose has to follow the outcome: the 2026-09-21
# run failed here, the 2026-09-23 fix (docs/tickets/like-count-convergence.md) made it converge,
# and a run that fails now is a regression rather than the known defect.
[void] $md.AppendLine('## The unlike leg')
[void] $md.AppendLine()
if ($unlikeConvergence.converged) {
    [void] $md.AppendLine('The unlike leg converges. The durable membership table is the authority, so an empty Redis set plus an empty table writes zero instead of preserving a stale count.')
} else {
    [void] $md.AppendLine("The unlike leg did **not** converge: ``like_count`` is still $($unlikeConvergence.value) after the flush. That is a regression against the 2026-09-23 fix, which made an empty Redis set plus an empty membership table write zero. The defect and its fix are recorded in docs/tickets/like-count-convergence.md.")
}
if ($failures.Count -gt 0) {
    [void] $md.AppendLine()
    [void] $md.AppendLine('## Failures')
    [void] $md.AppendLine()
    foreach ($failure in $failures) { [void] $md.AppendLine("- $failure") }
}

Set-Content -LiteralPath $mdPath -Value $md.ToString() -Encoding utf8
Write-Step "Wrote $jsonPath"
Write-Step "Wrote $mdPath"
Get-Content -LiteralPath $mdPath

if ($failures.Count -gt 0) { exit 1 }
exit 0
