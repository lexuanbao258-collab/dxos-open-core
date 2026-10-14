$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$keys = @('WORKFLOW_TEST_JDBC_URL', 'WORKFLOW_TEST_MIGRATION_USER', 'WORKFLOW_TEST_MIGRATION_PASSWORD',
    'WORKFLOW_TEST_DB_USER', 'WORKFLOW_TEST_DB_PASSWORD', 'WORKFLOW_TEST_CA_CERT', 'WORKFLOW_TEST_WRONG_CA_CERT',
    'WORKFLOW_AUDIT_LOCAL_JDBC_URL', 'WORKFLOW_AUDIT_LOCAL_CA_CERT', 'WORKFLOW_AUDIT_LOCAL_WRONG_CA_CERT',
    'WORKFLOW_AUDIT_LOCAL_DB_PASSWORD', 'WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE', 'WF25_DISPOSABLE_TEST',
    'DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USER', 'DB_PASSWORD', 'PGSSLROOTCERT', 'POSTGRES_PASSWORD')
$saved = @{}
foreach ($key in $keys) { $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$tempRoot = (Resolve-Path -LiteralPath $env:TEMP).Path
$fixture = Join-Path $tempRoot ('wf25-local-tls-' + [guid]::NewGuid().ToString('N'))
$container = 'dxos-wf25-local-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$started = $false
$pushed = $false
$openssl = (Get-Command openssl.exe -ErrorAction SilentlyContinue).Source
if (-not $openssl) { $openssl = 'C:\Program Files\Git\usr\bin\openssl.exe' }

function Invoke-LocalOpenSsl([string[]]$Arguments) {
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $openssl @Arguments 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'Local certificate generation failed' }
    } finally { $ErrorActionPreference = $previous }
}

