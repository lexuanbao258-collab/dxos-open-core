param(
    [switch]$ExecuteApprovedMigrations,
    [string]$CaCertificate = $env:PGSSLROOTCERT,
    [string]$JavaHome = 'C:\Program Files\Java\jdk-21.0.11'
)
$ErrorActionPreference = 'Stop'
$migrationExit = 1
$phase = 'launcher_validation'
$keys = @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','PGSSLROOTCERT',
    'WORKFLOW_FLYWAY_APPROVAL','JAVA_HOME','Path')
$saved = @{}
foreach ($key in $keys) { $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$pushed = $false
$reports = @{}
$handoff = $false
function Assert-WarpIpv6 {
    $warp = (Get-Command warp-cli.exe -ErrorAction SilentlyContinue).Source
    if (-not $warp) { $warp = 'C:\Program Files\Cloudflare\Cloudflare WARP\warp-cli.exe' }
    if (-not (Test-Path -LiteralPath $warp -PathType Leaf)) { throw 'WARP CLI missing' }
    $preference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $status = & $warp status 2>&1
        if ($LASTEXITCODE -ne 0 -or ($status -join ' ') -notmatch '\bConnected\b') { throw 'WARP must be connected' }
    } finally { $ErrorActionPreference = $preference }
    $addresses = [Net.Dns]::GetHostAddresses('db.zsfctpnitkdztlfhzqgw.supabase.co')
    if (-not @($addresses | Where-Object AddressFamily -eq ([Net.Sockets.AddressFamily]::InterNetworkV6))) {
        throw 'Direct IPv6 DNS unavailable'
    }
}
try {
    if (-not $ExecuteApprovedMigrations) { throw 'Explicit approved invocation required' }
    $expected = @{DB_HOST='db.zsfctpnitkdztlfhzqgw.supabase.co'; DB_PORT='5432'; DB_NAME='postgres'; DB_USER='postgres'}
    foreach ($key in $expected.Keys) {
        $existing = [Environment]::GetEnvironmentVariable($key, 'Process')
        if ($existing -and $existing -ne $expected[$key]) { throw 'Endpoint mismatch' }
    }
    foreach ($key in @('WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE','WORKFLOW_DEV_LOCAL_DISPOSABLE','WF25_DISPOSABLE_TEST')) {
        if ($null -ne [Environment]::GetEnvironmentVariable($key, 'Process')) { throw 'Local profile rejected' }
    }
    if (-not $CaCertificate) { $CaCertificate = Read-Host 'Supabase CA certificate file path' }
    if (-not (Test-Path -LiteralPath $CaCertificate -PathType Leaf)) { throw 'Trusted CA missing' }
    if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe') -PathType Leaf)) { throw 'JDK 21 missing' }
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;$env:Path"
    $preference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $version = & java.exe -version 2>&1
    } finally { $ErrorActionPreference = $preference }
    if (($version -join ' ') -notmatch 'version "21\.') { throw 'JDK 21 required' }
    $phase = 'warp_ipv6_precheck'
    Assert-WarpIpv6
    $phase = 'secure_password_input'
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
        $securePassword = Read-Host 'Supabase postgres password (hidden)' -AsSecureString
        try { $env:DB_PASSWORD = [Net.NetworkCredential]::new('postgres', $securePassword).Password }
        finally { $securePassword.Dispose(); $securePassword = $null }
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { throw 'Password missing' }
    foreach ($key in $expected.Keys) { [Environment]::SetEnvironmentVariable($key, $expected[$key], 'Process') }
    $env:PGSSLROOTCERT = (Resolve-Path -LiteralPath $CaCertificate).Path
    $env:WORKFLOW_FLYWAY_APPROVAL = 'dxos_wf25_smoke_01'
    Push-Location (Join-Path $PSScriptRoot '../backend')
    $pushed = $true
    if (-not (Test-Path -LiteralPath './gradlew.bat' -PathType Leaf)) { throw 'Gradle wrapper missing' }
    $phase = 'java_approved_migration'
    $handoff = $true
    $preference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & './gradlew.bat' workflowApprovedMigration --offline --no-daemon --no-configuration-cache --console=plain --quiet 2>&1 |
            ForEach-Object {
                $line = $_.ToString()
                if ($line -match '^(FLYWAY_EXECUTION=(COMMITTED_PASS|PARTIAL_COMMIT|FAILED_ROLLED_BACK|COMMIT_UNKNOWN|NOT_EXECUTED)|DIRECT_CONNECTION=(PASS|FAIL)|TLS_VERIFIED=(YES|NO)|V[34]_STATUS=(NOT_RUN|ABSENT|SUCCESS_PIN_MATCH|MISMATCH)|FLYWAY_HISTORY=(UNVERIFIED|ABSENT|EMPTY|V3_ONLY_SUCCESS|V3_V4_SUCCESS_CHECKSUM_MATCH|MISMATCH)|INVENTORY_CHECK=(PASS|FAIL|NOT_RUN)|SEED_CHECK=(PASS|FAIL|NOT_RUN)|POSTCOMMIT_AUDIT=(PASS|FAIL|NOT_RUN)|OTHER_CLOUD_WRITES=(NONE|UNVERIFIED_POSTCOMMIT_AUDIT_NOT_RUN|CATALOG_DRIFT_[a-z_]+)|MIGRATION_PHASE=[a-z_]+|FAILURE_CODE=[A-Z_]+|FAILURE_SQLSTATE=([0-9A-Z]{5}|NONE)|NEXT_APPROVAL_REQUIRED=YES)$') {
                    $pair = $line.Split('=',2)
                    if ($reports.ContainsKey($pair[0])) { throw 'Duplicate report field' }
                    $reports[$pair[0]] = $pair[1]
                }
            }
        $migrationExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $preference }
} catch {
    $migrationExit = 1
} finally {
    if ($pushed) { Pop-Location }
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
}
$required = @('FLYWAY_EXECUTION','DIRECT_CONNECTION','TLS_VERIFIED','V3_STATUS','V4_STATUS',
    'FLYWAY_HISTORY','INVENTORY_CHECK','SEED_CHECK','POSTCOMMIT_AUDIT','OTHER_CLOUD_WRITES','MIGRATION_PHASE','NEXT_APPROVAL_REQUIRED')
