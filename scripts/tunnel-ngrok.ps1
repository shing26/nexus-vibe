<#
.SYNOPSIS
    Serve the local stack on the reserved ngrok dev domain.

.DESCRIPTION
    The public demo lives on an ngrok free-tier domain. ngrok shows a one-time
    "Visit Site" warning page to browsers that have never seen the domain; after
    one click a cookie suppresses it for that visitor for seven days. API clients
    are unaffected.

    README.md publishes the address, and the backend only answers requests whose
    Origin is on campus.cors.allowed-origins. So if the domain ever changes, two
    things must change with it: CORS_ALLOWED_ORIGINS in .env, and the README.

.PARAMETER Domain
    The reserved ngrok dev domain, without scheme.

.PARAMETER Port
    Local port the nginx container publishes. Defaults to 8080.

.EXAMPLE
    pwsh scripts/tunnel-ngrok.ps1
#>
[CmdletBinding()]
param(
    [string]$Domain = "qualifier-discuss-marry.ngrok-free.dev",
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command ngrok -ErrorAction SilentlyContinue)) {
    throw "ngrok is not on PATH. Install it (choco install ngrok) and run 'ngrok config add-authtoken <token>' first."
}

$running = Get-CimInstance Win32_Process -Filter "Name='ngrok.exe'" |
    Where-Object { $_.CommandLine -like "*--url*$Domain*" -or $_.CommandLine -like "*--domain*$Domain*" }
if ($running) {
    Write-Host "ngrok is already serving $Domain (pid $($running.ProcessId -join ', '))."
    return
}

Write-Host "Serving http://localhost:$Port on https://$Domain"
ngrok http $Port --url $Domain
