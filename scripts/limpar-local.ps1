# Zera agendamentos e relatorios no ambiente local (H2 em ./data)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host "Parando servidor na porta 8081..." -ForegroundColor Yellow
Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 2

$dataDir = Join-Path $root "data"
if (Test-Path $dataDir) {
    Write-Host "Apagando banco local H2 em $dataDir ..." -ForegroundColor Cyan
    Get-ChildItem -Path $dataDir -Filter "clinica-local*" -ErrorAction SilentlyContinue |
        Remove-Item -Force -ErrorAction SilentlyContinue
}

$env:SPRING_PROFILES_ACTIVE = "local"
Write-Host "Subindo agenda limpa em http://localhost:8081 ..." -ForegroundColor Green
Write-Host "Login: admin / Luquinha12@" -ForegroundColor Gray
Write-Host "Agenda zerada: sem agendamentos, sem relatorios arquivados." -ForegroundColor Gray
Write-Host "Usuarios e salas sao recriados no primeiro start (admin + equipe)." -ForegroundColor Gray
Write-Host "Relatorio mensal: mesmo comportamento da producao (fecha no dia 3)." -ForegroundColor Gray
.\mvnw.cmd spring-boot:run
