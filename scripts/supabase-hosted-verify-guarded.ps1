# Windows PowerShell 5.1+; DRY RUN by default. Never put credentials in CLI arguments.
[CmdletBinding()]
param(
  [switch]$Execute,
  [switch]$OperatorApproved,
  [switch]$SelfTest,
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
$originalMaxObject = $null
$script:trackedPids = New-Object 'System.Collections.Generic.HashSet[int]'
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
# Tracks all descendants discovered while their parent is alive, not only named workers.
# A failed CIM query is a BLOCKED outcome; process-tree polling is best effort,
# and must be supplemented by an operator-controlled external watchdog.
function Update-OwnedPids([object[]]$snapshot) {
  $progress = $true
  while ($progress) {
    $progress = $false
    foreach ($entry in $snapshot) {
      $parent = [int]$entry.ParentProcessId
      $id = [int]$entry.ProcessId
      if ($script:trackedPids.Contains($parent) -and $script:trackedPids.Add($id)) {
        $progress = $true
      }
    }
  }
}
function Get-LiveOwnedProcesses {
  $snapshot = @(Get-CimInstance Win32_Process -ErrorAction Stop)
  Update-OwnedPids $snapshot
  return @($snapshot | Where-Object { $script:trackedPids.Contains([int]$_.ProcessId) })
}
function Stop-OwnedTree {
  # Only terminate PIDs discovered within this invocation's parent chain.
  # A terminated child does not prove that its remote mutation was rolled back.
  foreach ($entry in @(Get-LiveOwnedProcesses)) {
    & taskkill.exe /PID ([int]$entry.ProcessId) /T /F *> $null
  }
}
function Read-HostedJUnit([string]$directory, [datetime]$started) {
  Require (Test-Path -LiteralPath $directory -PathType Container) 'JUnit report missing'
  $reports = @(Get-ChildItem -LiteralPath $directory -Filter 'TEST-*.xml' -File -ErrorAction Stop)
  Require ($reports.Count -eq 1) 'expected exactly one hosted JUnit suite'
  $report = $reports[0]
  Require ($report.Name -ceq 'TEST-com.tricore.dxos.infrastructure.storage.supabase.SupabaseObjectStorageIntegrationTest.xml') 'unexpected JUnit suite'
  Require ($report.LastWriteTimeUtc -ge $started) 'stale JUnit report'
  [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw -ErrorAction Stop
  Require ($null -ne $xml.testsuite -and $xml.testsuite.name -ceq 'com.tricore.dxos.infrastructure.storage.supabase.SupabaseObjectStorageIntegrationTest') 'wrong JUnit suite identity'
  Require ([int]$xml.testsuite.tests -eq 19 -and [int]$xml.testsuite.failures -eq 0 -and
    [int]$xml.testsuite.errors -eq 0 -and [int]$xml.testsuite.skipped -eq 0) 'hosted JUnit result not 19/19'
  Require (@($xml.testsuite.testcase).Count -eq 19) 'hosted JUnit testcase count mismatch'
  return (Get-FileHash -LiteralPath $report.FullName -Algorithm SHA256).Hash
}
function Test-FinalGate([bool]$candidate, [int]$owned, [int]$workers, [long]$stagingBytes, [bool]$cleaned) {
  return ($candidate -and $owned -eq 0 -and $workers -eq 0 -and $stagingBytes -eq 0 -and $cleaned)
}
function Invoke-OfflineSelfTest {
  # Uses only temporary synthetic JUnit XML and in-memory process snapshots.
  # No private configuration is read; no network request/remote mutation occurs.
  $tmp = Join-Path ([IO.Path]::GetTempPath()) ('dxos-runner-selftest-' + [guid]::NewGuid().ToString('N'))
  [void](New-Item -ItemType Directory -Path $tmp -ErrorAction Stop)
  try {
    $script:trackedPids.Clear()
    [void]$script:trackedPids.Add(100001)
    $snapshot = @(
      [pscustomobject]@{ ProcessId=100002; ParentProcessId=100001 },
      [pscustomobject]@{ ProcessId=100003; ParentProcessId=100002 }
    )
    Update-OwnedPids $snapshot
    Require ($script:trackedPids.Contains(100003)) 'nested descendant tracking failed'
    Require (-not (Test-FinalGate $true 0 1 0 $true)) 'late worker orphan must block'
    Require (-not (Test-FinalGate $true 1 0 0 $true)) 'detached owned process must block'
    Require (-not (Test-FinalGate $true 0 0 0 $false)) 'cleanup error must block'
    Require (Test-FinalGate $true 0 0 0 $true) 'clean finish must pass'
    $junitName = 'TEST-com.tricore.dxos.infrastructure.storage.supabase.SupabaseObjectStorageIntegrationTest.xml'
    $junitPath = Join-Path $tmp $junitName
    $cases = (1..19 | ForEach-Object { '<testcase classname="com.tricore.dxos.infrastructure.storage.supabase.SupabaseObjectStorageIntegrationTest" name="offline-fixture"/>' }) -join ''
    $xml = '<testsuite name="com.tricore.dxos.infrastructure.storage.supabase.SupabaseObjectStorageIntegrationTest" tests="19" errors="0" failures="0" skipped="0">' + $cases + '</testsuite>'
    [IO.File]::WriteAllText($junitPath, $xml, [Text.Encoding]::UTF8)
    $hash = Read-HostedJUnit $tmp ([datetime]::UtcNow.AddMinutes(-1))
    Require ($hash.Length -eq 64) 'JUnit hash missing'
    $unexpectedWasBlocked = $false
    [IO.File]::WriteAllText((Join-Path $tmp 'TEST-unexpected.xml'), '<testsuite tests="0"/>')
    try { [void](Read-HostedJUnit $tmp ([datetime]::UtcNow.AddMinutes(-1))) }
    catch { $unexpectedWasBlocked = $true }
    Require $unexpectedWasBlocked 'extra JUnit suite must block'
    Write-Host 'OFFLINE_SELFTEST=PASS: descendant tracking, late orphan, cleanup and synthetic JUnit gates.'
  } finally {
    $script:trackedPids.Clear()
    Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
  }
}
function Get-JavaVersion([string]$executable) {
  # Java writes -version to stderr; direct native redirection is error-prone
  # under Windows PowerShell 5.1 with ErrorActionPreference=Stop.
  $start = New-Object System.Diagnostics.ProcessStartInfo
  $start.FileName = $executable
  $start.Arguments = '-version'
  $start.UseShellExecute = $false
  $start.CreateNoWindow = $true
  $start.RedirectStandardError = $true
  $start.RedirectStandardOutput = $true
  $probe = New-Object System.Diagnostics.Process
  $probe.StartInfo = $start
  try {
    Require ($probe.Start()) 'JDK version process failed to start'
    $err = $probe.StandardError.ReadToEnd()
    $out = $probe.StandardOutput.ReadToEnd()
    if (-not $probe.WaitForExit(10000)) {
      $probe.Kill()
      throw 'BLOCKED: Java version process timed out'
    }
    Require ($probe.ExitCode -eq 0) 'Java version process failed'
    return ($err + $out)
  } finally { $probe.Dispose() }
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

if ($SelfTest) {
  Require (-not $Execute) 'offline self-test cannot execute hosted tests'
  Invoke-OfflineSelfTest
  return
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
  $originalMaxObject = $env:SUPABASE_STORAGE_MAX_OBJECT_BYTES
  Require $OperatorApproved.IsPresent 'explicit operator approval required'
  Require ($ApprovedCommit -match '^[0-9a-f]{40}$') 'explicit approved source SHA required'
  Require (Test-Path -LiteralPath $gradle -PathType Leaf) 'Gradle wrapper not found'
  $commit = ((& git.exe -C $root rev-parse HEAD) | Out-String).Trim()
  Require ($LASTEXITCODE -eq 0 -and $commit -eq $ApprovedCommit) 'checkout does not match approved SHA'
  $dirty = @(& git.exe -C $root status --porcelain)
  Require ($LASTEXITCODE -eq 0 -and $dirty.Count -eq 0) 'checkout must be clean'
  Require ($env:JAVA_HOME -and
    (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe') -PathType Leaf)) 'JDK missing'
  $java = Get-JavaVersion (Join-Path $env:JAVA_HOME 'bin\java.exe')
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
  # Never silently count old result files from a previous execution.
  $results = Join-Path $backend 'build\test-results\supabaseIntegrationTest'
  if (Test-Path -LiteralPath $results -PathType Container) {
    Require (@(Get-ChildItem -LiteralPath $results -Filter 'TEST-*.xml' -File).Count -eq 0) 'old hosted XML exists; archive and clear it manually before execution'
  }
  Assert-PrivateBucket $uri

  $started = [DateTime]::UtcNow
  $command = '""' + $gradle + '" supabaseIntegrationTest --rerun-tasks --no-build-cache --no-daemon --max-workers=1 --console=plain"'
  $launch = @{
    FilePath = $env:ComSpec; ArgumentList = @('/d','/s','/c',$command);
    WorkingDirectory = $backend; PassThru = $true; WindowStyle = 'Hidden';
    RedirectStandardOutput = $stdout; RedirectStandardError = $stderr
  }
  $process = Start-Process @launch
  [void]$script:trackedPids.Add([int]$process.Id)
  $maxElapsed = [TimeSpan]::FromMinutes($MaxMinutes)
  $maxStaging = [long]$MaxStagingMiB * 1048576
  do {
    $owned = @(Get-LiveOwnedProcesses)
    $exited = $process.WaitForExit(1000)
    if (([DateTime]::UtcNow - $started) -ge $maxElapsed -or
      (Get-UsedBytes $staging) -gt $maxStaging -or
      ((Test-Path -LiteralPath $stdout) -and ((Get-Item -LiteralPath $stdout).Length -gt 16777216)) -or
      ((Test-Path -LiteralPath $stderr) -and ((Get-Item -LiteralPath $stderr).Length -gt 16777216))) {
      Stop-OwnedTree
      throw 'BLOCKED: watchdog/log/staging limit exceeded; reconcile isolated namespace'
    }
  } while (-not $exited)
  Require ($process.ExitCode -eq 0) 'Gradle hosted execution failed; inspect local private logs'

  $reportHash = Read-HostedJUnit $results $started
  $finalOwned = @(Get-LiveOwnedProcesses)
  Require (Test-FinalGate $true $finalOwned.Count (Get-RunWorkers $runPrefix).Count (Get-UsedBytes $staging) $true) 'worker or staging leftovers require reconciliation'
  Add-Content -LiteralPath (Join-Path $privateLogs 'reconciliation.private.txt') -Value ('JUNIT_XML_SHA256=' + $reportHash)
  # Final PASS is emitted after the finally cleanup and a second process check.
  $passed = $true
} catch {
  # No credentials, provider errors, response bodies or raw process logs printed.
  Write-Host 'HOSTED_GATE_RESULT=BLOCKED. Review protected local logs and reconcile run namespace.'
  throw 'Hosted verification NOT accepted. Never merge PR #35 on this result.'
} finally {
  $cleanupOk = $true
  if ($process) {
    try {
      # Never kill a healthy process after success. On error/timeout, only
      # terminate descendants discovered under this runner's process tree.
      if (-not $passed) { Stop-OwnedTree }
    } catch { $cleanupOk = $false }
    $process.Dispose()
  }
  if ($runPrefix) {
    try {
      $live = @(Get-LiveOwnedProcesses)
      $workers = @(Get-RunWorkers $runPrefix)
      if ($live.Count -gt 0 -or $workers.Count -gt 0) { $cleanupOk = $false }
    } catch { $cleanupOk = $false }
  }
  if ($passed -and $cleanupOk -and $staging -and (Test-Path -LiteralPath $staging)) {
    try {
      if ((Get-UsedBytes $staging) -ne 0) { $cleanupOk = $false }
      else {
        Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction Stop
        if (Test-Path -LiteralPath $staging) { $cleanupOk = $false }
      }
    } catch { $cleanupOk = $false }
  }
  if ($null -ne $originalPrefix) { $env:SUPABASE_STORAGE_KEY_PREFIX = $originalPrefix }
  if ($null -ne $originalStaging) { $env:SUPABASE_STORAGE_STAGING_DIRECTORY = $originalStaging }
  $env:SUPABASE_STORAGE_MAX_OBJECT_BYTES = $originalMaxObject
  if (-not $cleanupOk) {
    $passed = $false
    Write-Host 'HOSTED_GATE_WARNING=final cleanup/process verification failed; preserve namespace.'
  }
}
Require $passed 'final process, report or cleanup gate not satisfied'
Write-Host 'HOSTED_GATE_RESULT=PASS tests=19 failures=0 skipped=0'
Write-Host 'Evidence and namespace manifest remain in a protected local folder.'
