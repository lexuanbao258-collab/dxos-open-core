# Safely export the *actual* generated Helpdesk OpenAPI document from a loopback local Backend.
# Read-only GET. No credential capture, no public HTTP calls or uploads.
# Execute only with Backend and Keycloak already running locally.
[CmdletBinding()]
param(
    [ValidateRange(1,65535)][int]$Port = 8080,
    [string]$Output = '',
    [switch]$Overwrite
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Bound to this repository; no network other than loopback 127.0.0.1.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($Output)) {
    $Output = Join-Path $repoRoot 'backend/build/openapi-review/it-helpdesk.runtime.local.json'
}
$out = [System.IO.Path]::GetFullPath($Output)
if ((Test-Path -LiteralPath $out) -and -not $Overwrite) {
    throw "Output already exists; use -Overwrite only after reviewing the existing file: $out"
}
$endpoint = "http://127.0.0.1:$Port/v3/api-docs/it-helpdesk-mvp"
Write-Host "SOURCE=$endpoint (loopback GET; no token)"
try {
    $res = Invoke-WebRequest -Method Get -Uri $endpoint -UseBasicParsing -TimeoutSec 20
} catch {
    throw 'OpenAPI endpoint not readable (confirm local Backend, OIDC, and opt-in docs settings).'
}
if ([int]$res.StatusCode -ne 200) { throw "Expected HTTP 200, got $($res.StatusCode)" }
$data = $res.Content | ConvertFrom-Json
if (-not $data.openapi -or -not $data.paths) { throw 'Not an OpenAPI JSON object' }
$operations = @()
foreach ($p in $data.paths.PSObject.Properties) {
    foreach ($verb in @('get','post','put','patch','delete','head','options')) {
        if ($p.Value.PSObject.Properties.Name -contains $verb) {
            $operations += "$verb $($p.Name)"
        }
    }
}
$expected = @(
  'get /api/v1/mvp/requests',
  'post /api/v1/mvp/requests',
  'get /api/v1/mvp/requests/{id}',
  'get /api/v1/mvp/requests/{id}/history',
  'post /api/v1/mvp/requests/{id}/triage',
  'post /api/v1/mvp/requests/{id}/assign',
  'post /api/v1/mvp/requests/{id}/start',
  'post /api/v1/mvp/requests/{id}/resolve',
  'post /api/v1/mvp/requests/{id}/confirm'
)
if (@($operations | Where-Object { $expected -notcontains $_ }).Count -ne 0 -or
    @($expected | Where-Object { $operations -notcontains $_ }).Count -ne 0 -or
    $operations.Count -ne 9) {
    throw 'Contract changed: expected precisely nine local Helpdesk routes. No export saved.'
}
# Local Swagger may include transient server information. Do not publish internal addresses.
if ($data.PSObject.Properties.Name -contains 'servers') {
    $data.PSObject.Properties.Remove('servers')
}
$json = $data | ConvertTo-Json -Depth 100
# Fail closed if accidentally populated with a real JWT access token.
if ($json -match '(?i)Bearer\s+eyJ[A-Za-z0-9_\-]+\.' -or
    $json -match '(?i)"(password|client_secret|access_token)"\s*:\s*"[^"]{12,}"') {
    throw 'Export appears to contain credentials; refusing to write file.'
}
$directory = Split-Path -Parent $out
if (-not (Test-Path -LiteralPath $directory)) {
    $null = New-Item -ItemType Directory -Path $directory -Force
}
[System.IO.File]::WriteAllText($out, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
Write-Host "RUNTIME_OPENAPI_EXPORT=PASS OPERATIONS=$($operations.Count)"
Write-Host "OUTPUT=$out"
Write-Host "REVIEW_REQUIRED=YES (do NOT commit/publish this file before security and schema review)"
