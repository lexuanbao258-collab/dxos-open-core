$ErrorActionPreference = 'Stop'
$seedScript = Join-Path $PSScriptRoot 'helpdesk-local-actor-seed.ps1'
$subjects = @('synthetic-employee','synthetic-coordinator','synthetic-worker')
$issuer = 'http://127.0.0.1:8182/realms/tricore'
$realm = @{ realm='tricore'; users=@(
    @{ username='employee-demo'; id=$subjects[0]; firstName='Employee'; lastName='Demo' },
    @{ username='coordinator-demo'; id=$subjects[1]; firstName='Coordinator'; lastName='Demo' },
    @{ username='worker-demo'; id=$subjects[2]; firstName='Worker'; lastName='Demo' }
) }
$file = Join-Path ([IO.Path]::GetTempPath()) ('dxos-demo-identities-' + [Guid]::NewGuid().ToString('N') + '.json')
function Assert([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Reject([scriptblock]$Action) {
    $rejected = $false
    try { $null = & $Action } catch { $rejected = $true }
    Assert $rejected 'Expected provisioning rejection'
}
try {
    $realm | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $file -Encoding UTF8
    $bare = @(& $seedScript -KeycloakIssuer $issuer -Subjects $subjects)
    $named = @(& $seedScript -KeycloakIssuer $issuer -Subjects $subjects -TrustedDemoRealmFile $file)
    Assert ($named.Count -eq 6) 'Expected three memberships and three actors'
    foreach ($name in @('Employee Demo','Coordinator Demo','Worker Demo')) {
        Assert (($named -join "`n").Contains("'$name'")) 'Missing trusted display name'
    }
    Write-Output 'PASS verified-name-present'
    Assert (@($bare | Where-Object { $_ -match ',NULL\);$' }).Count -eq 3) 'Missing NULL fallback'
    Write-Output 'PASS null-fallback'
    $withoutNames = @($named | ForEach-Object { $_ -replace "'(Employee|Coordinator|Worker) Demo'(?=\);$)", 'NULL' })
    Assert (($withoutNames -join "`n") -ceq ($bare -join "`n")) 'Display names changed canonical actor, membership or authority flags'
    Write-Output 'PASS canonical-subject-and-authority-unchanged'
    Reject { & $seedScript -KeycloakIssuer $issuer -Subjects @('wrong-subject',$subjects[1],$subjects[2]) -TrustedDemoRealmFile $file }
    Write-Output 'PASS mismatched-subject-rejected'
    Reject { & $seedScript -KeycloakIssuer ($issuer + '-other') -Subjects $subjects -TrustedDemoRealmFile $file }
    Write-Output 'PASS mismatched-realm-rejected'
    $realm.users[0].firstName = "Emplo'yee"
    $realm | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $file -Encoding UTF8
    $escaped = @(& $seedScript -KeycloakIssuer $issuer -Subjects $subjects -TrustedDemoRealmFile $file)
    Assert (($escaped -join "`n").Contains("'Emplo''yee Demo'")) 'Display name must be SQL escaped'
    Write-Output 'PASS name-sql-escaped'
    $realm.users += $realm.users[0]
    $realm | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $file -Encoding UTF8
    Reject { & $seedScript -KeycloakIssuer $issuer -Subjects $subjects -TrustedDemoRealmFile $file }
    Write-Output 'PASS ambiguous-user-rejected'
} finally { if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file } }