if (@($required | Where-Object { -not $reports.ContainsKey($_) }).Count -eq 0) {
    foreach ($key in $required) { Write-Output "$key=$($reports[$key])" }
    foreach ($key in @('FAILURE_CODE','FAILURE_SQLSTATE')) {
        if ($reports.ContainsKey($key)) { Write-Output "$key=$($reports[$key])" }
    }
    $successExpected = @{FLYWAY_EXECUTION='COMMITTED_PASS'; POSTCOMMIT_AUDIT='PASS'; DIRECT_CONNECTION='PASS';
        TLS_VERIFIED='YES'; INVENTORY_CHECK='PASS'; SEED_CHECK='PASS'; OTHER_CLOUD_WRITES='NONE';
        V3_STATUS='SUCCESS_PIN_MATCH'; V4_STATUS='SUCCESS_PIN_MATCH'; FLYWAY_HISTORY='V3_V4_SUCCESS_CHECKSUM_MATCH'}
    $mismatch = @($successExpected.Keys | Where-Object { $reports[$_] -ne $successExpected[$_] }).Count
    if ($migrationExit -eq 0 -and $mismatch -ne 0) { $migrationExit = 1 }
} else {
    Write-Output ("FLYWAY_EXECUTION=" + $(if($handoff){'COMMIT_UNKNOWN'}else{'NOT_EXECUTED'}))
    Write-Output 'DIRECT_CONNECTION=FAIL'
    Write-Output 'TLS_VERIFIED=NO'
    Write-Output 'V3_STATUS=NOT_RUN'
    Write-Output 'V4_STATUS=NOT_RUN'
    Write-Output 'FLYWAY_HISTORY=UNVERIFIED'
    Write-Output 'INVENTORY_CHECK=NOT_RUN'
    Write-Output 'SEED_CHECK=NOT_RUN'
    Write-Output 'POSTCOMMIT_AUDIT=NOT_RUN'
    Write-Output ("OTHER_CLOUD_WRITES=" + $(if($handoff){'UNVERIFIED_POSTCOMMIT_AUDIT_NOT_RUN'}else{'NONE'}))
    Write-Output "MIGRATION_PHASE=$phase"
    Write-Output 'NEXT_APPROVAL_REQUIRED=YES'
    Write-Output ("FAILURE_CODE=" + $(if($handoff){'CHILD_REPORT_MISSING'}else{'LAUNCHER_PRECHECK_FAILED'}))
    Write-Output 'FAILURE_SQLSTATE=NONE'
    $migrationExit = 1
}
exit $migrationExit
