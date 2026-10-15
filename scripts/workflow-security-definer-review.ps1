param([string]$MetadataFile, [string[]]$HelperSignature = @())
$ErrorActionPreference = 'Stop'
$reviewExit = 1
$previousDirectory = $env:WORKFLOW_DEFINER_REVIEW_DIR
$previousMetadata = $env:WORKFLOW_DEFINER_METADATA_FILE
$previousHelpers = $env:WORKFLOW_DEFINER_HELPER_SIGNATURES
try {
    $required = @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','PGSSLROOTCERT','LOCALAPPDATA')
    if (@($required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) }).Count -ne 0) {
        throw 'Missing configuration'
    }
    if ($null -ne [Environment]::GetEnvironmentVariable('WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE')) {
        throw 'Local profile rejected'
    }
    if ($HelperSignature.Count -gt 32) { throw 'Too many helper signatures' }
    foreach ($signature in $HelperSignature) {
        if ([string]::IsNullOrWhiteSpace($signature) -or $signature.Length -gt 1024 -or $signature -match '[\r\n\x00]') {
            throw 'Invalid helper signature'
        }
    }
    $baseDirectory = Join-Path $env:LOCALAPPDATA 'TRICORE\wf25-definer-review'
    New-Item -ItemType Directory -Path $baseDirectory -Force | Out-Null
    $reviewDirectory = Join-Path $baseDirectory ([Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $reviewDirectory | Out-Null
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $security = [Security.AccessControl.DirectorySecurity]::new()
    $security.SetOwner($identity.User)
    $security.SetAccessRuleProtection($true, $false)
    foreach ($principal in @($identity.User, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $security.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($principal,
            [Security.AccessControl.FileSystemRights]::FullControl,
            [Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit',
            [Security.AccessControl.PropagationFlags]::None, [Security.AccessControl.AccessControlType]::Allow))
    }
    Set-Acl -LiteralPath $reviewDirectory -AclObject $security
    $actual = Get-Acl -LiteralPath $reviewDirectory
    if (-not $actual.AreAccessRulesProtected -or $actual.GetOwner([Security.Principal.SecurityIdentifier]).Value -ne $identity.User.Value) {
        throw 'Protected directory required'
    }
    foreach ($rule in $actual.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])) {
        if ($rule.AccessControlType -eq [Security.AccessControl.AccessControlType]::Allow -and
            $rule.IdentityReference.Value -notin @($identity.User.Value, 'S-1-5-18')) { throw 'Protected directory required' }
    }
    $env:WORKFLOW_DEFINER_REVIEW_DIR = $reviewDirectory
    $env:WORKFLOW_DEFINER_METADATA_FILE = if ($PSBoundParameters.ContainsKey('MetadataFile')) {
        if ([string]::IsNullOrWhiteSpace($MetadataFile)) { throw 'Metadata file required' }
        [IO.Path]::GetFullPath($MetadataFile)
    } else { $null }
    $env:WORKFLOW_DEFINER_HELPER_SIGNATURES = $HelperSignature -join "`n"
    Push-Location (Join-Path $PSScriptRoot '../backend')
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $successCount = 0
        $behaviorSeen = $false
        $failureSeen = $false
        & './gradlew.bat' workflowSecurityDefinerReview --no-daemon --no-configuration-cache --console=plain --quiet 2>&1 |
            ForEach-Object {
                $line = $_.ToString()
                if ($line -match '^SECURITY_DEFINER_CAPTURE=PASS scope=READ_ONLY_CATALOG client_tls=VERIFIED_VERIFY_FULL$') {
                    $successCount++
                } elseif ($line -match '^SECURITY_DEFINER_CAPTURE=FAIL phase=[a-z_]+$') {
                    $failureSeen = $true
                    Write-Output $line
                } elseif ($line -eq 'BEHAVIOR_REVIEW=NEEDS_REVIEW; WRITE_READINESS=BLOCKED') { $behaviorSeen = $true }
            }
        $reviewExit = $LASTEXITCODE
        if ($successCount -ne 1 -or -not $behaviorSeen -or $failureSeen -or
            -not (Test-Path -LiteralPath (Join-Path $reviewDirectory 'shareable-metadata.json') -PathType Leaf) -or
            ($PSBoundParameters.ContainsKey('MetadataFile') -and
             -not (Test-Path -LiteralPath (Join-Path $reviewDirectory 'private-function-review.json') -PathType Leaf))) { $reviewExit = 1 }
    } finally { $ErrorActionPreference = $previousPreference; Pop-Location }
    if ($reviewExit -eq 0) {
        Write-Output 'SECURITY_DEFINER_CAPTURE=PASS scope=READ_ONLY_CATALOG client_tls=VERIFIED_VERIFY_FULL'
        Write-Output 'BEHAVIOR_REVIEW=NEEDS_REVIEW; WRITE_READINESS=BLOCKED'
        Write-Output ('SHAREABLE_METADATA=' + (Join-Path $reviewDirectory 'shareable-metadata.json'))
        if ($PSBoundParameters.ContainsKey('MetadataFile')) {
            Write-Output ('PRIVATE_LOCAL_REVIEW=' + (Join-Path $reviewDirectory 'private-function-review.json'))
        }
        Write-Output 'READY_FOR_RUNTIME_WRITE=NO'
    }
} catch {
    [Console]::Error.WriteLine('SECURITY_DEFINER_CAPTURE=FAIL phase=launcher')
    $reviewExit = 1
} finally {
    $env:WORKFLOW_DEFINER_REVIEW_DIR = $previousDirectory
    $env:WORKFLOW_DEFINER_METADATA_FILE = $previousMetadata
    $env:WORKFLOW_DEFINER_HELPER_SIGNATURES = $previousHelpers
}
if ($reviewExit -ne 0) { [Console]::Error.WriteLine('SECURITY_DEFINER_CAPTURE=FAIL phase=process') }
exit $reviewExit
