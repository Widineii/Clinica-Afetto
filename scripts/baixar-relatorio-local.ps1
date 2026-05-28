# Baixa o PDF do relatorio mensal para a pasta Downloads (ambiente local).
$ErrorActionPreference = "Stop"
$base = "http://localhost:8081"
$destino = Join-Path $env:USERPROFILE "Downloads\relatorio-afetto-local.pdf"
$projeto = Split-Path $PSScriptRoot -Parent

function Get-CsrfFromHtml([string]$html) {
    if ($html -match 'name="_csrf"\s+value="([^"]+)"') { return $matches[1] }
    throw "CSRF nao encontrado. Abra $base/login no navegador."
}

function Test-ServidorLocal {
    try {
        $r = Invoke-WebRequest -Uri "$base/login" -UseBasicParsing -TimeoutSec 4
        return $r.StatusCode -eq 200
    } catch { return $false }
}

Write-Host "=== BAIXAR RELATORIO (LOCAL) ===" -ForegroundColor Cyan

if (-not (Test-ServidorLocal)) {
    Write-Host "Servidor parado. Iniciando em $projeto ..." -ForegroundColor Yellow
    Start-Process -FilePath "powershell" -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $PSScriptRoot "iniciar-servidor-local.ps1")
    ) -WorkingDirectory $projeto
    $tentativas = 0
    while (-not (Test-ServidorLocal) -and $tentativas -lt 60) {
        Start-Sleep -Seconds 2
        $tentativas++
    }
    if (-not (Test-ServidorLocal)) {
        throw "Servidor nao subiu em 2 minutos. Rode manualmente: scripts\iniciar-servidor-local.ps1"
    }
}

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Write-Host "[1/3] Login admin..."
$loginPage = Invoke-WebRequest -Uri "$base/login" -WebSession $session -UseBasicParsing
$csrf = Get-CsrfFromHtml $loginPage.Content
Invoke-WebRequest -Uri "$base/login" -Method Post -WebSession $session -UseBasicParsing -Body @{
    login = "admin"
    senha = "Luquinha12@"
    _csrf = $csrf
} | Out-Null

Write-Host "[2/3] Abrindo relatorio (gera arquivo do mes passado se precisar)..."
$rel = Invoke-WebRequest -Uri "$base/agendamentos/relatorio" -WebSession $session -UseBasicParsing
if ($rel.Content -notmatch "btn-export" -and $rel.Content -notmatch "download") {
    Write-Host "    Botao de PDF nao apareceu. Rodando preparacao de dados..." -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot "teste-relatorio-real.ps1")
    $rel = Invoke-WebRequest -Uri "$base/agendamentos/relatorio" -WebSession $session -UseBasicParsing
}

Write-Host "[3/3] Baixando PDF..."
$pdf = Invoke-WebRequest -Uri "$base/agendamentos/relatorio/mensal/download" -WebSession $session -UseBasicParsing
[IO.File]::WriteAllBytes($destino, $pdf.Content)

Write-Host ""
Write-Host "PDF salvo em:" -ForegroundColor Green
Write-Host "  $destino"
Write-Host "  ($($pdf.Content.Length) bytes)"
Write-Host ""
Write-Host "No navegador: $base/agendamentos/relatorio" -ForegroundColor Cyan
Write-Host "Login: admin / Luquinha12@"
Start-Process $destino
