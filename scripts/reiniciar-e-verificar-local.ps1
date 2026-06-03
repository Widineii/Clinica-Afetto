# Reinicia o app local (8081), compila e verifica se o build contém o marcador esperado.
# Uso: .\scripts\reiniciar-e-verificar-local.ps1
#      .\scripts\reiniciar-e-verificar-local.ps1 -Marcador "usarLocacaoTurno"

param(
    [string]$Marcador = "usarLocacaoTurno"
)

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

Write-Host ">> Encerrando processo na porta 8081..."
Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }

Write-Host ">> Compilando (mvn compile)..."
& .\mvnw.cmd -q compile
if ($LASTEXITCODE -ne 0) { throw "Falha no compile." }

$template = Join-Path $raiz "target\classes\templates\agenda.html"
if (-not (Test-Path $template)) {
    throw "Template compilado nao encontrado: $template"
}
if (-not (Select-String -Path $template -Pattern $Marcador -Quiet)) {
    throw "Marcador '$Marcador' NAO encontrado em target/classes. Alteracao nao entrou no build."
}
Write-Host "OK: marcador '$Marcador' presente em target/classes."

Write-Host ">> Subindo Spring Boot (perfil local) em segundo plano..."
$log = Join-Path $raiz "target\spring-boot-local.log"
$errLog = Join-Path $raiz "target\spring-boot-local.err.log"
Start-Process -FilePath ".\mvnw.cmd" `
    -ArgumentList "spring-boot:run", "-Dspring-boot.run.profiles=local" `
    -WorkingDirectory $raiz `
    -WindowStyle Hidden `
    -RedirectStandardOutput $log `
    -RedirectStandardError $errLog

$deadline = (Get-Date).AddMinutes(3)
$ok = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3
    try {
        $code = (curl.exe -s -o NUL -w "%{http_code}" http://localhost:8081/login 2>$null)
        if ($code -eq "200") {
            $body = curl.exe -s http://localhost:8081/login 2>$null
            if ($body -match "v2\.702") {
                $ok = $true
                break
            }
        }
    } catch { }
}
if (-not $ok) {
    Write-Host "AVISO: servidor ainda nao respondeu em http://localhost:8081/login - confira $log"
    exit 1
}

Write-Host "OK: http://localhost:8081/login responde (v2.702)."
Write-Host "Abra a agenda com Ctrl+F5: http://localhost:8081/agendamentos/dashboard"
