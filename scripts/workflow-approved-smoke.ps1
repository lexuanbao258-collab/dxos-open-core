param(
    [switch]$ExecuteApprovedSmoke,
    [switch]$AuditOnly,
    [string]$CaCertificate = $env:PGSSLROOTCERT,
    [string]$JavaHome = 'C:\Program Files\Java\jdk-21.0.11'
)
$ErrorActionPreference = 'Stop'
$runId = 'cloud-20261015-01'
$status = 'NOT_EXECUTED'
$precheck = 'NOT_RUN'
$postcheck = 'NOT_RUN'
$persistence = 'NOT_RUN'
$phase = 'launcher_validation'
$exitCode = 1
$handedOff = $false
$pushed = $false
$before = $null
$after = $null
$keys = @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','PGSSLROOTCERT','JAVA_HOME','Path',
    'WORKFLOW_DEV_INTEGRATION_OPT_IN','WORKFLOW_DEV_APPROVED_SCHEMA','WORKFLOW_DEV_PROJECT_REF',
    'WORKFLOW_DEV_BUNDLE','WORKFLOW_DEV_RUN_ID','WORKFLOW_DEV_WRITE_APPROVAL',
    'WORKFLOW_FLYWAY_APPROVAL','WORKFLOW_SCHEMA_BOOTSTRAP_APPROVAL')
$saved = @{}
foreach ($key in $keys) { $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }

function Assert-WarpIpv6 {
    $warp = (Get-Command warp-cli.exe -ErrorAction SilentlyContinue).Source
    if (-not $warp) { $warp = 'C:\Program Files\Cloudflare\Cloudflare WARP\warp-cli.exe' }
    if (-not (Test-Path -LiteralPath $warp -PathType Leaf)) { throw 'WARP CLI missing' }
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $warpStatus = & $warp status 2>&1
        if ($LASTEXITCODE -ne 0 -or ($warpStatus -join ' ') -notmatch '\bConnected\b') { throw 'WARP required' }
    } finally { $ErrorActionPreference = $previous }
    $addresses = [Net.Dns]::GetHostAddresses('db.zsfctpnitkdztlfhzqgw.supabase.co')
    if (-not @($addresses | Where-Object AddressFamily -eq ([Net.Sockets.AddressFamily]::InterNetworkV6))) {
        throw 'Direct IPv6 required'
    }
}

