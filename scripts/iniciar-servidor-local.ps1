# Sobe a aplicacao no perfil local (porta 8081, banco em ./data).
$ErrorActionPreference = "Stop"
$projeto = Split-Path $PSScriptRoot -Parent
Set-Location $projeto

$mailEnv = Join-Path $PSScriptRoot "local-mail.env"
if (Test-Path $mailEnv) {
    . (Join-Path $PSScriptRoot "carregar-env-arquivo.ps1")
    Import-DotEnvFile -Path $mailEnv | Out-Null
    Write-Host "Variaveis de e-mail carregadas de scripts\local-mail.env"
}

$p = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($p) {
    Write-Host "Servidor ja esta na porta 8081 (PID $($p.OwningProcess))."
    Write-Host "Abra: http://localhost:8081/agendamentos/relatorio"
    exit 0
}

Write-Host "Iniciando clinica-agenda (perfil local)..."
& .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
