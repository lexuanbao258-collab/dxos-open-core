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
# IMPORTANT: PowerShell 5.1 Invoke-WebRequest.Content may decode missing-charset
# application/json as Latin-1 and silently corrupt Vietnamese text. Always read
# response bytes and decode strict UTF-8 independent of HTTP Content-Type charset.
Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(20)
try {
    $response = $client.GetAsync($endpoint, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    try {
        if ([int]$response.StatusCode -ne 200) {
            throw "OpenAPI returned HTTP $([int]$response.StatusCode)"
        }
        $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    } finally {
        $response.Dispose()
    }
} catch {
    throw "OpenAPI endpoint not readable locally: $($_.Exception.GetType().Name). Verify loopback Backend and docs opt-in."
} finally {
    $client.Dispose()
}
$strictUtf8 = [System.Text.UTF8Encoding]::new($false, $true)
try {
    $rawJson = $strictUtf8.GetString($bytes)
} catch [System.Text.DecoderFallbackException] {
    throw 'OpenAPI response is not valid UTF-8; refusing to export broken text.'
}
if ($rawJson.Contains([char]0xFFFD)) {
    throw 'OpenAPI contains Unicode replacement character; refusing to publish suspicious text.'
}
$data = $rawJson | ConvertFrom-Json
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