function Invoke-Development([string]$Mode) {
    $fields = @{}
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & './gradlew.bat' workflowDevelopmentSmoke "--args=--development-integration $Mode $runId" `
            --offline --no-daemon --no-configuration-cache --console=plain --quiet 2>&1 | ForEach-Object {
                $line = $_.ToString()
                if ($line -match '^(DEVELOPMENT_AUDIT_PASS|DEVELOPMENT_CATALOG_SHA256=[a-f0-9]{64}|DEVELOPMENT_(RUN_)?(INSTANCES|HISTORY)=[0-9]+|DEVELOPMENT_SMOKE_PASS|DEVELOPMENT_RESTART_READ_PASS|WORKFLOW_DEVELOPMENT_FAIL_[A-Z_]+|WORKFLOW_DEVELOPMENT_SMOKE_FAIL)$') {
                    $pair = $line.Split('=',2)
                    if ($fields.ContainsKey($pair[0])) { throw 'Duplicate child report' }
                    $fields[$pair[0]] = if ($pair.Count -eq 2) { $pair[1] } else { 'YES' }
                }
            }
        $childExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    $required = switch ($Mode) {
        'audit' { @('DEVELOPMENT_AUDIT_PASS','DEVELOPMENT_CATALOG_SHA256','DEVELOPMENT_INSTANCES',
            'DEVELOPMENT_HISTORY','DEVELOPMENT_RUN_INSTANCES','DEVELOPMENT_RUN_HISTORY') }
        'smoke' { @('DEVELOPMENT_SMOKE_PASS') }
        'read' { @('DEVELOPMENT_RESTART_READ_PASS') }
        default { throw 'Mode rejected' }
    }
    $valid = $childExit -eq 0 -and $fields.Count -eq $required.Count -and
        @($required | Where-Object { -not $fields.ContainsKey($_) }).Count -eq 0
    return @{ Valid=$valid; Fields=$fields }
}

try {
    if ($ExecuteApprovedSmoke -eq $AuditOnly) { throw 'Select approved execution or read-only audit' }
    $expected = @{DB_HOST='db.zsfctpnitkdztlfhzqgw.supabase.co'; DB_PORT='5432'; DB_NAME='postgres'; DB_USER='postgres'}
    foreach ($key in $expected.Keys) {
        $existing = [Environment]::GetEnvironmentVariable($key, 'Process')
        if ($existing -and $existing -ne $expected[$key]) { throw 'Endpoint mismatch' }
    }
    foreach ($key in @('WORKFLOW_DEV_LOCAL_DISPOSABLE','WF25_DISPOSABLE_TEST','WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE')) {
        if ($null -ne [Environment]::GetEnvironmentVariable($key, 'Process')) { throw 'Local profile rejected' }
    }
    if (-not $CaCertificate) { $CaCertificate = Read-Host 'Supabase CA certificate file path' }
    if (-not (Test-Path -LiteralPath $CaCertificate -PathType Leaf)) { throw 'Trusted CA missing' }
    if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe') -PathType Leaf)) { throw 'JDK missing' }
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;$env:Path"
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $version = & java.exe -version 2>&1
        if ($LASTEXITCODE -ne 0 -or ($version -join ' ') -notmatch 'version "21\.') { throw 'JDK 21 required' }
    } finally { $ErrorActionPreference = $previous }
    $phase = 'warp_ipv6_precheck'
    Assert-WarpIpv6
    $phase = 'secure_password_input'
    $securePassword = Read-Host 'Supabase postgres password (hidden)' -AsSecureString
    try { $env:DB_PASSWORD = [Net.NetworkCredential]::new('postgres', $securePassword).Password }
    finally { $securePassword.Dispose(); $securePassword = $null }
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { throw 'Password missing' }
    foreach ($key in $expected.Keys) { [Environment]::SetEnvironmentVariable($key, $expected[$key], 'Process') }
    $env:PGSSLROOTCERT = (Resolve-Path -LiteralPath $CaCertificate).Path
    $env:WORKFLOW_DEV_INTEGRATION_OPT_IN = 'issue-25-postgres-development-only'
    $env:WORKFLOW_DEV_APPROVED_SCHEMA = 'dxos_wf25_smoke_01'
    $env:WORKFLOW_DEV_PROJECT_REF = 'zsfctpnitkdztlfhzqgw'
    $env:WORKFLOW_DEV_RUN_ID = $runId
    foreach ($key in @('WORKFLOW_DEV_WRITE_APPROVAL','WORKFLOW_FLYWAY_APPROVAL','WORKFLOW_SCHEMA_BOOTSTRAP_APPROVAL')) {
        [Environment]::SetEnvironmentVariable($key, $null, 'Process')
    }
    Push-Location (Join-Path $PSScriptRoot '../backend')
    $pushed = $true
    $env:WORKFLOW_DEV_BUNDLE = Join-Path (Get-Location).Path 'build/workflow-smoke-bundle'
    $phase = 'fresh_read_only_precheck'
    $before = Invoke-Development 'audit'
    if (-not $before.Valid) { $precheck = 'FAIL'; throw 'Audit failed' }
    if ($AuditOnly) { $status = 'AUDIT_ONLY'; $postcheck = 'READ_ONLY_PASS'; $exitCode = 0 }
    else {
        if ($before.Fields.DEVELOPMENT_INSTANCES -ne '0' -or $before.Fields.DEVELOPMENT_HISTORY -ne '0' -or
            $before.Fields.DEVELOPMENT_RUN_INSTANCES -ne '0' -or $before.Fields.DEVELOPMENT_RUN_HISTORY -ne '0') {
            $precheck = 'FAIL'; throw 'Existing smoke data requires review; never rerun'
        }
        $precheck = 'PASS'
        $phase = 'approved_smoke_handoff'
        $env:WORKFLOW_DEV_WRITE_APPROVAL = 'dxos_wf25_smoke_01'
        $handedOff = $true
        try { $smoke = Invoke-Development 'smoke' }
        finally { [Environment]::SetEnvironmentVariable('WORKFLOW_DEV_WRITE_APPROVAL', $null, 'Process') }
        $phase = 'fresh_jvm_read'
        $read = Invoke-Development 'read'
        $persistence = if ($read.Valid) { 'PASS' } else { 'FAIL' }
        $phase = 'fresh_read_only_postcheck'
        $after = Invoke-Development 'audit'
        $postcheck = 'FAIL'
        if ($after.Valid -and $after.Fields.DEVELOPMENT_CATALOG_SHA256 -eq $before.Fields.DEVELOPMENT_CATALOG_SHA256 -and
            $after.Fields.DEVELOPMENT_INSTANCES -eq '2' -and $after.Fields.DEVELOPMENT_HISTORY -eq '4' -and
            $after.Fields.DEVELOPMENT_RUN_INSTANCES -eq '2' -and $after.Fields.DEVELOPMENT_RUN_HISTORY -eq '4') {
            $postcheck = 'PASS'
        }
        if (-not $smoke.Valid -or $persistence -ne 'PASS' -or $postcheck -ne 'PASS') { throw 'Reconciliation required' }
        $status = 'COMMITTED_PASS'
        $exitCode = 0
    }
} catch {
    $status = if ($handedOff) { 'COMMIT_OUTCOME_UNKNOWN' } else { 'NOT_EXECUTED' }
    # A failed/missing acknowledgment never proves rollback of the multi-transaction smoke.
    if ($handedOff -and $null -eq $after) {
        try { $after = Invoke-Development 'audit'; $postcheck = if ($after.Valid) { 'AUDIT_OBSERVED' } else { 'FAIL' } }
        catch { $postcheck = 'FAIL' }
    }
} finally {
    if ($pushed) { Pop-Location }
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
}
Write-Output "CLOUD_SMOKE_EXECUTION=$status"
Write-Output "SMOKE_RUN_ID=$runId"
Write-Output "PRECHECK=$precheck"
Write-Output "POSTCHECK=$postcheck"
Write-Output "FRESH_JVM_PERSISTENCE=$persistence"
Write-Output "SMOKE_PHASE=$phase"
$observed = if ($null -ne $after) { $after } else { $before }
if ($null -ne $observed -and $observed.Valid) {
    foreach ($key in @('DEVELOPMENT_CATALOG_SHA256','DEVELOPMENT_INSTANCES','DEVELOPMENT_HISTORY',
        'DEVELOPMENT_RUN_INSTANCES','DEVELOPMENT_RUN_HISTORY')) { Write-Output "$key=$($observed.Fields[$key])" }
}
Write-Output 'OWNER_APPROVAL_REQUIRED=YES'
exit $exitCode
