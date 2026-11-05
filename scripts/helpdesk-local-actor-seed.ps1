# SPDX-FileCopyrightText: 2026 TRICORE contributors
#
# SPDX-License-Identifier: Apache-2.0

param(
    [Parameter(Mandatory)][string]$KeycloakIssuer,
    [Parameter(Mandatory)][string[]]$Subjects,
    [ValidatePattern('(?-i)\A[a-z][a-z0-9_]{0,62}\z')][string]$Schema = 'helpdesk_demo',
    [ValidatePattern('(?-i)\A[a-z0-9_-]{1,63}\z')][string]$Tenant = 'demo',
    [string]$TrustedDemoRealmFile
)
$ErrorActionPreference = 'Stop'
$uri = $null
if (-not [Uri]::TryCreate($KeycloakIssuer, [UriKind]::Absolute, [ref]$uri) -or
    $uri.Scheme -notin @('http','https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or
    $Subjects.Count -ne 3 -or
    @($Subjects | Where-Object { [string]::IsNullOrWhiteSpace($_) -or $_ -match '[\x00-\x1f\x7f]' }).Count -gt 0 -or
    @($Subjects | Select-Object -Unique).Count -ne 3) {
    throw 'Supply the exact issuer and three distinct user sub values'
}
$names = @($null, $null, $null)
if ($TrustedDemoRealmFile) {
    # Operator supplies the original trusted local realm provisioning file, never a Portal payload.
    try { $realm = Get-Content -LiteralPath $TrustedDemoRealmFile -Raw | ConvertFrom-Json }
    catch { throw 'Cannot read trusted demo realm file; contents suppressed' }
    if (-not $uri.IsLoopback -or $realm.realm -isnot [string] -or
        [string]::IsNullOrWhiteSpace($realm.realm) -or
        $uri.AbsolutePath -cne ('/realms/' + [Uri]::EscapeDataString($realm.realm))) {
        throw 'Trusted demo realm must match the exact local issuer realm'
    }
    $usernames = @('employee-demo','coordinator-demo','worker-demo')
    for ($i=0; $i -lt 3; $i++) {
        $users = @($realm.users | Where-Object { $_.username -ceq $usernames[$i] })
        if ($users.Count -ne 1 -or $users[0].id -isnot [string] -or
            $users[0].id -cne $Subjects[$i] -or
            $users[0].firstName -isnot [string] -or $users[0].lastName -isnot [string] -or
            [string]::IsNullOrWhiteSpace($users[0].firstName) -or [string]::IsNullOrWhiteSpace($users[0].lastName)) {
            throw 'Trusted demo identity must have one matching username, exact subject and supplied name'
        }
        $names[$i] = $users[0].firstName.Trim() + ' ' + $users[0].lastName.Trim()
        if ($names[$i].Length -gt 200 -or $names[$i] -match '[\x00-\x1f\x7f]') {
            throw 'Invalid trusted demo display name'
        }
    }
}
function Encode([string]$Value) {
    [Convert]::ToBase64String([Text.UTF8Encoding]::new($false,$true).GetBytes($Value)).TrimEnd('=').Replace('+','-').Replace('/','_')
}
for ($i=0; $i -lt 3; $i++) {
    $actor = 'oidc:' + (Encode $KeycloakIssuer) + '.' + (Encode $Subjects[$i])
    $flags = @('true,false,false','false,true,false','false,false,true')[$i]
    $nameSql = if ($null -eq $names[$i]) { 'NULL' } else { "'" + $names[$i].Replace("'", "''") + "'" }
    "INSERT INTO $Schema.tenant_memberships VALUES ('$Tenant','$actor',true);"
    "INSERT INTO $Schema.helpdesk_actors (tenant_id,actor_id,submitter,coordinator,worker,display_name) VALUES ('$Tenant','$actor',$flags,$nameSql);"
}
