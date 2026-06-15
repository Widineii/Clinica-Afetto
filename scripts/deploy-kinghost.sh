#!/usr/bin/env bash
# Atualiza o app na KingHost (VPS). Rode NO SERVIDOR, dentro da pasta do projeto.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/clinica-afetto}"
COMPOSE_FILE="docker-compose.kinghost.yml"

cd "$APP_DIR"

echo "==> Pasta: $(pwd)"
echo "==> Versao no GitHub (remoto):"
git fetch origin main
git log -1 --oneline origin/main

echo "==> Versao local antes do pull:"
git log -1 --oneline || true

git pull origin main

echo "==> Versao local apos pull:"
git log -1 --oneline

echo "==> Rebuild e restart (pode levar alguns minutos)..."
docker compose -f "$COMPOSE_FILE" up -d --build

echo "==> Health:"
sleep 5
curl -sf "http://localhost:8080/actuator/health" || echo "Aguardando app subir — veja logs abaixo."

echo ""
echo "Pronto. Confira no navegador: login deve mostrar v2.817 (ou versao em application.properties)."
echo "Logs: docker compose -f $COMPOSE_FILE logs -f app"
