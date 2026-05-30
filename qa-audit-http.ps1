# QA HTTP audit script - Agenda Afetto
$base = "http://localhost:8081"
$results = @()

function Get-Csrf($session) {
    $page = Invoke-WebRequest -Uri "$base/login" -WebSession $session -UseBasicParsing
    if ($page.Content -match 'name="_csrf"\s+value="([^"]+)"') { return $Matches[1] }
    return $null
}

function Login-User($login, $senha) {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $csrf = Get-Csrf $session
    $body = @{ login = $login; senha = $senha; _csrf = $csrf }
    try {
        $null = Invoke-WebRequest -Uri "$base/login" -Method POST -WebSession $session -Body $body -MaximumRedirection 5 -UseBasicParsing
    } catch {
        # redirect may throw in PS
    }
    return $session
}

function Test-Route($name, $session, $url, $expectStatus, $expectContains) {
    try {
        $r = Invoke-WebRequest -Uri "$url" -WebSession $session -MaximumRedirection 0 -UseBasicParsing -ErrorAction Stop
        $status = [int]$r.StatusCode
        $loc = $r.Headers['Location']
        $body = $r.Content
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $status = [int]$resp.StatusCode
            $loc = $resp.Headers['Location']
            try { $reader = New-Object System.IO.StreamReader($resp.GetResponseStream()); $body = $reader.ReadToEnd() } catch { $body = '' }
        } else {
            $script:results += [pscustomobject]@{ Test = $name; Result = "ERROR"; Detail = $_.Exception.Message }
            return
        }
    }
    $ok = ($status -eq $expectStatus)
    if ($expectContains) {
        $ok = $ok -and ($body -like "*$expectContains*")
    }
    $detail = "HTTP $status"
    if ($loc) { $detail += " -> $loc" }
    $script:results += [pscustomobject]@{ Test = $name; Result = $(if ($ok) { "PASS" } else { "FAIL" }); Detail = $detail }
}

# --- Auth ---
Test-Route "Acesso dashboard sem login" $null "$base/agendamentos/dashboard" 302 $null

$sBad = Login-User "polyana" "senhaerrada"
Test-Route "Login senha errada" $sBad "$base/agendamentos/dashboard" 200 $null

$sPoly = Login-User "polyana" "297b"
Test-Route "Polyana dashboard" $sPoly "$base/agendamentos/dashboard" 200 "Layout caderno"

$sJulia = Login-User "julia" "297b"
Test-Route "Julia dashboard" $sJulia "$base/agendamentos/dashboard" 200 "Layout caderno"

$sAdmin = Login-User "admin" "Luquinha12@"
Test-Route "Admin dashboard" $sAdmin "$base/agendamentos/dashboard" 200 $null

# --- Permissions ---
Test-Route "Julia central profissionais (negado)" $sJulia "$base/agendamentos/central-profissionais" 302 $null
Test-Route "Polyana central profissionais" $sPoly "$base/agendamentos/central-profissionais" 200 "Central"
Test-Route "Admin central profissionais (negado)" $sAdmin "$base/agendamentos/central-profissionais" 302 $null

Test-Route "Julia meus pagamentos" $sJulia "$base/agendamentos/meus-pagamentos" 200 "Meus pagamentos"
Test-Route "Polyana meus pagamentos (redirect)" $sPoly "$base/agendamentos/meus-pagamentos" 302 $null

Test-Route "Julia relatorio (negado)" $sJulia "$base/agendamentos/relatorio/semanal" 302 $null
Test-Route "Polyana relatorio semanal" $sPoly "$base/agendamentos/relatorio/semanal" 200 $null

Test-Route "Julia financeiro (negado)" $sJulia "$base/agendamentos/financeiro" 302 $null
Test-Route "Polyana financeiro" $sPoly "$base/agendamentos/financeiro" 200 $null

Test-Route "Julia admin uso banco (negado)" $sJulia "$base/agendamentos/admin/uso-banco" 302 $null
Test-Route "Admin uso banco" $sAdmin "$base/agendamentos/admin/uso-banco" 200 $null

# --- Logout ---
try {
    $csrfDash = (Invoke-WebRequest -Uri "$base/agendamentos/dashboard" -WebSession $sJulia -UseBasicParsing).Content
    if ($csrfDash -match 'name="_csrf"\s+value="([^"]+)"') { $csrfOut = $Matches[1] } else { $csrfOut = Get-Csrf $sJulia }
    Invoke-WebRequest -Uri "$base/logout" -Method POST -WebSession $sJulia -Body @{ _csrf = $csrfOut } -MaximumRedirection 5 -UseBasicParsing | Out-Null
    Test-Route "Pos logout dashboard" $sJulia "$base/agendamentos/dashboard" 302 $null
} catch {
    $results += [pscustomobject]@{ Test = "Logout"; Result = "WARN"; Detail = $_.Exception.Message }
}

# --- Webhook ---
try {
    $wh = Invoke-WebRequest -Uri "$base/api/webhooks/infinitepay" -Method POST -Body '{}' -ContentType "application/json" -UseBasicParsing -ErrorAction Stop
    $results += [pscustomobject]@{ Test = "Webhook sem secret"; Result = "FAIL"; Detail = "HTTP $($wh.StatusCode) deveria rejeitar" }
} catch {
    $status = [int]$_.Exception.Response.StatusCode
    $results += [pscustomobject]@{ Test = "Webhook sem secret"; Result = $(if ($status -ge 400) { "PASS" } else { "FAIL" }); Detail = "HTTP $status" }
}

$results | Format-Table -AutoSize
$fail = ($results | Where-Object { $_.Result -eq "FAIL" }).Count
Write-Host "TOTAL: $($results.Count) | FAIL: $fail | PASS: $(($results | Where-Object { $_.Result -eq 'PASS' }).Count)"
