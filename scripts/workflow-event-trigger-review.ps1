param([string[]]$HelperOid = @(), [string[]]$HelperSignature = @())
$ErrorActionPreference = 'Stop'
$required = @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','PGSSLROOTCERT','LOCALAPPDATA')
$missing = @($required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) })
if ($missing.Count) {
    [Console]::Error.WriteLine('Missing environment variables: ' + ($missing -join ', '))
    exit 1
}
$previousDirectory = $env:WORKFLOW_EVENT_REVIEW_DIR
$previousHelpers = $env:WORKFLOW_EVENT_REVIEW_HELPER_OIDS
$previousSignatures = $env:WORKFLOW_EVENT_REVIEW_HELPER_SIGNATURES
$reviewExit = 1
try {
    if (($HelperOid.Count + $HelperSignature.Count) -gt 32) { throw 'Invalid helper identifiers' }
    foreach ($value in $HelperOid) {
        if ($value -notmatch '^[0-9]+$' -or [uint64]$value -lt 1 -or [uint64]$value -gt 4294967295) {
            throw 'Invalid helper identifiers'
        }
    }
    foreach ($value in $HelperSignature) {
        if ([string]::IsNullOrWhiteSpace($value) -or $value.Length -gt 1024 -or $value -match '[\r\n\x00]') {
            throw 'Invalid helper identifiers'
        }
    }
    $baseDirectory = Join-Path $env:LOCALAPPDATA 'TRICORE\wf25-event-review'
    New-Item -ItemType Directory -Path $baseDirectory -Force | Out-Null
    $reviewDirectory = Join-Path $baseDirectory ([Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $reviewDirectory | Out-Null
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $security = [Security.AccessControl.DirectorySecurity]::new()
    $security.SetOwner($identity.User)
    $security.SetAccessRuleProtection($true, $false)
    foreach ($principal in @($identity.User, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $rule = [Security.AccessControl.FileSystemAccessRule]::new($principal,
            [Security.AccessControl.FileSystemRights]::FullControl,
            [Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit',
            [Security.AccessControl.PropagationFlags]::None,
            [Security.AccessControl.AccessControlType]::Allow)
        $security.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $reviewDirectory -AclObject $security
    $actual = Get-Acl -LiteralPath $reviewDirectory
    if (-not $actual.AreAccessRulesProtected -or
        $actual.GetOwner([Security.Principal.SecurityIdentifier]).Value -ne $identity.User.Value) {
        throw 'Protected directory required'
    }
    foreach ($rule in $actual.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])) {
        if ($rule.AccessControlType -eq [Security.AccessControl.AccessControlType]::Allow -and
            $rule.IdentityReference.Value -notin @($identity.User.Value, 'S-1-5-18')) {
            throw 'Protected directory required'
        }
    }
    $env:WORKFLOW_EVENT_REVIEW_DIR = $reviewDirectory
    $env:WORKFLOW_EVENT_REVIEW_HELPER_OIDS = ($HelperOid | Sort-Object -Unique) -join ','
    $env:WORKFLOW_EVENT_REVIEW_HELPER_SIGNATURES = $HelperSignature -join "`n"
    Push-Location (Join-Path $PSScriptRoot '../backend')
    try {
        & './gradlew.bat' workflowEventTriggerReview --no-daemon
        $reviewExit = $LASTEXITCODE
    } finally { Pop-Location }
    if ($reviewExit -eq 0) {
        Write-Output ('SHAREABLE_METADATA=' + (Join-Path $reviewDirectory 'shareable-metadata.json'))
        Write-Output ('PRIVATE_LOCAL_REVIEW=' + (Join-Path $reviewDirectory 'private-function-review.json'))
        Write-Output 'Share only shareable-metadata.json; deployed bodies/configuration remain private.'
    }
} catch {
    [Console]::Error.WriteLine('EVENT_REVIEW_FAIL phase=launcher error=protected_launcher_failed')
    $reviewExit = 1
} finally {
    $env:WORKFLOW_EVENT_REVIEW_DIR = $previousDirectory
    $env:WORKFLOW_EVENT_REVIEW_HELPER_OIDS = $previousHelpers
    $env:WORKFLOW_EVENT_REVIEW_HELPER_SIGNATURES = $previousSignatures
}
exit $reviewExit
