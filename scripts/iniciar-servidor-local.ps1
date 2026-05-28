# Sobe a aplicacao no perfil local (porta 8081, banco em ./data).
$ErrorActionPreference = "Stop"
$projeto = Split-Path $PSScriptRoot -Parent
Set-Location $projeto

$p = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($p) {
    Write-Host "Servidor ja esta na porta 8081 (PID $($p.OwningProcess))."
    Write-Host "Abra: http://localhost:8081/agendamentos/relatorio"
    exit 0
}

Write-Host "Iniciando clinica-agenda (perfil local)..."
& .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
