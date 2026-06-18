# Sobe o servidor local com SMTP (Gmail) para teste real de recuperacao de senha.
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$envFile = Join-Path $PSScriptRoot "local-mail.env"
$exampleFile = Join-Path $PSScriptRoot "local-mail.env.example"

if (-not (Test-Path $envFile)) {
    Copy-Item $exampleFile $envFile
    Write-Host ""
    Write-Host "Criei scripts\local-mail.env" -ForegroundColor Yellow
    Write-Host "Cole a senha de app do Gmail em MAIL_PASSWORD, salve e rode este script de novo." -ForegroundColor Yellow
    Write-Host "https://myaccount.google.com/apppasswords" -ForegroundColor Cyan
    Write-Host ""
    Start-Process notepad.exe $envFile
    exit 1
}

. (Join-Path $PSScriptRoot "carregar-env-arquivo.ps1")
Import-DotEnvFile -Path $envFile | Out-Null

$senhaPlaceholder = @('', 'COLE_A_SENHA_DE_APP_AQUI')
if ($senhaPlaceholder -contains $env:MAIL_PASSWORD) {
    Write-Host ""
    Write-Host "MAIL_PASSWORD ainda nao configurada em scripts\local-mail.env" -ForegroundColor Red
    Write-Host "Abra o arquivo, cole a senha de app do Gmail, salve e rode de novo." -ForegroundColor Yellow
    Write-Host ""
    Start-Process notepad.exe $envFile
    exit 1
}

if (-not $env:RECUPERACAO_SENHA_MODO_CONSOLA) {
    $env:RECUPERACAO_SENHA_MODO_CONSOLA = "false"
}

Write-Host "Parando processos na porta 8081..." -ForegroundColor Yellow
Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 2

Write-Host ""
Write-Host "E-mail real ativo:" -ForegroundColor Green
Write-Host "  Remetente: $($env:MAIL_USERNAME)"
Write-Host "  Modo consola: $($env:RECUPERACAO_SENHA_MODO_CONSOLA)"
Write-Host ""
Write-Host "Subindo em http://localhost:8081 ..." -ForegroundColor Green
Write-Host "Teste: http://localhost:8081/senha/esqueci" -ForegroundColor Cyan
Write-Host ""

& .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
