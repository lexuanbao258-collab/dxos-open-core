param([switch]$ExecuteDisposable, [string]$JavaHome = 'C:\Program Files\Java\jdk-21.0.11')
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backend = Join-Path $root 'backend'
$container = 'dxos-b06-' + [Guid]::NewGuid().ToString('N')
$success = $false
$started = $false
$locationPushed = $false
$keys = @('JAVA_HOME','Path','HELPDESK_DISPOSABLE_URL','HELPDESK_DISPOSABLE_PASSWORD','WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE')
$saved = @{}
foreach ($name in $keys) { $saved[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    if (-not $ExecuteDisposable) { throw 'Use -ExecuteDisposable to approve the local ephemeral Docker database.' }
    foreach ($role in @('EMPLOYEE','COORDINATOR','WORKER','FOREIGN')) {
        $key = 'B06_' + $role + '_ACCESS_TOKEN'
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key,'Process'))) {
            throw ('Missing Keycloak test access token: ' + $key)
        }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe') -PathType Leaf)) { throw 'JDK 21 missing' }
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker CLI missing' }
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;$env:Path"
    $head = (& git -C $root rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -notmatch '^[0-9a-f]{40}$') { throw 'Unverifiable HEAD' }
    Write-Output ('B06_HEAD=' + $head)
    Write-Output 'B06_DB=jdbc:postgresql://127.0.0.1:55439/helpdesk_disposable_test'
    $pw = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $dockerArgs = @('run','--rm','-d','--name',$container,'-p','127.0.0.1:55439:5432',
        '-e','POSTGRES_USER=helpdesk_test','-e','POSTGRES_DB=helpdesk_disposable_test',
        '-e',('POSTGRES_PASSWORD=' + $pw),'postgres:18.6-alpine')
    $id = (& docker @dockerArgs 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $id -notmatch '^[0-9a-f]{64}$') { throw 'Disposable PG18 launch failed' }
    $started = $true
    $ready = $false
    for ($i=0; $i -lt 60; $i++) {
        $null = & docker exec $container pg_isready -U helpdesk_test -d helpdesk_disposable_test 2>&1
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Disposable PG18 not ready' }
    $env:HELPDESK_DISPOSABLE_URL='jdbc:postgresql://127.0.0.1:55439/helpdesk_disposable_test'
    $env:HELPDESK_DISPOSABLE_PASSWORD=$pw
    $env:WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE='true'
    Push-Location $backend
    $locationPushed = $true
    $argsGradle = @('--no-daemon','--no-configuration-cache','--rerun-tasks','test',
        '--tests','com.tricore.dxos.infrastructure.workflow.HelpdeskConfigurationTest',
        '--tests','com.tricore.dxos.infrastructure.workflow.HelpdeskHttpPostgresTest')
    & '.\gradlew.bat' @argsGradle
    if ($LASTEXITCODE -ne 0) { throw 'Gradle focused tests failed' }
    foreach ($suite in @(@{Name='HelpdeskConfigurationTest';Tests=2},@{Name='HelpdeskHttpPostgresTest';Tests=31})) {
        $prefix='com.tricore.dxos.infrastructure.workflow.'
        $xmlFile=Join-Path $backend ('build\test-results\test\TEST-'+$prefix+$suite.Name+'.xml')
        if (-not (Test-Path -LiteralPath $xmlFile -PathType Leaf)) { throw 'JUnit report absent' }
        [xml]$doc=Get-Content -LiteralPath $xmlFile -Raw
        $s=$doc.testsuite
        if ([int]$s.tests -ne $suite.Tests -or [int]$s.failures -ne 0 -or [int]$s.errors -ne 0 -or [int]$s.skipped -ne 0) {
            throw ('JUnit verification failed: '+$suite.Name)
        }
        Write-Output ($suite.Name + '=' + $s.tests + '_PASS_0_FAIL_0_SKIP')
    }
    Write-Output 'B06_RESULT=PASS'
    $success = $true
} catch {
    Write-Output 'B06_RESULT=FAIL'
    Write-Output ('B06_FAILURE_TYPE=' + $_.Exception.GetType().Name)
} finally {
    if ($locationPushed) { Pop-Location }
    foreach ($name in $keys) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') }
    # Random container name owned only by this launcher; never touch shared containers.
    $null = & docker rm -f $container 2>&1
    if ($started) { Write-Output 'B06_EPHEMERAL_CONTAINER=CLEANED' }
}
if ($success) { exit 0 } else { exit 1 }
