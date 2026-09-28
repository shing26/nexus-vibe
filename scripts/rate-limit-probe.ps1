<#
.SYNOPSIS
    Rotates a client-supplied IP header across N API calls and prints the status histogram.

.DESCRIPTION
    The pre-deployment checklist records that rotating CF-Connecting-IP still gets rate
    limited after nginx stopped trusting that header. A number in this repository is
    supposed to name the command behind it, so this is that command.

    It targets the published web port, which is nginx, not the app. Going straight to the
    app would skip the layer the claim is about: nginx overwrites X-Real-IP with
    $remote_addr and drops CF-Connecting-IP, so the limiter keys on the socket address no
    matter what the caller sends. Hitting the app directly would still rotate the key,
    because application-prod.yml trusts X-Real-IP from the proxy it is told to sit behind.

    The login bucket is 10 requests per 60s, so this probe deliberately spends it. Run it
    no more often than once a minute, otherwise the histogram is measuring the previous
    run on top of this one.

.EXAMPLE
    pwsh -File scripts/rate-limit-probe.ps1
    401 x 10
    429 x 5

.EXAMPLE
    # Rotate the header the app does trust, to see the opposite result. This only shows
    # anything when the probe is pointed at nginx with TRUST_FORWARDED_HEADERS=false.
    pwsh -File scripts/rate-limit-probe.ps1 -HeaderName X-Real-IP
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Path = '/api/v1/auth/login',
    [string]$HeaderName = 'CF-Connecting-IP',
    [int]$Count = 15,
    [string]$JsonBody = '{"username":"rate-limit-probe","password":"not-a-real-password"}'
)

$ErrorActionPreference = 'Stop'

$statuses = for ($i = 1; $i -le $Count; $i++) {
    $response = Invoke-WebRequest -Uri ($BaseUrl.TrimEnd('/') + $Path) `
        -Method Post `
        -ContentType 'application/json' `
        -Body $JsonBody `
        -Headers @{ $HeaderName = "203.0.113.$i" } `
        -SkipHttpErrorCheck
    $response.StatusCode
}

Write-Output "$HeaderName rotated $Count times against $BaseUrl$Path"
$statuses | Group-Object | Sort-Object Name | ForEach-Object {
    Write-Output ('{0} x {1}' -f $_.Name, $_.Count)
}
