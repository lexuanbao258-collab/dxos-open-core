# Windows PowerShell 5.1+; DRY RUN by default. Never put credentials in CLI arguments.
[CmdletBinding()]
param(
  [switch]$Execute,
  [switch]$OperatorApproved,
  [string]$ApprovedCommit = '',
  [ValidateRange(2,15)][int]$MaxMinutes = 5,
  [ValidateRange(2,32)][int]$MaxStagingMiB = 8
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'backend'
$gradle = Join-Path $backend 'gradlew.bat'
$originalPrefix = $null
$originalStaging = $null
$runPrefix = $null
$staging = $null
$privateLogs = $null
$process = $null
$passed = $false

function Require([bool]$condition, [string]$reason) {
  if (-not $condition) { throw ('BLOCKED: ' + $reason) }
}
function New-PrivateFolder([string]$parent, [string]$label) {
  Require ([IO.Path]::IsPathRooted($parent)) 'staging/log root must be absolute'
  Require (Test-Path -LiteralPath $parent -PathType Container) 'approved staging/log parent is missing'
  $item = Get-Item -LiteralPath $parent -Force
  Require (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'reparse-point root is not permitted'
  $folder = Join-Path $parent ($label + '-' + [guid]::NewGuid().ToString('N'))
  [void](New-Item -Path $folder -ItemType Directory -ErrorAction Stop)
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
  & icacls.exe $folder /inheritance:r /grant:r ($identity + ':(OI)(CI)F') 'SYSTEM:(OI)(CI)F' *> $null
  Require ($LASTEXITCODE -eq 0) 'private directory ACL setup failed'
  return $folder
}
function Get-UsedBytes([string]$folder) {
  $bytes = [long]0
  foreach ($file in @(Get-ChildItem -LiteralPath $folder -File -Recurse -Force -ErrorAction Stop)) {
    $bytes += [long]$file.Length
  }
  return $bytes
}
function Get-RunWorkers([string]$namespace) {
  # Never display command lines (they contain test keys and the namespace).
  return @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction Stop |
    Where-Object { $_.CommandLine -and $_.CommandLine.Contains('SupabaseProcessWorker') -and
      $_.CommandLine.Contains($namespace) })
}
function Stop-Tree($p) {
  if ($null -ne $p -and -not $p.HasExited) {
    & taskkill.exe /PID $p.Id /T /F *> $null
  }
}
function Assert-PrivateBucket([uri]$restUri) {
  # Independent read-only bucket metadata preflight; no objects are modified here.
  Add-Type -AssemblyName System.Net.Http
  $handler = New-Object System.Net.Http.HttpClientHandler
  $handler.AllowAutoRedirect = $false
  $http = [System.Net.Http.HttpClient]::new($handler)
  $http.Timeout = [TimeSpan]::FromSeconds(15)
  $preflightDeadline = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(15))
  $request = $null; $response = $null; $stream = $null; $buffer = $null
  try {
    $target = [uri]($restUri.AbsoluteUri.TrimEnd('/') + '/bucket/dxos-objects')
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $target)
    [void]$request.Headers.TryAddWithoutValidation('apikey', $env:SUPABASE_STORAGE_API_KEY)
    if ($env:SUPABASE_STORAGE_API_KEY_MODE -eq 'service-role') {
      $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $env:SUPABASE_STORAGE_API_KEY)
    }
    $response = $http.SendAsync(
      $request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead,
      $preflightDeadline.Token
    ).GetAwaiter().GetResult()
    Require ($response.StatusCode -eq [System.Net.HttpStatusCode]::OK) 'bucket response not OK'
    Require (($null -eq $response.Content.Headers.ContentLength) -or
      ($response.Content.Headers.ContentLength -le 65536)) 'bucket response too large'
    $stream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
    $buffer = New-Object System.IO.MemoryStream
    $block = New-Object byte[] 4096
    while (($n = $stream.ReadAsync($block, 0, $block.Length,
      $preflightDeadline.Token).GetAwaiter().GetResult()) -gt 0) {
      Require (($buffer.Length + $n) -le 65536) 'bucket response too large'
      $buffer.Write($block, 0, $n)
    }
    $doc = [Text.Encoding]::UTF8.GetString($buffer.ToArray()) | ConvertFrom-Json
    Require (($doc.public -is [bool]) -and ($doc.public -eq $false)) 'private status not verified'
  } catch {
    throw 'BLOCKED: private bucket check failed; no hosted writes performed'
  } finally {
    if ($buffer) { $buffer.Dispose() }
    if ($stream) { $stream.Dispose() }
    if ($response) { $response.Dispose() }
    if ($request) { $request.Dispose() }
    $http.Dispose(); $handler.Dispose(); $preflightDeadline.Dispose()
  }
}

