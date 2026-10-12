$ErrorActionPreference = "Stop"

$issuer = "http://localhost:8180/realms/tricore"
$clientId = "tricore-api"
$redirectUri = "http://localhost:8080/login/oauth2/code/tricore"

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("$redirectUri/")

try {
    $listener.Start()

    $state = [Guid]::NewGuid().ToString("N")

    $authUrl =
        "$issuer/protocol/openid-connect/auth" +
        "?client_id=$clientId" +
        "&response_type=code" +
        "&scope=openid" +
        "&redirect_uri=$([uri]::EscapeDataString($redirectUri))" +
        "&state=$state"

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

    $tokenResponse = Invoke-RestMethod `
        -Method Post `
        -Uri "$issuer/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{
            grant_type   = "authorization_code"
            client_id    = $clientId
            code         = $query["code"]
            redirect_uri = $redirectUri
        }

    Write-Host "Token exchange: SUCCESS"
    Write-Host "Access token present:" (
        -not [string]::IsNullOrWhiteSpace($tokenResponse.access_token)
    )
    Write-Host "Token type:" $tokenResponse.token_type
    Write-Host "Expires in:" $tokenResponse.expires_in

    $jwtParts = $tokenResponse.access_token.Split(".")

    if ($jwtParts.Length -ne 3) {
        throw "Access token is not a three-part JWT"
    }

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
    Write-Host "iss:" $claims.iss
    Write-Host "sub present:" (-not [string]::IsNullOrWhiteSpace($claims.sub))
    Write-Host "aud:" ($claims.aud -join ", ")
    Write-Host "azp:" $claims.azp
    Write-Host "typ:" $claims.typ
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
}
catch {
    Write-Host "POC result: FAIL"
    Write-Host "Reason:" $_.Exception.Message
}
finally {
    if ($listener) {
        try { $listener.Stop() } catch {}
        try { $listener.Close() } catch {}
    }

    Write-Host "Listener cleaned: True"
}