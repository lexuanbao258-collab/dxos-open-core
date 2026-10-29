param([Parameter(Mandatory)][ValidatePattern('(?-i)\A[a-z][a-z0-9_]{0,23}\z')][string]$RunId)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$container = 'dxos-helpdesk-negative-' + $RunId
$volume = $container + '-data'
$database = 'helpdesk_disposable_test'
$image = 'postgres:17.11-alpine'
$evidence = Join-Path $root ('backend/build/closeout-' + $RunId)
function Invoke-NegativeDocker([string[]]$Arguments) {
    $result = & docker --context desktop-linux @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ('Docker operation failed: ' + $Arguments[0] + '; resources retained') }
    $result
}
$endpoint = (Invoke-NegativeDocker @('context','inspect','desktop-linux','--format','{{.Endpoints.docker.Host}}') | Out-String).Trim()
if ($endpoint -notmatch '^npipe:/{4}\./pipe/dockerDesktopLinuxEngine$') { throw 'Local Docker Desktop Linux required' }
$existingIds = @(Invoke-NegativeDocker @('ps','-aq'))
$metadataFormat = '{{.Id}} {{.Name}} {{.State.StartedAt}} {{.State.Status}} {{.RestartCount}}'
$before = @(Invoke-NegativeDocker (@('inspect','--format',$metadataFormat) + $existingIds))
if (@(Invoke-NegativeDocker @('ps','-a','--format','{{.Names}}')) -contains $container -or
    @(Invoke-NegativeDocker @('volume','ls','--format','{{.Name}}')) -contains $volume -or
    @(Invoke-NegativeDocker @('ps','-a','--filter','publish=55439','--format','{{.ID}}')).Count -gt 0 -or
    @(Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq 55439).Count -gt 0 -or
    (Test-Path -LiteralPath $evidence)) { throw 'Fixture name, volume, port or evidence path already exists; nothing changed' }
