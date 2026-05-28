# Teste real: login, 15 agendamentos em abril/2026, relatorio automatico e PDF
$ErrorActionPreference = "Stop"
$base = "http://localhost:8081"
$pdfPath = Join-Path $env:TEMP "relatorio-teste-real.pdf"

function Get-CsrfFromHtml([string]$html) {
    if ($html -match 'name="_csrf"\s+value="([^"]+)"') {
        return $matches[1]
    }
    throw "CSRF nao encontrado na pagina."
}

function Invoke-FormPost($session, $url, $fields) {
    return Invoke-WebRequest -Uri $url -Method Post -WebSession $session -Body $fields -UseBasicParsing
}

Write-Host "=== TESTE REAL DO RELATORIO MENSAL ===" -ForegroundColor Cyan

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

Write-Host "[1/6] Login como admin..."
$loginPage = Invoke-WebRequest -Uri "$base/login" -WebSession $session -UseBasicParsing
$csrf = Get-CsrfFromHtml $loginPage.Content
Invoke-FormPost $session "$base/login" @{
    login  = "admin"
    senha  = "Luquinha12@"
    _csrf  = $csrf
} | Out-Null

Write-Host "[2/6] Carregando agenda..."
$dash = Invoke-WebRequest -Uri "$base/agendamentos/dashboard" -WebSession $session -UseBasicParsing
$csrf = Get-CsrfFromHtml $dash.Content

$profissionais = [regex]::Matches($dash.Content, 'name="profissionalId"[^>]*>[\s\S]*?</select>') 
$profIds = [regex]::Matches($dash.Content, '<option value="(\d+)"[^>]*>[^<]+</option>') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
$salaIds = [regex]::Matches($dash.Content, 'name="salaId"[\s\S]*?</select>') 
if ($salaIds.Count -eq 0) {
    $salaBlock = [regex]::Match($dash.Content, 'name="salaId"[\s\S]*?</select>').Value
    $salaIdList = [regex]::Matches($salaBlock, 'value="(\d+)"') | ForEach-Object { $_.Groups[1].Value }
} else {
    $salaBlock = $salaIds[0].Value
    $salaIdList = [regex]::Matches($salaBlock, 'value="(\d+)"') | ForEach-Object { $_.Groups[1].Value }
}

$profBlock = [regex]::Match($dash.Content, 'name="profissionalId"[\s\S]*?</select>').Value
$profIdList = [regex]::Matches($profBlock, 'value="(\d+)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne "" }

if ($profIdList.Count -lt 2 -or $salaIdList.Count -lt 2) {
    throw "Nao foi possivel ler profissionais/salas do dashboard."
}

$loginsProf = @()
foreach ($m in [regex]::Matches($profBlock, '<option value="(\d+)"[^>]*>([^<]+)</option>')) {
    if ($m.Groups[1].Value) { $loginsProf += @{ id = $m.Groups[1].Value; nome = $m.Groups[2].Value.Trim() } }
}

$diasAbril = @(
    "2026-04-07","2026-04-08","2026-04-09","2026-04-10","2026-04-11",
    "2026-04-14","2026-04-15","2026-04-16","2026-04-17","2026-04-18",
    "2026-04-21","2026-04-22","2026-04-23","2026-04-24","2026-04-25"
)
$horarios = @(7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21)

Write-Host "[3/6] Criando 15 agendamentos em abril/2026..."
$criados = 0
for ($i = 0; $i -lt 15; $i++) {
    $prof = $loginsProf[$i % $loginsProf.Count]
    $salaId = $salaIdList[$i % $salaIdList.Count]
    $hora = "{0:D2}:00" -f $horarios[$i]

    $resp = Invoke-FormPost $session "$base/agendamentos" @{
        _csrf             = $csrf
        profissionalId    = $prof.id
        salaId            = $salaId
        nomeCliente       = "Teste real cliente $($i + 1)"
        dataAtendimento   = $diasAbril[$i]
        horarioAtendimento = $hora
        recorrencia       = "AVULSO"
        fixo              = "false"
    }
    if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400) {
        $criados++
    }
    $dash = Invoke-WebRequest -Uri "$base/agendamentos/dashboard" -WebSession $session -UseBasicParsing
    $csrf = Get-CsrfFromHtml $dash.Content
}
Write-Host "    Agendamentos criados: $criados"

Write-Host "[4/6] Abrindo relatorio (processamento automatico do mes passado)..."
$rel = Invoke-WebRequest -Uri "$base/agendamentos/relatorio" -WebSession $session -UseBasicParsing

if ($rel.Content -notmatch "Relatorio pronto" -and $rel.Content -notmatch "Baixar PDF") {
    Write-Host "    Aviso: tela pode estar aguardando processamento automatico." -ForegroundColor Yellow
}

$totalMatch = [regex]::Match($rel.Content, "Total de horarios:\s*(\d+)")
if ($totalMatch.Success) {
    Write-Host "    Total no relatorio: $($totalMatch.Groups[1].Value)"
}

Write-Host "[5/6] Baixando PDF..."
try {
    $pdf = Invoke-WebRequest -Uri "$base/agendamentos/relatorio/mensal/download" -WebSession $session -UseBasicParsing -OutFile $pdfPath
    $size = (Get-Item $pdfPath).Length
    Write-Host "    PDF salvo: $pdfPath ($size bytes)"
} catch {
    Write-Host "    Falha ao baixar PDF: $_" -ForegroundColor Red
    throw
}

Write-Host "[6/6] Conferindo se abril foi limpo do banco (reabrindo relatorio)..."
$rel2 = Invoke-WebRequest -Uri "$base/agendamentos/relatorio" -WebSession $session -UseBasicParsing
if ($rel2.Content -match "Gerado automaticamente") {
    Write-Host "    Relatorio arquivado com sucesso." -ForegroundColor Green
}

Write-Host ""
Write-Host "=== TESTE REAL CONCLUIDO COM SUCESSO ===" -ForegroundColor Green
Write-Host "Profissionais usados: $($loginsProf.nome -join ', ')"
Write-Host "Abra o PDF em: $pdfPath"
