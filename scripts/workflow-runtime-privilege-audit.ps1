$ErrorActionPreference = 'Stop'
$auditExit = 1
try {
    $required = @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','PGSSLROOTCERT')
    if (@($required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) }).Count) {
        throw 'Missing configuration'
    }
    if ($null -ne [Environment]::GetEnvironmentVariable('WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE')) {
        throw 'Local profile rejected'
    }
    # Secrets remain in the inherited environment, never Gradle arguments or printed errors.
    Push-Location (Join-Path $PSScriptRoot '../backend')
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $resultSeen = $false
        & './gradlew.bat' workflowRuntimePrivilegeAudit --no-daemon --no-configuration-cache --console=plain --quiet 2>&1 |
            ForEach-Object {
                $line = $_.ToString()
                if ($line -match '^(RUNTIME_PRIVILEGE_AUDIT=|AUDIT_REPORT=|WRITE_READINESS=)') {
                    Write-Output $line
                    if ($line -match '^RUNTIME_PRIVILEGE_AUDIT=') { $resultSeen = $true }
                }
            }
        $auditExit = $LASTEXITCODE
        if (-not $resultSeen) { $auditExit = 1 }
    } finally {
        $ErrorActionPreference = $previousPreference
        Pop-Location
    }
} catch {
    [Console]::Error.WriteLine('RUNTIME_PRIVILEGE_AUDIT=FAIL phase=launcher')
    $auditExit = 1
}
if ($auditExit -ne 0) { [Console]::Error.WriteLine('RUNTIME_PRIVILEGE_AUDIT=FAIL phase=process') }
exit $auditExit
