param(
    [switch]$RunBackendIntegration,
    [switch]$RunGatewayIntegrationOnly,
    [switch]$WrongVerifier
)

$ErrorActionPreference = "Stop"

if ($RunGatewayIntegrationOnly -and [string]::IsNullOrWhiteSpace($env:OIDC_TEST_EXPECTED_SUBJECT)) {
    throw "OIDC_TEST_EXPECTED_SUBJECT is required for -RunGatewayIntegrationOnly"
}

$issuer = "http://localhost:8180/realms/tricore"
$clientId = "tricore-api"
$redirectUri = "http://localhost:8080/login/oauth2/code/tricore"

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("$redirectUri/")

try {
    $listener.Start()

    $state = [Guid]::NewGuid().ToString("N")

    # RFC 7636 PKCE: generate a cryptographically random verifier and
    # derive the S256 challenge. Never print or persist the verifier.
    $verifierBytes = [byte[]]::new(32)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($verifierBytes)
    }
    finally {
        $rng.Dispose()
    }

    $codeVerifier = [Convert]::ToBase64String($verifierBytes).
        TrimEnd("=").
        Replace("+", "-").
        Replace("/", "_")

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $challengeBytes = $sha256.ComputeHash(
            [System.Text.Encoding]::ASCII.GetBytes($codeVerifier)
        )
    }
    finally {
        $sha256.Dispose()
    }

    $codeChallenge = [Convert]::ToBase64String($challengeBytes).
        TrimEnd("=").
        Replace("+", "-").
        Replace("/", "_")

    $authUrl =
        "$issuer/protocol/openid-connect/auth" +
        "?client_id=$clientId" +
        "&response_type=code" +
        "&scope=openid" +
        "&redirect_uri=$([uri]::EscapeDataString($redirectUri))" +
        "&state=$state" +
        "&code_challenge=$([uri]::EscapeDataString($codeChallenge))" +
        "&code_challenge_method=S256"

    Write-Host "OIDC POC started"
    Write-Host "Listener active: $($listener.IsListening)"
    Write-Host "Opening authorization request..."

    Start-Process $authUrl

    $context = $listener.GetContext()
    $query = $context.Request.QueryString

    $responseText = "OIDC callback received. You may close this tab."
    $responseBytes = [System.Text.Encoding]::UTF8.GetBytes($responseText)

    $context.Response.StatusCode = 200
    $context.Response.ContentType = "text/plain; charset=utf-8"
    $context.Response.ContentLength64 = $responseBytes.Length
    $context.Response.OutputStream.Write(
        $responseBytes,
        0,
        $responseBytes.Length
    )
    $context.Response.Close()

    Write-Host "Callback received: True"

    if ($query["state"] -ne $state) {
        throw "OAuth state mismatch"
    }

    Write-Host "State matches: True"

    if (-not [string]::IsNullOrWhiteSpace($query["error"])) {
        throw "Authorization failed: $($query['error'])"
    }

    if ([string]::IsNullOrWhiteSpace($query["code"])) {
        throw "Authorization code missing"
    }

    Write-Host "Code present: True"
    Write-Host "Exchanging code immediately..."

    $exchangeVerifier = $codeVerifier

    if ($WrongVerifier) {
        # Negative PKCE test: exchange the valid authorization code with a
        # different cryptographically random verifier. Never print either verifier.
        $wrongVerifierBytes = [byte[]]::new(32)
        $wrongVerifierRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $wrongVerifierRng.GetBytes($wrongVerifierBytes)
        }
        finally {
            $wrongVerifierRng.Dispose()
        }

        $exchangeVerifier = [Convert]::ToBase64String($wrongVerifierBytes).
            TrimEnd("=").
            Replace("+", "-").
            Replace("/", "_")

        try {
            $null = Invoke-RestMethod `
                -Method Post `
                -Uri "$issuer/protocol/openid-connect/token" `
                -ContentType "application/x-www-form-urlencoded" `
                -Body @{
                    grant_type    = "authorization_code"
                    client_id     = $clientId
                    code          = $query["code"]
                    redirect_uri  = $redirectUri
                    code_verifier = $exchangeVerifier
                }

            throw "SECURITY FAILURE: token exchange accepted a wrong PKCE verifier"
        }
        catch {
            if ($_.Exception.Message -like "SECURITY FAILURE:*") {
                throw
            }

            if (-not $_.Exception.Response) {
                throw "PKCE negative test inconclusive: token endpoint returned no HTTP response"
            }

            $statusCode = [int]$_.Exception.Response.StatusCode

            if ($statusCode -ne 400) {
                throw "PKCE negative test inconclusive: unexpected HTTP status $statusCode"
            }

            Write-Host "Wrong verifier rejected: True"
            Write-Host "Token endpoint HTTP status: 400"
            Write-Host "PKCE negative test: PASS"
            return
        }
    }

    $tokenResponse = Invoke-RestMethod `
        -Method Post `
        -Uri "$issuer/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{
            grant_type    = "authorization_code"
            client_id     = $clientId
            code          = $query["code"]
            redirect_uri  = $redirectUri
            code_verifier = $exchangeVerifier
        }

    Write-Host "Token exchange: SUCCESS"
    Write-Host "Access token present:" (
        -not [string]::IsNullOrWhiteSpace($tokenResponse.access_token)
    )
    Write-Host "Expires in:" $tokenResponse.expires_in

    $jwtParts = $tokenResponse.access_token.Split(".")

    if ($jwtParts.Length -ne 3) {
        throw "Access token is not a three-part JWT"
    }

    $header = $jwtParts[0].
        Replace("-", "+").
        Replace("_", "/")

    switch ($header.Length % 4) {
        2 { $header += "==" }
        3 { $header += "=" }
        0 { }
        default { throw "Invalid JWT header encoding" }
    }

    $headerJson = [System.Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String($header)
    )

    $joseHeader = $headerJson | ConvertFrom-Json

    $payload = $jwtParts[1].
        Replace("-", "+").
        Replace("_", "/")

    switch ($payload.Length % 4) {
        2 { $payload += "==" }
        3 { $payload += "=" }
        0 { }
        default { throw "Invalid JWT payload encoding" }
    }

    $payloadJson = [System.Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String($payload)
    )

    $claims = $payloadJson | ConvertFrom-Json

    Write-Host ""
    Write-Host "=== SAFE JWT CLAIM INSPECTION ==="
    Write-Host "OAuth token_type:" $tokenResponse.token_type
    Write-Host "JOSE alg:" $joseHeader.alg
    Write-Host "JOSE typ:" $joseHeader.typ
    $payloadTyp = if ($null -eq $claims.PSObject.Properties['typ']) {
        "<absent>"
    }
    else {
        $claims.typ
    }
    Write-Host "Payload typ:" $payloadTyp
    Write-Host "iss:" $claims.iss
    Write-Host "sub present:" (-not [string]::IsNullOrWhiteSpace($claims.sub))
    Write-Host "aud:" ($claims.aud -join ", ")
    Write-Host "azp:" $claims.azp
    Write-Host "scope:" $claims.scope
    Write-Host "preferred_username:" $claims.preferred_username
    Write-Host "exp present:" ($null -ne $claims.exp)
    Write-Host "nbf present:" ($null -ne $claims.nbf)

    Write-Host ""
    Write-Host "realm roles:"

    if ($null -ne $claims.realm_access.roles) {
        $claims.realm_access.roles | ForEach-Object {
            Write-Host " -" $_
        }
    }
    else {
        Write-Host " <none>"
    }

    Write-Host ""
    Write-Host "tricore-api client roles:"

    $clientAccess = $claims.resource_access.'tricore-api'

    if ($null -ne $clientAccess.roles) {
        $clientAccess.roles | ForEach-Object {
            Write-Host " -" $_
        }
    }
    else {
        Write-Host " <none>"
    }

    # ============================================================
    # REAL SPRING OIDC/JWKS INTEGRATION
    # ============================================================

    if ($RunBackendIntegration -or $RunGatewayIntegrationOnly) {
        Write-Host ""
        Write-Host "=== SPRING OIDC INTEGRATION TEST ==="

        $previousTestToken = $env:OIDC_TEST_ACCESS_TOKEN
        $previousD2AEvidence = $env:OIDC_TEST_D2A_EVIDENCE

        try {
            # Token is passed only through the child process environment.
            # Never write or print the raw token.
            $env:OIDC_TEST_ACCESS_TOKEN = $tokenResponse.access_token
            $env:OIDC_TEST_D2A_EVIDENCE = if ($RunGatewayIntegrationOnly) { "true" } else { "false" }

            if ($RunGatewayIntegrationOnly) {
                Write-Host "Expected subject provided: True"
                Write-Host "Canonical assertion required: True"
            }

            $repoRoot = Resolve-Path (
                Join-Path $PSScriptRoot "..\.."
            )

            $gradleWrapper = Join-Path `
                $repoRoot `
                "backend\gradlew.bat"

            $backendDir = Join-Path `
                $repoRoot `
                "backend"

            $gatewaySuite = "com.tricore.dxos.infrastructure.identity.security.GatewayKeycloakTokenHttpIntegrationTest"
            $oidcSuite = "com.tricore.dxos.infrastructure.identity.security.OidcKeycloakIntegrationTest"

            $suites = if ($RunGatewayIntegrationOnly) {
                @($gatewaySuite)
            }
            else {
                @($oidcSuite, $gatewaySuite)
            }

            $gradleArgs = @(
                "--no-daemon",
                "-p", $backendDir,
                "test",
                "--rerun-tasks"
            )

            foreach ($suite in $suites) {
                $gradleArgs += @("--tests", $suite)
            }

            & $gradleWrapper @gradleArgs

            if ($LASTEXITCODE -ne 0) {
                throw "Spring OIDC + Gateway integration test failed with exit code $LASTEXITCODE"
            }

            # The selected authenticated HTTP/OIDC tests must really execute.
            # BUILD SUCCESSFUL is not enough if an environment gate skipped a selected suite.
            foreach ($suite in $suites) {
                $xmlPath = Join-Path $backendDir ("build\test-results\test\TEST-" + $suite + ".xml")
                if (-not (Test-Path -LiteralPath $xmlPath -PathType Leaf)) {
                    throw "Expected JUnit suite result is missing"
                }
                [xml]$report = Get-Content -LiteralPath $xmlPath -Raw
                $summary = $report.testsuite
                $total = [int]$summary.tests
                $failed = [int]$summary.failures
                $errors = [int]$summary.errors
                $skipped = [int]$summary.skipped
                if ($total -ne 3 -or $failed -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
                    throw "OIDC/Gateway JUnit gate did not execute three clean tests"
                }
                Write-Host ("JUnit " + $suite.Split(".")[-1] + ": 3 PASS / 0 FAIL / 0 ERROR / 0 SKIP")
            }

            Write-Host "Spring OIDC + Gateway HTTP integration: SUCCESS"
        }
        finally {
            # Do not leave the temporary access token in this process.
            if ($null -eq $previousTestToken) {
                Remove-Item `
                    Env:OIDC_TEST_ACCESS_TOKEN `
                    -ErrorAction SilentlyContinue
            }
            else {
                $env:OIDC_TEST_ACCESS_TOKEN = $previousTestToken
            }

            if ($null -eq $previousD2AEvidence) {
                Remove-Item Env:OIDC_TEST_D2A_EVIDENCE -ErrorAction SilentlyContinue
            }
            else {
                $env:OIDC_TEST_D2A_EVIDENCE = $previousD2AEvidence
            }
        }
    }
}
catch {
    Write-Host "POC result: FAIL"
    Write-Host "Reason: OIDC POC step failed"
    exit 1
}
finally {
    if ($listener) {
        try {
            $listener.Stop()
        }
        catch {
        }

        try {
            $listener.Close()
        }
        catch {
        }
    }

    Write-Host "Listener cleaned: True"
}
