# Local, read-only verification of the opt-in DX-OS Swagger/OpenAPI surface.
# No credentials, no writes, no fake OIDC tokens, no tests against public hosts.
# Prerequisite: Backend with DXOS_OPENAPI_ENABLED=true, DXOS_HELPDESK_ENABLED=true
# and SERVER_ADDRESS=127.0.0.1, a disposable PostgreSQL and real Keycloak issuer.
[CmdletBinding()]
param(
    [ValidateRange(1,65535)]
    [int]$Port = 8080
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$base = "http://127.0.0.1:$Port"
$specUrl = "$base/v3/api-docs/it-helpdesk-mvp"

function Assert([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAIL: $Message" }
}

function HttpStatus([string]$Url, [string]$Method='GET', [string]$Authorization='') {
    $arguments = @('-sS','--connect-timeout','4','--max-time','12','-o','NUL','-w','%{http_code}','-X',$Method)
    if ($Authorization) { $arguments += @('-H',"Authorization: $Authorization") }
    $arguments += $Url
    $value = (& curl.exe @arguments | Out-String).Trim()
    Assert ($LASTEXITCODE -eq 0) "curl transport error at $Url"
    Assert ($value -match '^[0-9]{3}$') "Unexpected HTTP status: $value"
    return [int]$value
}

Write-Host "BASE=$base (loopback only)"
Assert ((HttpStatus $specUrl) -eq 200) 'Grouped OpenAPI JSON not readable locally'
Assert ((HttpStatus "$base/swagger-ui/index.html") -eq 200) 'Swagger UI not readable locally'
$spec = Invoke-RestMethod -Method Get -Uri $specUrl -TimeoutSec 15
Assert (-not [string]::IsNullOrEmpty($spec.openapi)) 'Missing openapi version'
Assert ($null -ne $spec.paths) 'Missing paths'
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
$actual = @()
foreach ($property in $spec.paths.PSObject.Properties) {
    foreach ($verb in @('get','post','put','patch','delete','head','options')) {
        if ($property.Value.PSObject.Properties.Name -contains $verb) {
            $actual += "$verb $($property.Name)"
        }
    }
}
$missing = @($expected | Where-Object { $actual -notcontains $_ })
$extra = @($actual | Where-Object { $expected -notcontains $_ })
Assert ($missing.Count -eq 0 -and $extra.Count -eq 0) "Endpoint mismatch. Missing: $($missing -join '; '); extra: $($extra -join '; ')"
Assert ($actual.Count -eq 9) "Expected 9 operations; actual=$($actual.Count)"
$scheme = $spec.components.securitySchemes.bearerAuth
Assert ($null -ne $scheme) 'Missing bearerAuth scheme'
Assert ($scheme.type -eq 'http' -and $scheme.scheme -eq 'bearer') 'bearerAuth must be HTTP bearer'
foreach ($operation in $expected) {
    $parts = $operation.Split(' ',2)
    $op = $spec.paths.($parts[1]).($parts[0])
    $combined = if ($null -ne $op.security) { @($op.security) } else { @($spec.security) }
    Assert (@($combined | Where-Object { $_.PSObject.Properties.Name -contains 'bearerAuth' }).Count -gt 0) "No bearerAuth security on $operation"
}
Assert ($null -ne $spec.paths.'/api/v1/mvp/requests'.post.responses.'201') 'Create endpoint missing 201 response'
Write-Host 'OPENAPI_CONTRACT=PASS (9 exact routes, bearerAuth, create 201)'
Assert ((HttpStatus "$base/api/v1/mvp/requests") -eq 401) 'GET without JWT must return 401'
Assert ((HttpStatus "$base/api/v1/mvp/requests" 'POST') -eq 401) 'POST without JWT must return 401'
Assert ((HttpStatus "$base/api/v1/mvp/requests" 'GET' 'Bearer deliberately.invalid.token') -eq 401) 'GET with invalid JWT must return 401'
Write-Host 'HTTP_NO_TOKEN_AND_INVALID_JWT=PASS (GET 401, POST 401, malformed token 401)'
Write-Host 'NOT_TESTED: real JWT, 403/404/409, tenant and resource ACL, disabled-by-default HTTP, non-loopback startup, Keycloak E2E'
