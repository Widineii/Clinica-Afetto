# Smoke E2E local - agendamentos e relatorios
$ErrorActionPreference = "Stop"
$base = "http://localhost:8081"

function Login-As($login, $senha) {
    $s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $r = Invoke-WebRequest -Uri "$base/login" -WebSession $s -UseBasicParsing
    $csrf = [regex]::Match($r.Content, 'name="_csrf" value="([^"]+)"').Groups[1].Value
    if (-not $csrf) { throw "CSRF nao encontrado no login" }
    $null = Invoke-WebRequest -Uri "$base/login" -Method POST -WebSession $s -Body @{ login=$login; senha=$senha; _csrf=$csrf } -MaximumRedirection 5 -UseBasicParsing
    return $s
}

function Get-Csrf($session, $url) {
    $p = Invoke-WebRequest -Uri $url -WebSession $session -UseBasicParsing
    $m = [regex]::Match($p.Content, 'name="_csrf" value="([^"]+)"')
    if (-not $m.Success) { throw "CSRF ausente em $url" }
    return $m.Groups[1].Value
}

$results = @()

function Ok($name) { $script:results += [pscustomobject]@{ Test=$name; Status="OK" } }
function Fail($name, $msg) { $script:results += [pscustomobject]@{ Test=$name; Status="FAIL: $msg" } }

try {
    # Health
    $h = Invoke-WebRequest -Uri "$base/actuator/health" -UseBasicParsing
    if ($h.Content -match '"status"\s*:\s*"UP"') { Ok "Health UP" } else { Fail "Health" $h.Content }

    # Admin dashboard
    $admin = Login-As "admin" "Luquinha12@"
    $dash = Invoke-WebRequest -Uri "$base/agendamentos/dashboard" -WebSession $admin -UseBasicParsing
    if ($dash.Content.Contains('id="agendamentos-profissionais"') -and -not $dash.Content.Contains('id="meus-agendamentos"')) { Ok "Admin painel profissionais" }
    else { Fail "Admin painel" "layout incorreto" }

    # Carol profissional
    $carol = Login-As "carol" "297b"
    $cd = Invoke-WebRequest -Uri "$base/agendamentos/dashboard" -WebSession $carol -UseBasicParsing
    if ($cd.Content.Contains('Layout caderno') -and $cd.Content.Contains('id="meus-agendamentos"') -and -not $cd.Content.Contains('id="agendamentos-profissionais"')) { Ok "Carol layout caderno" }
    else { Fail "Carol layout" "meus/admin incorreto" }

    # Relatorio semanal admin
    $rs = Invoke-WebRequest -Uri "$base/agendamentos/relatorio/semanal" -WebSession $admin -UseBasicParsing
    if ($rs.StatusCode -eq 200) { Ok "Relatorio semanal" } else { Fail "Relatorio semanal" $rs.StatusCode }

    # Relatorio mensal admin
    $rm = Invoke-WebRequest -Uri "$base/agendamentos/relatorio/mensal" -WebSession $admin -UseBasicParsing
    if ($rm.StatusCode -eq 200) { Ok "Relatorio mensal" } else { Fail "Relatorio mensal" $rm.StatusCode }

    # Central profissionais (admin)
    $cp = Invoke-WebRequest -Uri "$base/agendamentos/central-profissionais" -WebSession $admin -UseBasicParsing
    if ($cp.StatusCode -eq 200 -and $cp.Content -match 'Carol|Julia|Polyana') { Ok "Central profissionais" }
    else { Fail "Central profissionais" "lista vazia ou 403" }

    # Polyana dona
    $poly = Login-As "polyana" "297b"
    $pd = Invoke-WebRequest -Uri "$base/agendamentos/dashboard" -WebSession $poly -UseBasicParsing
    if ($pd.Content.Contains('id="meus-agendamentos"')) { Ok "Polyana meus agendamentos" }
    else { Fail "Polyana dashboard" "" }

    Ok "Login flows"
}
catch {
    Fail "Excecao geral" $_.Exception.Message
}

$results | Format-Table -AutoSize
if ($results | Where-Object { $_.Status -like 'FAIL*' }) { exit 1 }
exit 0
