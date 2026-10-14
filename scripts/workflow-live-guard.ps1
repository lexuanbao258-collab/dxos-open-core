$ErrorActionPreference = 'Stop'
$guardExit = 1
try {
    $required = @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','PGSSLROOTCERT')
    if (@($required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) }).Count) {
        throw 'Missing configuration'
    }
    if ($null -ne [Environment]::GetEnvironmentVariable('WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE')) {
        throw 'Local profile rejected'
    }
    # Credentials stay in the inherited environment; never pass them as Gradle arguments.
    Push-Location (Join-Path $PSScriptRoot '../backend')
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        if (-not (Test-Path -LiteralPath './gradlew.bat' -PathType Leaf)) { throw 'Gradle launcher missing' }
        # Windows PowerShell treats native stderr as errors; filter both streams without printing driver failures.
        $ErrorActionPreference = 'Continue'
        $guardResultSeen = $false
        & './gradlew.bat' workflowLiveGuard --no-daemon --no-configuration-cache --console=plain --quiet 2>&1 |
            ForEach-Object {
                $line = $_.ToString()
                if ($line -match '^(LIVE_GUARD=|manifest_triggers=|catalog_guard_ms |WRITE_READINESS=)') {
                    Write-Output $line
                    if ($line -match '^LIVE_GUARD=') { $guardResultSeen = $true }
                }
            }
        $guardExit = $LASTEXITCODE
        if (-not $guardResultSeen) { $guardExit = 1 }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
        Pop-Location
    }
} catch {
    [Console]::Error.WriteLine('LIVE_GUARD=FAIL phase=launcher')
    $guardExit = 1
}
if ($guardExit -ne 0) {
    [Console]::Error.WriteLine('LIVE_GUARD=FAIL phase=process')
}
exit $guardExit
