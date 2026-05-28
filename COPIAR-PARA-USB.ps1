# Clique direito -> Executar com PowerShell
# Ou: powershell -ExecutionPolicy Bypass -File COPIAR-PARA-USB.ps1

$origem = $PSScriptRoot
$nomePasta = "clinica-agenda-main"

$usb = Get-CimInstance Win32_LogicalDisk -ErrorAction SilentlyContinue |
    Where-Object { $_.DriveType -eq 2 -and $_.Size -gt 0 } |
    Select-Object -First 1

if (-not $usb) {
    Write-Host "Pendrive nao encontrado. Conecte o USB de 8GB e rode de novo." -ForegroundColor Yellow
    Write-Host "Alternativa: copie manualmente a pasta:" -ForegroundColor Yellow
    Write-Host "  $origem" -ForegroundColor Cyan
    Write-Host "para o pendrive (pasta $nomePasta)." -ForegroundColor Yellow
    Read-Host "Enter para sair"
    exit 1
}

$letra = $usb.DeviceID
$destino = Join-Path $letra $nomePasta
$livreGb = [math]::Round($usb.FreeSpace / 1GB, 2)

Write-Host "USB: $letra ($livreGb GB livres)" -ForegroundColor Green
Write-Host "Copiando para: $destino" -ForegroundColor Green
Write-Host "Aguarde alguns minutos..." -ForegroundColor Yellow

if (Test-Path $destino) {
    Remove-Item $destino -Recurse -Force -ErrorAction SilentlyContinue
}

$robocopy = robocopy $origem $destino /E /XD ".git" ".cursor" "agent-transcripts" /XF "*.log" /NFL /NDL /NJH /NJS /nc /ns /np
if ($LASTEXITCODE -ge 8) {
    Write-Host "Erro na copia (codigo $LASTEXITCODE)." -ForegroundColor Red
    Read-Host "Enter para sair"
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Pronto! Pasta no USB: $destino" -ForegroundColor Green
Write-Host "Abra COMO-RODAR-NA-REUNIAO.txt no pendrive." -ForegroundColor Cyan
Read-Host "Enter para sair"
