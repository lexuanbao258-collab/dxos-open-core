$ErrorActionPreference = 'Stop'
$required = @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','PGSSLROOTCERT')
$missing = @($required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) })
if ($missing.Count) {
    [Console]::Error.WriteLine('Missing environment variables: ' + ($missing -join ', '))
    exit 1
}
Push-Location (Join-Path $PSScriptRoot '../backend')
try {
    & './gradlew.bat' workflowReadOnlyPreflight --no-daemon
    $preflightExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $preflightExitCode
