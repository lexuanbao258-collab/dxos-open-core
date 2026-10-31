# Read-only DX-OS OLP documentation evidence collector.
# Windows PowerShell 5.1+ / PowerShell 7; never sends credentials or writes to Backend.
param(
    [string]$DocumentationUrl = 'https://lexuanbao258-collab.github.io/dxos-open-core/',
    [string]$SwaggerUrl = 'http://127.0.0.1:8080/swagger-ui/index.html',
    [string]$OutputDir = (Join-Path (Get-Location).Path 'olp-docs-evidence')
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$OutputDir = (Resolve-Path $OutputDir).Path
$edgePaths = @(
    (Join-Path ([Environment]::GetFolderPath('ProgramFilesX86')) 'Microsoft\Edge\Application\msedge.exe'),
    (Join-Path $env:ProgramFiles 'Microsoft\Edge\Application\msedge.exe'),
    (Join-Path $env:ProgramFiles 'Google\Chrome\Application\chrome.exe')
)
$browser = $edgePaths | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $browser) { throw 'Microsoft Edge or Chrome not found. No screenshots were captured.' }

function Test-Http200([string]$Uri) {
    try {
        $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 20
        return ($response.StatusCode -eq 200)
    } catch {
        Write-Warning "Could not verify HTTP 200: $Uri ($($_.Exception.Message))"
        return $false
    }
}
function Save-BrowserEvidence([string]$Uri, [string]$Name, [int]$Width, [int]$Height) {
    $target = Join-Path $OutputDir $Name
    $profileDir = Join-Path $env:TEMP ("dxos-olp-docs-" + [guid]::NewGuid().ToString('N'))
    try {
        # Chromium often logs harmless renderer diagnostics to stderr.
        # Direct native invocation with $ErrorActionPreference='Stop' turns
        # those diagnostics into PowerShell errors on Windows PowerShell 5.1.
        # Start-Process captures stderr separately and checks the PNG itself.
        New-Item -ItemType Directory -Force -Path $profileDir | Out-Null
        $browserStderr = Join-Path $profileDir 'chromium-stderr.log'
        $browserStdout = Join-Path $profileDir 'chromium-stdout.log'
        $flags = @(
            '--headless=new',
            '--disable-gpu',
            '--hide-scrollbars',
            '--no-first-run',
            '--disable-extensions',
            ('--user-data-dir="' + $profileDir + '"'),
            "--window-size=$Width,$Height",
            '--virtual-time-budget=5000',
            ('--screenshot="' + $target + '"'),
            ('"' + $Uri + '"')
        )
        $process = Start-Process -FilePath $browser -ArgumentList $flags -Wait -PassThru -WindowStyle Hidden -RedirectStandardError $browserStderr -RedirectStandardOutput $browserStdout
        # Some Chrome/Edge builds report nonfatal renderer diagnostics even
        # when screenshot output is valid. The artifact is the source of truth.
        if (-not (Test-Path $target)) {
            throw "Screenshot was not created: $Name (browser exit $($process.ExitCode))"
        }
        if ((Get-Item $target).Length -lt 1024) { throw "Screenshot unexpectedly small: $Name" }
        Write-Host "SCREENSHOT=$target"
        return $true
    } catch {
        Write-Warning "Screenshot failed: $Name ($($_.Exception.Message))"
        return $false
    } finally {
        Remove-Item $profileDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
$manifest = [ordered]@{
    generatedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    documentationUrl = $DocumentationUrl
    documentationHttp200 = $false
    documentationDesktopScreenshot = $false
    documentationNarrowViewportScreenshot = $false
    swaggerUrl = $SwaggerUrl
    swaggerHttp200 = $false
    swaggerScreenshot = $false
    openApiGroupHttp200 = $false
    openApiExactNineOperations = $false
    openApiBearerAuthPresent = $false
    disclaimer = 'Local evidence only. Narrow viewport is not a real-device mobile test; screenshots do not establish auth/ACL E2E.'
}
$manifest.documentationHttp200 = Test-Http200 $DocumentationUrl
if ($manifest.documentationHttp200) {
    $manifest.documentationDesktopScreenshot = Save-BrowserEvidence $DocumentationUrl '01-documentation-desktop.png' 1440 1000
    $manifest.documentationNarrowViewportScreenshot = Save-BrowserEvidence $DocumentationUrl '02-documentation-narrow-viewport.png' 390 844
}
# Safety: only the local Swagger UI may be captured; no public bind or auth token.
if ($SwaggerUrl -notmatch '^http://(127\.0\.0\.1|localhost):[0-9]+/') {
    Write-Warning 'SwaggerUrl is not localhost/loopback; refusing to capture it.'
} else {
    $manifest.swaggerHttp200 = Test-Http200 $SwaggerUrl
    if ($manifest.swaggerHttp200) {
        # Safe, read-only inspection of the local group JSON. Count operations; do not log JWT or payload data.
        try {
            $openApiUri = ([uri]$SwaggerUrl).GetLeftPart([System.UriPartial]::Authority) + '/v3/api-docs/it-helpdesk-mvp'
            $spec = Invoke-RestMethod -Uri $openApiUri -TimeoutSec 20
            $manifest.openApiGroupHttp200 = $true
            $httpMethods = @('get', 'post', 'put', 'patch', 'delete', 'options', 'head')
            $count = 0
            foreach ($pathEntry in $spec.paths.PSObject.Properties) {
                foreach ($verb in $pathEntry.Value.PSObject.Properties) {
                    if ($httpMethods -contains $verb.Name.ToLowerInvariant()) { $count++ }
                }
            }
            $manifest.openApiExactNineOperations = ($count -eq 9)
            $manifest.openApiBearerAuthPresent = ($null -ne $spec.components.securitySchemes.bearerAuth)
            Write-Host "OPENAPI_OPERATIONS=$count; BEARER_AUTH_PRESENT=$($manifest.openApiBearerAuthPresent)"
        } catch {
            Write-Warning "Runtime OpenAPI group could not be parsed: $($_.Exception.Message)"
        }

        $manifest.swaggerScreenshot = Save-BrowserEvidence $SwaggerUrl '03-swagger-local-no-auth.png' 1440 1050
    }
}
$manifestFile = Join-Path $OutputDir 'manifest.json'
$manifest | ConvertTo-Json -Depth 5 | Set-Content -Path $manifestFile -Encoding UTF8
Write-Host "MANIFEST=$manifestFile"
Write-Host 'Do not upload images until you have inspected them for identifying data, tokens or secrets.'
if (-not $manifest.documentationHttp200) { Write-Warning 'PUBLIC_DEPLOYMENT_NOT_VERIFIED. Check GitHub Settings > Pages.' }
if (-not $manifest.swaggerHttp200) { Write-Warning 'LOCAL_SWAGGER_NOT_VERIFIED. Start opt-in loopback Swagger before collecting evidence.' }