$null = Invoke-NegativeDocker @('image','inspect',$image,'--format','{{.Id}}')
$null = New-Item -ItemType Directory -Path $evidence
$before | Set-Content -LiteralPath (Join-Path $evidence 'protected-containers-before.txt')
$saved = @{}
foreach ($name in @('HELPDESK_DISPOSABLE_URL','HELPDESK_DISPOSABLE_PASSWORD','WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE',
    'WORKFLOW_TEST_JDBC_URL','B06_EMPLOYEE_ACCESS_TOKEN','B06_COORDINATOR_ACCESS_TOKEN','B06_WORKER_ACCESS_TOKEN')) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, $null, 'Process')
}
$bytes = New-Object byte[] 48
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$secret = [Convert]::ToBase64String($bytes)
[Array]::Clear($bytes, 0, $bytes.Length)
try {
    $nonce = [Guid]::NewGuid().ToString('N')
    $null = Invoke-NegativeDocker @('volume','create','--label',"dxos.helpdesk.negative=$RunId",'--label',"dxos.helpdesk.instance=$nonce",$volume)
    $volumeInfo = (Invoke-NegativeDocker @('volume','inspect',$volume) | Out-String) | ConvertFrom-Json
    if ($volumeInfo[0].Labels.'dxos.helpdesk.instance' -cne $nonce) { throw 'Concurrent volume creation; refusing reuse' }
    # Bootstrap secret travels over stdin into tmpfs, never Docker Config.Env or a host file.
    $startup = 'while [ ! -s /run/secrets/pg_password ]; do sleep 1; done; exec docker-entrypoint.sh postgres -c log_statement=none -c log_min_error_statement=panic'
    $null = Invoke-NegativeDocker @('run','-d','--pull=never','--name',$container,'--label',"dxos.helpdesk.negative=$RunId",
        '-p','127.0.0.1:55439:5432','--mount',"type=volume,source=$volume,target=/var/lib/postgresql/data",
        '--tmpfs','/run/secrets:rw,noexec,nosuid,size=65536',
        '-e',"POSTGRES_DB=$database",'-e','POSTGRES_USER=helpdesk_test',
        '-e','POSTGRES_PASSWORD_FILE=/run/secrets/pg_password','-e','POSTGRES_INITDB_ARGS=--auth-host=scram-sha-256',
        '--entrypoint','sh',$image,'-c',$startup)
    $secret | & docker --context desktop-linux exec -i $container sh -c "umask 077; tr -d '\r\n' > /run/secrets/pg_password" 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Secret bootstrap failed; no credential logged' }
    $info = ((Invoke-NegativeDocker @('inspect',$container) | Out-String) | ConvertFrom-Json)[0]
    $binding = @($info.HostConfig.PortBindings.'5432/tcp')
    if ($info.Config.Labels.'dxos.helpdesk.negative' -cne $RunId -or $binding.Count -ne 1 -or
        $binding[0].HostIp -cne '127.0.0.1' -or $binding[0].HostPort -cne '55439' -or
        @($info.Mounts | Where-Object { $_.Type -eq 'volume' -and $_.Name -ceq $volume }).Count -ne 1 -or
        -not $info.HostConfig.Tmpfs.'/run/secrets' -or
        @($info.Config.Env | Where-Object { $_ -like 'POSTGRES_PASSWORD=*' }).Count -gt 0) { throw 'Fixture isolation mismatch' }
    $ready = $false
    for ($i=0; $i -lt 60; $i++) {
        $null = & docker --context desktop-linux exec $container pg_isready -h 127.0.0.1 -U helpdesk_test -d $database 2>&1
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Isolated PostgreSQL not ready; retained without cleanup' }
    $null = Invoke-NegativeDocker @('exec',$container,'sh','-c','rm /run/secrets/pg_password')
    $identity = Invoke-NegativeDocker @('exec',$container,'psql','-X','-w','-At','-U','helpdesk_test','-d',$database,
        '-c',"BEGIN READ ONLY; SELECT current_database(),current_setting('server_version_num'); ROLLBACK;")
    if (-not ($identity -match '^helpdesk_disposable_test\|17[0-9]{4}$')) { throw 'Unexpected database or PostgreSQL version' }
    "CONTAINER=$container PORT=127.0.0.1:55439 DATABASE=$database VOLUME=$volume" | Tee-Object -FilePath (Join-Path $evidence 'isolation.txt')
    $env:HELPDESK_DISPOSABLE_URL = 'jdbc:postgresql://127.0.0.1:55439/helpdesk_disposable_test'
    $env:HELPDESK_DISPOSABLE_PASSWORD = $secret.Trim()
    $env:WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE = 'true'
    Push-Location (Join-Path $root 'backend')
    try {
        $output = & .\gradlew.bat --no-daemon test --tests '*HelpdeskHttpPostgresTest' --console=plain 2>&1
        $testExit = $LASTEXITCODE
    } finally { Pop-Location }
    $sanitized = ($output | Out-String).Replace($secret, '<REDACTED>')
    $sanitized = $sanitized -replace '(?i)Bearer\s+\S+', 'Bearer <REDACTED>' -replace 'eyJ[\w-]+\.[\w-]+\.[\w-]*', '<REDACTED_JWT>'
    $sanitized | Set-Content -LiteralPath (Join-Path $evidence 'http-postgres.log') -Encoding UTF8
    $reports = @(Get-ChildItem (Join-Path $root 'backend/build/test-results/test') -Filter 'TEST-*HelpdeskHttpPostgresTest.xml')
    if ($reports.Count -ne 1) { throw 'Missing unique HTTP/PostgreSQL test report' }
    foreach ($file in $reports) {
        [xml]$report = Get-Content -LiteralPath $file.FullName
        $suite = $report.testsuite
        $cases = @($suite.testcase | ForEach-Object { @{ name=$_.name; time=$_.time; failed=($null -ne $_.failure -or $null -ne $_.error); skipped=($null -ne $_.skipped) } })
        @{ suite=$suite.name; tests=$suite.tests; failures=$suite.failures; errors=$suite.errors; skipped=$suite.skipped; cases=$cases } |
            ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'http-postgres-results.json') -Encoding UTF8
        "TESTS=$($suite.tests) FAILURES=$($suite.failures) ERRORS=$($suite.errors) SKIPPED=$($suite.skipped) EXIT=$testExit"
        foreach ($required in @('fullBootApplicationStartsAndServesGuardedRoutesOverHttp()',
            'trustedLocalProvisioningBindsNamesToExactSubjectsWithoutGrantingAuthority()')) {
            $case = @($cases | Where-Object { $_.name -ceq $required })
            if ($case.Count -ne 1 -or $case[0].failed -or $case[0].skipped) { throw "Required check did not pass: $required" }
        }
    }
    if ($testExit -ne 0) { throw "Tests failed; inspect sanitized log at $evidence" }
} finally {
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    $secret = $null; $output = $null; $sanitized = $null; $info = $null
    $after = @(Invoke-NegativeDocker (@('inspect','--format',$metadataFormat) + $existingIds))
    $after | Set-Content -LiteralPath (Join-Path $evidence 'protected-containers-after.txt')
    if (($before -join "`n") -cne ($after -join "`n")) { throw 'Protected container metadata changed; inspect evidence' }
    Write-Output "PROTECTED_CONTAINERS_UNCHANGED=PASS EVIDENCE=$evidence"
}
