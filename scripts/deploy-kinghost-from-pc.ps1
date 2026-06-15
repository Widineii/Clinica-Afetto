# Deploy na KingHost a partir do Windows (precisa de OpenSSH).
# Uso:
#   $env:KINGHOST_HOST = "177.x.x.x"
#   $env:KINGHOST_USER = "root"
#   .\scripts\deploy-kinghost-from-pc.ps1

param(
    [string]$Host = $env:KINGHOST_HOST,
    [string]$User = $env:KINGHOST_USER
)

if (-not $Host -or -not $User) {
    Write-Error "Defina KINGHOST_HOST e KINGHOST_USER (ex.: 177.x.x.x e root)."
    exit 1
}

$remote = "${User}@${Host}"
Write-Host "Conectando em $remote ..."

ssh $remote @"
set -euo pipefail
cd /opt/clinica-afetto
git fetch origin main
git reset --hard origin/main
chmod +x scripts/deploy-kinghost.sh
./scripts/deploy-kinghost.sh
"@

if ($LASTEXITCODE -ne 0) {
    Write-Error "Deploy falhou (exit $LASTEXITCODE)."
    exit $LASTEXITCODE
}

Write-Host "Deploy concluido. Confira a versao em http://${Host}:8080/login"
