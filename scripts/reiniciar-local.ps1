# Reinicia o Agenda Afetto local com build limpo (porta 8081)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host "Parando processos na porta 8081..." -ForegroundColor Yellow
Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 2

$env:SPRING_PROFILES_ACTIVE = "local"
Write-Host "Compilando..." -ForegroundColor Cyan
.\mvnw.cmd -q clean compile -DskipTests
Write-Host "Subindo em http://localhost:8081 ..." -ForegroundColor Green
.\mvnw.cmd spring-boot:run
