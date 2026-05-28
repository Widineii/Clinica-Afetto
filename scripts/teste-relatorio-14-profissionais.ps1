# Relatorio de teste local: 14 profissionais com agendamentos no mes passado
$ErrorActionPreference = "Stop"
$base = "http://localhost:8081"

function Get-CsrfFromHtml([string]$html) {
    if ($html -match 'name="_csrf"\s+value="([^"]+)"') { return $matches[1] }
    throw "CSRF nao encontrado."
}

function Invoke-FormPost($session, $url, $fields) {
    return Invoke-WebRequest -Uri $url -Method Post -WebSession $session -Body $fields -UseBasicParsing -MaximumRedirection 0 -ErrorAction SilentlyContinue
}

$mesPassado = (Get-Date).AddMonths(-1)
$ano = $mesPassado.Year
$mes = $mesPassado.Month
$ultimoDia = [DateTime]::DaysInMonth($ano, $mes)
$diasUteis = @()
for ($d = 1; $d -le $ultimoDia; $d++) {
    $dt = Get-Date -Year $ano -Month $mes -Day $d
    if ($dt.DayOfWeek -ne 'Sunday') { $diasUteis += $dt.ToString('yyyy-MM-dd') }
}

Write-Host "=== RELATORIO TESTE: 14 PROFISSIONAIS ===" -ForegroundColor Cyan
Write-Host "Mes passado: $($mesPassado.ToString('MMMM yyyy', [cultureinfo]'pt-BR'))"

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginPage = Invoke-WebRequest -Uri "$base/login" -WebSession $session -UseBasicParsing
$csrf = Get-CsrfFromHtml $loginPage.Content
Invoke-FormPost $session "$base/login" @{ login = "admin"; senha = "Luquinha12@"; _csrf = $csrf } | Out-Null

$dash = Invoke-WebRequest -Uri "$base/agendamentos/dashboard" -WebSession $session -UseBasicParsing
$csrf = Get-CsrfFromHtml $dash.Content

$profBlock = [regex]::Match($dash.Content, 'name="profissionalId"[\s\S]*?</select>').Value
$profs = @()
foreach ($m in [regex]::Matches($profBlock, '<option value="(\d+)"[^>]*>([^<]+)</option>')) {
    if ($m.Groups[1].Value) {
        $profs += @{ id = $m.Groups[1].Value; nome = $m.Groups[2].Value.Trim() }
    }
}

$salaBlock = [regex]::Match($dash.Content, 'name="salaId"[\s\S]*?</select>').Value
$salas = [regex]::Matches($salaBlock, 'value="(\d+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ }

if ($profs.Count -lt 14) {
    Write-Host "Criando profissionais de teste (tinha $($profs.Count))..."
    for ($i = $profs.Count + 1; $i -le 14; $i++) {
        $login = "testeprof$i"
        Invoke-FormPost $session "$base/agendamentos/profissionais" @{
            _csrf = $csrf; nome = "Profissional Teste $i"; login = $login; senha = "297b"
        } | Out-Null
        $dash = Invoke-WebRequest -Uri "$base/agendamentos/dashboard" -WebSession $session -UseBasicParsing
        $csrf = Get-CsrfFromHtml $dash.Content
    }
    $profBlock = [regex]::Match($dash.Content, 'name="profissionalId"[\s\S]*?</select>').Value
    $profs = @()
    foreach ($m in [regex]::Matches($profBlock, '<option value="(\d+)"[^>]*>([^<]+)</option>')) {
        if ($m.Groups[1].Value) { $profs += @{ id = $m.Groups[1].Value; nome = $m.Groups[2].Value.Trim() } }
    }
}

$profsRelatorio = $profs | Where-Object { $_.nome -notmatch 'Administrador' } | Select-Object -First 14
Write-Host "Profissionais no relatorio ($($profsRelatorio.Count)):"
$profsRelatorio | ForEach-Object { Write-Host "  - $($_.nome)" }

$horarios = 7..20
$criados = 0
for ($i = 0; $i -lt $profsRelatorio.Count; $i++) {
    $prof = $profsRelatorio[$i]
    $salaId = $salas[$i % $salas.Count]
    $dia = $diasUteis[$i % $diasUteis.Count]
    $hora = "{0:D2}:00" -f $horarios[$i % $horarios.Count]

    try {
        Invoke-FormPost $session "$base/agendamentos" @{
            _csrf = $csrf
            profissionalId = $prof.id
            salaId = $salaId
            nomeCliente = "Cliente teste relatorio"
            dataAtendimento = $dia
            horarioAtendimento = $hora
            recorrencia = "AVULSO"
            fixo = "false"
        } | Out-Null
        $criados++
    } catch {
        Write-Host "  Aviso: $($prof.nome) - $_" -ForegroundColor Yellow
    }
    $dash = Invoke-WebRequest -Uri "$base/agendamentos/dashboard" -WebSession $session -UseBasicParsing
    $csrf = Get-CsrfFromHtml $dash.Content
}

Write-Host "Agendamentos avulsos criados no mes passado: $criados" -ForegroundColor Green

$rel = Invoke-WebRequest -Uri "$base/agendamentos/relatorio" -WebSession $session -UseBasicParsing
if ($rel.StatusCode -eq 200 -and $rel.Content -match 'profissional-card|Total de horarios') {
    Write-Host "Pagina do relatorio OK: $base/agendamentos/relatorio" -ForegroundColor Green
    $m = [regex]::Match($rel.Content, 'profissional-card')
    $cards = ([regex]::Matches($rel.Content, 'profissional-card')).Count
    Write-Host "Cards de profissionais na pagina: $cards"
} else {
    Write-Host "Relatorio retornou redirect ou erro. Abra manualmente apos login." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Abra no navegador (logado como admin):" -ForegroundColor Cyan
Write-Host "  $base/agendamentos/relatorio"