if (-not $Execute) {
  Write-Host 'DRY_RUN_ONLY: no credentials, network calls or hosted writes.'
  Write-Host 'Read docs/poc/issue26-hosted-runbook.md before a reviewed, approved execution.'
  return
}
try {
  # Dry-run returned before reading any private process environment.
  $originalPrefix = $env:SUPABASE_STORAGE_KEY_PREFIX
  $originalStaging = $env:SUPABASE_STORAGE_STAGING_DIRECTORY
  Require $OperatorApproved.IsPresent 'explicit operator approval required'
  Require ($ApprovedCommit -match '^[0-9a-f]{40}$') 'explicit approved source SHA required'
  Require (Test-Path -LiteralPath $gradle -PathType Leaf) 'Gradle wrapper not found'
  $commit = ((& git.exe -C $root rev-parse HEAD) | Out-String).Trim()
  Require ($LASTEXITCODE -eq 0 -and $commit -eq $ApprovedCommit) 'checkout does not match approved SHA'
  $dirty = @(& git.exe -C $root status --porcelain)
  Require ($LASTEXITCODE -eq 0 -and $dirty.Count -eq 0) 'checkout must be clean'
  Require ($env:JAVA_HOME -and
    (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe') -PathType Leaf)) 'JDK missing'
  $java = (& (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1 | Out-String)
  Require ($java -match 'version "21\.') 'JDK 21 required'

  $needed = @(
    'SUPABASE_STORAGE_REST_BASE_URL', 'SUPABASE_STORAGE_API_KEY',
    'SUPABASE_STORAGE_API_KEY_MODE', 'SUPABASE_STORAGE_BUCKET',
    'SUPABASE_STORAGE_KEY_PREFIX', 'SUPABASE_STORAGE_MAX_METADATA_BYTES',
    'SUPABASE_STORAGE_MAX_KEY_BYTES', 'SUPABASE_STORAGE_MAX_RESPONSE_BYTES',
    'SUPABASE_STORAGE_STAGING_DIRECTORY', 'SUPABASE_STORAGE_CONNECT_TIMEOUT',
    'SUPABASE_STORAGE_REQUEST_TIMEOUT', 'SUPABASE_STORAGE_EXPECTED_HOST'
  )
  $missing = @($needed | Where-Object { -not [Environment]::GetEnvironmentVariable($_, 'Process') })
  Require ($missing.Count -eq 0) 'required private runtime configuration is missing'
  Require ($env:SUPABASE_STORAGE_BUCKET -ceq 'dxos-objects') 'expected private bucket not selected'
  Require ($env:SUPABASE_STORAGE_API_KEY_MODE -in @('secret','service-role')) 'unsupported key mode'
  Require ($originalPrefix -cmatch '^[A-Za-z0-9_-]{1,40}$') 'invalid base prefix'
  $uri = [uri]$env:SUPABASE_STORAGE_REST_BASE_URL
  Require ($uri.Scheme -ceq 'https' -and $uri.Host -ceq $env:SUPABASE_STORAGE_EXPECTED_HOST -and
    $uri.AbsolutePath -match '^/storage/v1/?$' -and -not $uri.UserInfo -and
    -not $uri.Query -and -not $uri.Fragment) 'private project host not confirmed'
  $maxKey = [int]$env:SUPABASE_STORAGE_MAX_KEY_BYTES
  $maxMeta = [int]$env:SUPABASE_STORAGE_MAX_METADATA_BYTES
  $maxResponse = [int]$env:SUPABASE_STORAGE_MAX_RESPONSE_BYTES
  Require ($maxMeta -ge 4096 -and $maxMeta -le 65536 -and
    $maxResponse -gt $maxMeta -and $maxResponse -le 1048576) 'response/metadata limits require approval'
  $runPrefix = $originalPrefix + '_verify_' + [guid]::NewGuid().ToString('N')
  Require (($runPrefix.Length + 100) -le $maxKey) 'physical key byte limit too low'
  $staging = New-PrivateFolder $originalStaging 'dxos-hosted-stage'
  $privateLogs = New-PrivateFolder $env:TEMP 'dxos-hosted-evidence'
  $stdout = Join-Path $privateLogs 'gradle.stdout.private.txt'
  $stderr = Join-Path $privateLogs 'gradle.stderr.private.txt'
  @(
    ('SOURCE_SHA=' + $commit), 'BUCKET=dxos-objects', ('RUN_PREFIX=' + $runPrefix),
    ('STAGING=' + $staging), 'REPORT=backend/build/test-results/supabaseIntegrationTest',
    'NOTE=reconcile only exact RUN_PREFIX_it_<UUID>/v1/ remote keys; never sweep base prefix'
  ) | Set-Content -LiteralPath (Join-Path $privateLogs 'reconciliation.private.txt') -Encoding UTF8

  $env:SUPABASE_STORAGE_KEY_PREFIX = $runPrefix
  $env:SUPABASE_STORAGE_STAGING_DIRECTORY = $staging
  # All hosted fixtures fit 64 KiB; cap test upload size to 128 KiB.
  $env:SUPABASE_STORAGE_MAX_OBJECT_BYTES = '131072'
  Require ((Get-RunWorkers $runPrefix).Count -eq 0) 'run namespace already has active worker'
  Assert-PrivateBucket $uri

  $started = [DateTime]::UtcNow
  $command = '""' + $gradle + '" supabaseIntegrationTest --rerun-tasks --no-build-cache --no-daemon --max-workers=1 --console=plain"'
  $launch = @{
    FilePath = $env:ComSpec; ArgumentList = @('/d','/s','/c',$command);
    WorkingDirectory = $backend; PassThru = $true; WindowStyle = 'Hidden';
    RedirectStandardOutput = $stdout; RedirectStandardError = $stderr
  }
  $process = Start-Process @launch
  $maxElapsed = [TimeSpan]::FromMinutes($MaxMinutes)
  $maxStaging = [long]$MaxStagingMiB * 1048576
  while (-not $process.WaitForExit(1000)) {
    if (([DateTime]::UtcNow - $started) -ge $maxElapsed -or
      (Get-UsedBytes $staging) -gt $maxStaging -or
      ((Test-Path -LiteralPath $stdout) -and ((Get-Item -LiteralPath $stdout).Length -gt 16777216)) -or
      ((Test-Path -LiteralPath $stderr) -and ((Get-Item -LiteralPath $stderr).Length -gt 16777216))) {
      Stop-Tree $process
      throw 'BLOCKED: watchdog/log/staging limit exceeded; reconcile isolated namespace'
    }
  }
  Require ($process.ExitCode -eq 0) 'Gradle hosted execution failed; inspect local private logs'

  $results = Join-Path $backend 'build\test-results\supabaseIntegrationTest'
  Require (Test-Path -LiteralPath $results -PathType Container) 'JUnit report missing'
  $reports = @(Get-ChildItem -LiteralPath $results -Filter 'TEST-*.xml' -File |
    Where-Object { $_.LastWriteTimeUtc -ge $started.AddSeconds(-2) })
  Require ($reports.Count -gt 0) 'fresh JUnit reports missing'
  $count = 0; $bad = 0; $skips = 0
  foreach ($file in $reports) {
    [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw
    $count += [int]$xml.testsuite.tests
    $bad += [int]$xml.testsuite.failures + [int]$xml.testsuite.errors
    $skips += [int]$xml.testsuite.skipped
  }
  Require ($count -eq 19 -and $bad -eq 0 -and $skips -eq 0) '19 hosted tests did not all pass'
  Require ((Get-RunWorkers $runPrefix).Count -eq 0) 'run-specific JVM is still active'
  Require ((Get-UsedBytes $staging) -eq 0) 'staging leftovers require reconciliation'
  $passed = $true
  Write-Host 'HOSTED_GATE_RESULT=PASS tests=19 failures=0 skipped=0'
  Write-Host 'Evidence and namespace manifest remain in a protected local folder.'
} catch {
  # No credentials, provider errors, response bodies or raw process logs printed.
  Write-Host 'HOSTED_GATE_RESULT=BLOCKED. Review protected local logs and reconcile run namespace.'
  throw 'Hosted verification NOT accepted. Never merge PR #35 on this result.'
} finally {
  if ($process) { Stop-Tree $process; $process.Dispose() }
  if ($runPrefix) {
    try {
      if ((Get-RunWorkers $runPrefix).Count -ne 0) {
        $passed = $false
        Write-Host 'HOSTED_GATE_WARNING=run-specific worker remains; preserve namespace and staging.'
      }
    } catch { $passed = $false }
  }
  if ($passed -and $staging -and (Test-Path -LiteralPath $staging)) {
    if ((Get-UsedBytes $staging) -eq 0) {
      Remove-Item -LiteralPath $staging -Recurse -Force
    }
  }
  if ($null -ne $originalPrefix) { $env:SUPABASE_STORAGE_KEY_PREFIX = $originalPrefix }
  if ($null -ne $originalStaging) { $env:SUPABASE_STORAGE_STAGING_DIRECTORY = $originalStaging }
}
