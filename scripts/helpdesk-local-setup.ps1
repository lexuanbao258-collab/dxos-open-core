param(
    [Parameter(Mandatory)][ValidatePattern('(?-i)\A[a-z][a-z0-9_]{0,23}\z')][string]$RunId,
    [ValidateRange(1024,65535)][int]$Port = 55440,
    [ValidatePattern('(?-i)\A[a-z0-9_-]{1,63}\z')][string]$Tenant = 'demo',
    [string]$KeycloakIssuer,
    [string]$EmployeeSubject,
    [string]$CoordinatorSubject,
    [string]$WorkerSubject,
    [switch]$Execute,
    [string]$ConfirmDemoDatabase
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$database = 'helpdesk_disposable_' + $RunId
$container = 'dxos-helpdesk-' + $RunId
$volume = $container + '-data'
$schema = 'helpdesk_demo'
$owner = 'helpdesk_owner_' + $RunId
$runtime = 'helpdesk_runtime_' + $RunId
$image = 'postgres:17.11-alpine'

function Invoke-DemoDocker([string[]]$Arguments) {
    $output = & docker --context desktop-linux @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ('Docker operation failed: ' + $Arguments[0]) }
    return $output
}
function Sql([string]$Text, [string]$User, [string]$Password) {
    $previous = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $Password
        $output = $Text | & docker --context desktop-linux exec -i -e PGPASSWORD -e HELPDESK_RUNTIME_PASSWORD $container `
            psql -X -q -A -t -w -h 127.0.0.1 -U $User -d $database -v ON_ERROR_STOP=1 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'SQL operation failed; no SQL/credentials logged. Container retained for inspection.' }
        return $output
    } finally { $env:PGPASSWORD = $previous }
}
function Secret([string]$Name) {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrEmpty($value)) {
        $secure = Read-Host $Name -AsSecureString
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $value = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    }
    if ($value.Length -lt 24 -or $value -match '[\x00-\x1f\x7f]') { throw "$Name requires at least 24 characters and no control characters" }
    return $value
}
function Encode([string]$Value) {
    return [Convert]::ToBase64String([Text.UTF8Encoding]::new($false,$true).GetBytes($Value)).TrimEnd('=').Replace('+','-').Replace('/','_')
}

# The existing candidate accepts exactly this loopback database/runtime naming convention.
$subjects = @($EmployeeSubject, $CoordinatorSubject, $WorkerSubject)
$seedActors = -not [string]::IsNullOrEmpty($KeycloakIssuer) -or @($subjects | Where-Object { $_ }).Count -gt 0
if ($seedActors) {
    $uri = $null
    if (-not [Uri]::TryCreate($KeycloakIssuer, [UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -notin @('http','https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or
        @($subjects | Where-Object { [string]::IsNullOrWhiteSpace($_) -or $_ -match '[\x00-\x1f\x7f]' }).Count -gt 0 -or
        @($subjects | Select-Object -Unique).Count -ne 3) { throw 'Supply the exact Keycloak issuer and three distinct real user sub values, or omit all identity arguments' }
}
if ($Execute -and $ConfirmDemoDatabase -cne $database) { throw "Execution requires -ConfirmDemoDatabase $database" }
$files = @('db/migration/V3__create_workflow_persistence.sql', 'db/helpdesk/policy-schema.sql',
    'db/helpdesk/schema.sql', 'db/helpdesk/definition.sql', 'db/helpdesk/runtime-grants.sql')
$sql = foreach ($file in $files) {
    (Get-Content -LiteralPath (Join-Path $root ('backend/src/main/resources/' + $file)) -Raw).
        Replace('${flyway:defaultSchema}', $schema).Replace('${helpdesk:runtimeRole}', $runtime)
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker CLI required' }
$endpoint = (Invoke-DemoDocker -Arguments @('context','inspect','desktop-linux','--format','{{.Endpoints.docker.Host}}') | Out-String).Trim()
if ($endpoint -notmatch '^npipe:/{4}\./pipe/dockerDesktopLinuxEngine$') { throw 'Only the local Docker Desktop Linux named pipe is supported' }
if ((Invoke-DemoDocker -Arguments @('info','--format','{{.OSType}}') | Out-String).Trim() -ne 'linux') { throw 'Local Linux engine required' }
$null = Invoke-DemoDocker -Arguments @('image','inspect',$image,'--format','{{.Id}}')
if (@(Invoke-DemoDocker -Arguments @('ps','-a','--format','{{.Names}}')) -contains $container -or
    @(Invoke-DemoDocker -Arguments @('volume','ls','--format','{{.Name}}')) -contains $volume) { throw 'Demo container or volume already exists; use a new RunId. Nothing changed.' }
if (@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object LocalPort -eq $Port).Count) { throw 'Demo port is occupied' }
Write-Output "PREFLIGHT=PASS DATABASE=$database ENDPOINT=127.0.0.1:$Port CONTAINER=$container VOLUME=$volume"
if (-not $Execute) { Write-Output 'SETUP=NOT_RUN (use -Execute and -ConfirmDemoDatabase)'; return }

$savedPostgres = $env:POSTGRES_PASSWORD
$savedRuntime = $env:HELPDESK_RUNTIME_PASSWORD
try {
    $setupPassword = Secret 'HELPDESK_SETUP_PASSWORD'
    $runtimePassword = Secret 'HELPDESK_RUNTIME_PASSWORD'
    if ($setupPassword -ceq $runtimePassword) { throw 'Setup and runtime passwords must differ' }
    $env:POSTGRES_PASSWORD = $setupPassword
    $env:HELPDESK_RUNTIME_PASSWORD = $runtimePassword
    $instance = [Guid]::NewGuid().ToString('N')
    $null = Invoke-DemoDocker -Arguments @('volume','create','--label',"dxos.helpdesk.demo=$RunId",'--label',"dxos.helpdesk.instance=$instance",$volume)
    $createdVolume = (Invoke-DemoDocker -Arguments @('volume','inspect',$volume) | Out-String) | ConvertFrom-Json
    if ($createdVolume[0].Labels.'dxos.helpdesk.instance' -cne $instance) { throw 'Refusing an existing volume (concurrent setup); nothing deleted' }
    $null = Invoke-DemoDocker -Arguments @('run','-d','--pull=never','--name',$container,'--label',"dxos.helpdesk.demo=$RunId",
        '-p',"127.0.0.1:${Port}:5432",'--mount',"type=volume,source=$volume,target=/var/lib/postgresql/data",
        '-e',"POSTGRES_DB=$database",'-e','POSTGRES_USER=helpdesk_setup','-e','POSTGRES_PASSWORD',
        '-e','POSTGRES_INITDB_ARGS=--auth-host=scram-sha-256',$image,
        '-c','log_statement=none','-c','log_min_error_statement=panic')
    $created = ((Invoke-DemoDocker -Arguments @('inspect',$container) | Out-String) | ConvertFrom-Json)[0]
    $binding = @($created.HostConfig.PortBindings.'5432/tcp')
    if ($created.Config.Labels.'dxos.helpdesk.demo' -cne $RunId -or $binding.Count -ne 1 -or
        $binding[0].HostIp -cne '127.0.0.1' -or $binding[0].HostPort -ne [string]$Port -or
        @($created.Mounts | Where-Object { $_.Type -eq 'volume' -and $_.Name -ceq $volume -and $_.Destination -ceq '/var/lib/postgresql/data' }).Count -ne 1) {
        throw 'Demo container isolation mismatch; resources retained'
    }
    $ready = $false
    for ($i=0; $i -lt 60; $i++) {
        $null = & docker --context desktop-linux exec $container pg_isready -h 127.0.0.1 -U helpdesk_setup -d $database 2>&1
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'PostgreSQL not ready; resources retained, no automatic deletion' }
    $version = [int](Sql 'SHOW server_version_num;' 'helpdesk_setup' $setupPassword)
    if ($version -lt 170000 -or $version -ge 180000) { throw 'PostgreSQL 17 required' }
    $seed = @"
INSERT INTO $schema.tenant_guards VALUES ('$Tenant',0);
INSERT INTO $schema.workflow_definition_bindings VALUES ('$Tenant','mvp_request','it-helpdesk',1,true);
"@
    if ($seedActors) {
        for ($i=0; $i -lt 3; $i++) {
            $actor = 'oidc:' + (Encode $KeycloakIssuer) + '.' + (Encode $subjects[$i])
            $flags = @('true,false,false','false,true,false','false,false,true')[$i]
            $seed += "`nINSERT INTO $schema.tenant_memberships VALUES ('$Tenant','$actor',true);"
            $seed += "`nINSERT INTO $schema.helpdesk_actors (tenant_id,actor_id,submitter,coordinator,worker) VALUES ('$Tenant','$actor',$flags);"
        }
    }
    $install = @"
