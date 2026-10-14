param(
    [switch]$ExecuteApprovedBootstrap,
    [string]$CaCertificate = $env:PGSSLROOTCERT,
    [string]$JavaHome = 'C:\Program Files\Java\jdk-21.0.11'
)
$ErrorActionPreference = 'Stop'
$bootstrapExit = 1
$reported = $false
$phase = 'launcher_validation'
$keys = @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','PGSSLROOTCERT',
    'WORKFLOW_SCHEMA_BOOTSTRAP_APPROVAL','JAVA_HOME','Path')
$saved = @{}
foreach ($key in $keys) { $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$pushed = $false
try {
    if (-not $ExecuteApprovedBootstrap) { throw 'Explicit approved invocation required' }
    $expected = @{DB_HOST='db.zsfctpnitkdztlfhzqgw.supabase.co'; DB_PORT='5432'; DB_NAME='postgres'; DB_USER='postgres'}
    foreach ($key in $expected.Keys) {
        $existing = [Environment]::GetEnvironmentVariable($key, 'Process')
        if ($existing -and $existing -ne $expected[$key]) { throw 'Endpoint configuration mismatch' }
    }
    foreach ($key in @('WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE','WORKFLOW_DEV_LOCAL_DISPOSABLE','WF25_DISPOSABLE_TEST')) {
        if ($null -ne [Environment]::GetEnvironmentVariable($key, 'Process')) { throw 'Local profile rejected' }
    }
    if (-not $CaCertificate -or -not (Test-Path -LiteralPath $CaCertificate -PathType Leaf)) { throw 'Trusted CA missing' }
    if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe') -PathType Leaf)) { throw 'JDK 21 missing' }
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;$env:Path"
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $version = & java.exe -version 2>&1
    } finally { $ErrorActionPreference = $previousPreference }
    if (($version -join ' ') -notmatch 'version "21\.') { throw 'JDK 21 required' }
    $phase = 'secure_password_input'
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
        $securePassword = Read-Host 'Supabase postgres password (hidden)' -AsSecureString
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('postgres', $securePassword).Password
        $securePassword.Dispose()
        $securePassword = $null
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { throw 'Database password missing' }
    foreach ($key in $expected.Keys) { [Environment]::SetEnvironmentVariable($key, $expected[$key], 'Process') }
    $env:PGSSLROOTCERT = (Resolve-Path -LiteralPath $CaCertificate).Path
    $env:WORKFLOW_SCHEMA_BOOTSTRAP_APPROVAL = 'dxos_wf25_smoke_01'
    Push-Location (Join-Path $PSScriptRoot '../backend')
    $pushed = $true
    if (-not (Test-Path -LiteralPath './gradlew.bat' -PathType Leaf)) { throw 'Gradle wrapper missing' }
    $phase = 'java_bootstrap'
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & './gradlew.bat' workflowSchemaBootstrap --offline --no-daemon --no-configuration-cache --console=plain --quiet 2>&1 |
            ForEach-Object {
                $line = $_.ToString()
                if ($line -match '^(SCHEMA_BOOTSTRAP=|DIRECT_CONNECTION=|TLS_VERIFIED=|POSTCOMMIT_AUDIT=|SCHEMA_OWNER=|SCHEMA_EMPTY=|SCHEMA_PRESENT=|FLYWAY_HISTORY=|OTHER_CLOUD_WRITES=|PGRST_NOTIFY=|BOOTSTRAP_PHASE=)') {
                    Write-Output $line
                    if ($line -match '^SCHEMA_BOOTSTRAP=') { $reported = $true }
                }
            }
        $bootstrapExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
} catch {
    $bootstrapExit = 1
} finally {
    if ($pushed) { Pop-Location }
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
}
if (-not $reported) {
    if ($phase -eq 'java_bootstrap') { Write-Output 'SCHEMA_BOOTSTRAP=COMMIT_UNKNOWN' }
    else { Write-Output 'SCHEMA_BOOTSTRAP=NOT_EXECUTED' }
    Write-Output 'DIRECT_CONNECTION=FAIL'
    Write-Output 'TLS_VERIFIED=NO'
    Write-Output 'POSTCOMMIT_AUDIT=NOT_RUN'
    Write-Output 'SCHEMA_OWNER=UNVERIFIED'
    Write-Output 'SCHEMA_EMPTY=NO'
    Write-Output 'FLYWAY_HISTORY=UNVERIFIED'
    Write-Output ('OTHER_CLOUD_WRITES=' + $(if ($phase -eq 'java_bootstrap') {
        'UNVERIFIED_POSTCOMMIT_AUDIT_NOT_CONFIRMED'
    } else { 'NONE' }))
    Write-Output "BOOTSTRAP_PHASE=$phase"
    $bootstrapExit = 1
}
exit $bootstrapExit