try {
    if (-not (Test-Path -LiteralPath $openssl -PathType Leaf)) { throw 'OpenSSL is required (Git for Windows includes it)' }
    Get-Command docker.exe, java.exe -ErrorAction Stop | Out-Null
    $dockerEndpoint = if ($env:DOCKER_HOST -and -not $env:DOCKER_CONTEXT) { $env:DOCKER_HOST } else {
        & docker.exe context inspect --format '{{.Endpoints.docker.Host}}'
    }
    if ($dockerEndpoint -notmatch '^(npipe:////\./pipe/|unix:///)') { throw 'Local Docker endpoint required' }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $javaVersion = & java.exe -version 2>&1
    $ErrorActionPreference = $previous
    if (($javaVersion -join ' ') -notmatch 'version "21\.') { throw 'Put JDK 21 on PATH before running this script' }
    New-Item -ItemType Directory -Path $fixture | Out-Null
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $fixture /inheritance:r /grant:r "${identity}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Local fixture ACL setup failed' }
    Invoke-LocalOpenSsl @('req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '2', '-subj', '/CN=Issue25 local test CA',
        '-keyout', "$fixture\ca.key", '-out', "$fixture\ca.crt")
    Invoke-LocalOpenSsl @('req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '2', '-subj', '/CN=Issue25 wrong test CA',
        '-keyout', "$fixture\wrong-ca.key", '-out', "$fixture\wrong-ca.crt")
    Invoke-LocalOpenSsl @('req', '-newkey', 'rsa:2048', '-nodes', '-subj', '/CN=localhost',
        '-keyout', "$fixture\server.key", '-out', "$fixture\server.csr")
    [IO.File]::WriteAllText("$fixture\server.ext", 'subjectAltName=DNS:localhost')
    Invoke-LocalOpenSsl @('x509', '-req', '-in', "$fixture\server.csr", '-CA', "$fixture\ca.crt", '-CAkey', "$fixture\ca.key",
        '-CAcreateserial', '-days', '2', '-extfile', "$fixture\server.ext", '-out', "$fixture\server.crt")
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $bytes = New-Object byte[] 32
        $random.GetBytes($bytes)
        $env:WORKFLOW_TEST_MIGRATION_PASSWORD = [BitConverter]::ToString($bytes).Replace('-', '')
        $random.GetBytes($bytes)
        $env:WORKFLOW_TEST_DB_PASSWORD = [BitConverter]::ToString($bytes).Replace('-', '')
    } finally { $random.Dispose() }
    $env:POSTGRES_PASSWORD = $env:WORKFLOW_TEST_MIGRATION_PASSWORD
    $dockerArguments = @('run', '-d', '--rm', '--name', $container, '--label', 'dxos.issue25.disposable=true',
        '-p', '127.0.0.1::5432', '-e', 'POSTGRES_PASSWORD', '-e', 'POSTGRES_USER=workflow_test', '-e', 'POSTGRES_DB=workflow_test',
        '--mount', "type=bind,source=$fixture,target=/tls,readonly", '--entrypoint', 'sh', 'postgres:17.11-alpine', '-c',
        'cp /tls/server.crt /tmp/server.crt; cp /tls/server.key /tmp/server.key; chown postgres:postgres /tmp/server.crt /tmp/server.key; chmod 600 /tmp/server.key; exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tmp/server.crt -c ssl_key_file=/tmp/server.key')
    & docker.exe @dockerArguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Local PostgreSQL startup failed' }
    $started = $true
    Remove-Item Env:POSTGRES_PASSWORD
    $port = (& docker.exe port $container 5432/tcp) -replace '^127\.0\.0\.1:', ''
    if ($LASTEXITCODE -ne 0 -or $port -notmatch '^[0-9]+$') { throw 'Loopback-only PostgreSQL port required' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        & docker.exe exec $container pg_isready -h localhost -U workflow_test -d workflow_test *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Local PostgreSQL did not become ready' }
    $version = & docker.exe exec $container psql -X -At -U workflow_test -d workflow_test -c 'SHOW server_version_num;'
    if ($LASTEXITCODE -ne 0 -or $version -ne '170011') { throw 'PostgreSQL 17.11 is required' }
    # Generated credentials are passed on stdin, never in args, source files or printed output.
    $roleSql = "CREATE ROLE workflow_runtime LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '" +
        $env:WORKFLOW_TEST_DB_PASSWORD + "'; CREATE ROLE postgres LOGIN SUPERUSER PASSWORD '" +
        $env:WORKFLOW_TEST_MIGRATION_PASSWORD + "'; ALTER ROLE workflow_test SET dxos.wf25_disposable = 'on'; ALTER ROLE postgres SET dxos.wf25_disposable = 'on';"
    $roleSql | & docker.exe exec -i $container psql -X -U workflow_test -d workflow_test -v ON_ERROR_STOP=1 | Out-Null
    $roleSql = $null
    if ($LASTEXITCODE -ne 0) { throw 'Local test roles initialization failed' }
    $env:WORKFLOW_TEST_JDBC_URL = "jdbc:postgresql://127.0.0.1:$port/workflow_test"
    $env:WORKFLOW_TEST_MIGRATION_USER = 'workflow_test'
    $env:WORKFLOW_TEST_DB_USER = 'workflow_runtime'
    $env:WORKFLOW_TEST_CA_CERT = "$fixture\ca.crt"
    $env:WORKFLOW_TEST_WRONG_CA_CERT = "$fixture\wrong-ca.crt"
    $env:WORKFLOW_AUDIT_LOCAL_JDBC_URL = "jdbc:postgresql://127.0.0.1:$port/postgres"
    $env:WORKFLOW_AUDIT_LOCAL_CA_CERT = $env:WORKFLOW_TEST_CA_CERT
    $env:WORKFLOW_AUDIT_LOCAL_WRONG_CA_CERT = $env:WORKFLOW_TEST_WRONG_CA_CERT
    $env:WORKFLOW_AUDIT_LOCAL_DB_PASSWORD = $env:WORKFLOW_TEST_MIGRATION_PASSWORD
    $env:WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE = 'true'
    $env:WF25_DISPOSABLE_TEST = '1'
    $env:DB_HOST = 'localhost'
    $env:DB_PORT = $port
    $env:DB_NAME = 'postgres'
    $env:DB_USER = 'workflow_test'
    $env:DB_PASSWORD = $env:WORKFLOW_TEST_MIGRATION_PASSWORD
    $env:PGSSLROOTCERT = $env:WORKFLOW_TEST_CA_CERT
    Push-Location (Join-Path $root 'backend')
    $pushed = $true
    & '.\gradlew.bat' test build --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Local test/build failed; inspect backend/build/reports/tests/test' }
    $reports = @(Get-ChildItem -LiteralPath 'build/test-results/test' -Filter 'TEST-*.xml')
    if ($reports.Count -eq 0) { throw 'Test results missing' }
    $tests = 0
    $failures = 0
    $skipped = 0
    $integrationSuites = 0
    foreach ($report in $reports) {
        [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
        $suite = $xml.testsuite
        $tests += [int]$suite.tests
        $failures += [int]$suite.failures + [int]$suite.errors
        $skipped += [int]$suite.skipped
        if ($suite.name -like '*infrastructure.workflow.*IntegrationTest' -and [int]$suite.tests -gt 0) { $integrationSuites++ }
    }
    if ($tests -eq 0 -or $failures -ne 0 -or $skipped -ne 0 -or $integrationSuites -ne 9) {
        throw 'Local verification requires all nine Workflow integration suites and zero failures/skips'
    }
    Write-Output "LOCAL_WORKFLOW_VERIFICATION=PASS tests=$tests failures=$failures skipped=$skipped postgres=17.11"
} finally {
    try {
        if ($pushed) { Pop-Location }
        if ($started) {
            $labelsJson = & docker.exe inspect --format '{{json .Config.Labels}}' $container
            if ($LASTEXITCODE -ne 0 -or ($labelsJson | ConvertFrom-Json).'dxos.issue25.disposable' -ne 'true') {
                throw 'Refusing to stop a container without the disposable label'
            }
            & docker.exe stop $container | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'Local fixture container cleanup failed' }
        }
        if (Test-Path -LiteralPath $fixture) {
            $target = (Resolve-Path -LiteralPath $fixture).Path
            if (-not $target.StartsWith($tempRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase) -or $target -eq $tempRoot) {
                throw 'Refusing unsafe local fixture cleanup'
            }
            Remove-Item -LiteralPath $target -Recurse -Force
        }
    } finally {
        foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
    }
}