BEGIN;
CREATE ROLE $owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT;
\getenv runtime_secret HELPDESK_RUNTIME_PASSWORD
CREATE ROLE $runtime LOGIN PASSWORD :'runtime_secret' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT;
REVOKE ALL ON DATABASE $database FROM PUBLIC;
REVOKE ALL ON DATABASE postgres, template1 FROM PUBLIC;
GRANT CONNECT ON DATABASE $database TO $runtime;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA $schema AUTHORIZATION $owner;
SET LOCAL ROLE $owner;
ALTER DEFAULT PRIVILEGES IN SCHEMA $schema REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA $schema REVOKE ALL ON TABLES FROM PUBLIC;
$($sql -join "`n")
$seed
REVOKE ALL ON SCHEMA $schema FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA $schema FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA $schema FROM PUBLIC;
SET CONSTRAINTS ALL IMMEDIATE;
COMMIT;
"@
    $null = Sql $install 'helpdesk_setup' $setupPassword
    # Fresh TCP login asserts the runtime credential, seed, sealing and effective privileges.
    $expectedActors = if ($seedActors) { 3 } else { 0 }
    $verify = @'
BEGIN READ ONLY;
DO $check$
BEGIN
    IF current_user <> 'RUNTIME' OR EXISTS (SELECT FROM pg_roles WHERE rolname=current_user AND
        (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)) OR
        EXISTS (SELECT FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname=current_user)) OR
        has_database_privilege(current_user,current_database(),'CREATE') OR
        has_database_privilege(current_user,current_database(),'TEMP') OR
        EXISTS (SELECT FROM pg_database WHERE datallowconn AND datname<>current_database() AND has_database_privilege(current_user,oid,'CONNECT')) OR
        has_schema_privilege(current_user,'helpdesk_demo','CREATE') OR has_schema_privilege(current_user,'public','CREATE') THEN
        RAISE EXCEPTION 'runtime role is overprivileged';
    END IF;
    IF (SELECT count(*) FROM pg_class WHERE relnamespace='helpdesk_demo'::regnamespace AND relkind='r') <> 13 OR
        EXISTS (SELECT FROM pg_class WHERE relnamespace='helpdesk_demo'::regnamespace AND relowner=(SELECT oid FROM pg_roles WHERE rolname=current_user)) OR
        EXISTS (SELECT FROM pg_class c CROSS JOIN LATERAL aclexplode(c.relacl) a WHERE c.relnamespace='helpdesk_demo'::regnamespace AND a.grantee=0) OR
        EXISTS (SELECT FROM pg_proc p CROSS JOIN LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a WHERE p.pronamespace='helpdesk_demo'::regnamespace AND a.grantee=0) OR
        (SELECT count(*) FROM pg_proc WHERE pronamespace='helpdesk_demo'::regnamespace) <> 4 OR
        EXISTS (SELECT FROM pg_proc WHERE pronamespace='helpdesk_demo'::regnamespace AND (prosecdef OR proname LIKE 'wf_port_%')) THEN
        RAISE EXCEPTION 'schema ownership/PUBLIC grants/function inventory mismatch';
    END IF;
    IF EXISTS (SELECT FROM pg_class WHERE relnamespace='helpdesk_demo'::regnamespace AND relkind='r' AND
        (has_table_privilege(current_user,oid,'DELETE,TRUNCATE,TRIGGER,REFERENCES,UPDATE') OR
         has_table_privilege(current_user,oid,'SELECT') <> (relname <> 'workflow_policy_audit') OR
         has_table_privilege(current_user,oid,'INSERT') <> (relname IN ('resource_registry','resource_acl','helpdesk_requests','workflow_instances','workflow_transition_history')))) OR
        EXISTS (SELECT FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid
            WHERE c.relnamespace='helpdesk_demo'::regnamespace AND a.attnum>0 AND NOT a.attisdropped AND
            has_column_privilege(current_user,c.oid,a.attnum,'UPDATE') <>
            ((c.relname='tenant_guards' AND a.attname='acl_revision') OR
             (c.relname='resource_acl' AND a.attname='granted') OR
             (c.relname='helpdesk_requests' AND a.attname IN ('assignee_id','assignment_version','resolution')) OR
             (c.relname='workflow_instances' AND a.attname IN ('current_state','runtime_version','lifecycle','updated_at_seconds','updated_at_nanos')))) THEN
        RAISE EXCEPTION 'runtime data grants mismatch';
    END IF;
    IF (SELECT count(*) FROM helpdesk_demo.workflow_definitions WHERE definition_id='it-helpdesk' AND definition_version=1 AND initial_state='SUBMITTED' AND provisioning_transaction<>txid_current()) <> 1 OR
        (SELECT count(*) FROM helpdesk_demo.workflow_definition_states) <> 5 OR
        (SELECT count(*) FROM helpdesk_demo.workflow_definition_transitions) <> 4 OR
        (SELECT count(*) FROM helpdesk_demo.workflow_definition_bindings WHERE tenant_id='TENANT' AND resource_type='mvp_request' AND definition_id='it-helpdesk' AND definition_version=1 AND active) <> 1 OR
        (SELECT count(*) FROM helpdesk_demo.tenant_guards WHERE tenant_id='TENANT') <> 1 OR
        (SELECT count(*) FROM helpdesk_demo.tenant_memberships WHERE active) <> ACTORS OR
        (SELECT count(*) FROM helpdesk_demo.helpdesk_actors) <> ACTORS THEN
        RAISE EXCEPTION 'definition/seed verification failed';
    END IF;
END;
$check$;
ROLLBACK;
SELECT 'TCP_LOGIN_AND_CATALOG=PASS';
'@
    Sql ($verify.Replace('RUNTIME',$runtime).Replace('TENANT',$Tenant).Replace('ACTORS',[string]$expectedActors)) $runtime $runtimePassword
    Write-Output "SETUP=PASS SCHEMA=$schema RUNTIME=$runtime ACTORS=$expectedActors"
    Write-Output 'KEYCLOAK_E2E=NOT_VERIFIED (supplied subjects are provisioning input, not proof of authentication)'
} finally {
    $env:POSTGRES_PASSWORD = $savedPostgres
    $env:HELPDESK_RUNTIME_PASSWORD = $savedRuntime
    $setupPassword = $null; $runtimePassword = $null; $install = $null
}
