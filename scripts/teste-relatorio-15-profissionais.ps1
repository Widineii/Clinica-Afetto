# Relatorio de teste: 15 profissionais do sistema, salas aleatorias, mes passado
$ErrorActionPreference = "Stop"
$base = "http://localhost:8081"

function Get-CsrfFromHtml([string]$html) {
    if ($html -match 'name="_csrf"\s+value="([^"]+)"') { return $matches[1] }
    throw "CSRF nao encontrado."
}

function Invoke-FormPost($session, $url, $fields) {
    Invoke-WebRequest -Uri $url -Method Post -WebSession $session -Body $fields -UseBasicParsing | Out-Null
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
$horarios = 7..20

Write-Host "=== RELATORIO TESTE: 15 PROFISSIONAIS (SALAS ALEATORIAS) ===" -ForegroundColor Cyan
Write-Host "Mes passado: $($mesPassado.ToString('MMMM yyyy', [cultureinfo]'pt-BR'))"

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginPage = Invoke-WebRequest -Uri "$base/login" -WebSession $session -UseBasicParsing
$csrf = Get-CsrfFromHtml $loginPage.Content
Invoke-FormPost $session "$base/login" @{ login = "admin"; senha = "Luquinha12@"; _csrf = $csrf }

$dash = Invoke-WebRequest -Uri "$base/agendamentos/dashboard" -WebSession $session -UseBasicParsing
$csrf = Get-CsrfFromHtml $dash.Content

Write-Host "[1/4] Resetando arquivo antigo do mes passado (so local)..."
try {
    Invoke-FormPost $session "$base/agendamentos/relatorio/dev/resetar-arquivo-mes-passado" @{ _csrf = $csrf }
    Write-Host "    OK"
} catch {
    Write-Host "    Aviso: $($_.Exception.Message)" -ForegroundColor Yellow
}
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
$salas = @([regex]::Matches($salaBlock, 'value="(\d+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ })

$profsRelatorio = $profs | Where-Object { $_.nome -notmatch 'Administrador' } | Select-Object -First 15
if ($profsRelatorio.Count -lt 15) {
    throw "O sistema precisa ter pelo menos 15 profissionais (sem admin). Encontrados: $($profsRelatorio.Count)"
}

Write-Host "[2/4] Profissionais ($($profsRelatorio.Count)):"
$profsRelatorio | ForEach-Object { Write-Host "  - $($_.nome)" }

Write-Host "[3/4] Criando agendamentos avulsos no mes passado (sala aleatoria)..."
$rng = [System.Random]::new()
$criados = 0
$erros = 0

foreach ($i in 0..($profsRelatorio.Count - 1)) {
    $prof = $profsRelatorio[$i]
    $salaId = $salas[$rng.Next(0, $salas.Count)]
    $dia = $diasUteis[$rng.Next(0, $diasUteis.Count)]
    $hora = "{0:D2}:00" -f $horarios[$rng.Next(0, $horarios.Count)]
    $salaNum = ([array]::IndexOf($salas, $salaId)) + 1

    try {
        Invoke-FormPost $session "$base/agendamentos" @{
            _csrf = $csrf
            profissionalId = $prof.id
            salaId = $salaId
            nomeCliente = "Teste relatorio $($i + 1)"
            dataAtendimento = $dia
            horarioAtendimento = $hora
            recorrencia = "AVULSO"
            fixo = "false"
        }
        $criados++
        Write-Host "  + $($prof.nome) | $dia $hora | Sala $salaNum" -ForegroundColor DarkGray
    } catch {
        $erros++
        Write-Host "  ! $($prof.nome) falhou ($dia $hora sala $salaNum)" -ForegroundColor Yellow
    }
    $dash = Invoke-WebRequest -Uri "$base/agendamentos/dashboard" -WebSession $session -UseBasicParsing
    $csrf = Get-CsrfFromHtml $dash.Content
}

Write-Host "    Criados: $criados | Falhas: $erros" -ForegroundColor Green

Write-Host "[4/4] Abrindo relatorio (fecha o mes passado automaticamente)..."
$rel = Invoke-WebRequest -Uri "$base/agendamentos/relatorio" -WebSession $session -UseBasicParsing

$total = if ($rel.Content -match 'Total de Horarios Agendados no Mes[\s\S]*?<h3>\s*(\d+)\s*</h3>') { $matches[1] } else { "?" }
$profsAtivos = if ($rel.Content -match 'Profissionais Ativos no Mes[\s\S]*?<h3>\s*(\d+)\s*</h3>') { $matches[1] } else { "?" }
$linhas = ([regex]::Matches($rel.Content, 'class="prof-cell"')).Count

Write-Host ""
Write-Host "=== RESULTADO ===" -ForegroundColor Green
Write-Host "Total horarios: $total"
Write-Host "Profissionais na tabela: $profsAtivos (linhas: $linhas)"
Write-Host "URL: $base/agendamentos/relatorio" -ForegroundColor Cyan
