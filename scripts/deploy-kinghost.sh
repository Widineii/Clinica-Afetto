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

git fetch origin main
git reset --hard origin/main

echo "==> Versao local apos atualizacao:"
git log -1 --oneline

echo "==> Rebuild e restart (pode levar alguns minutos)..."
docker compose -f "$COMPOSE_FILE" up -d --build

echo "==> Health:"
sleep 5
curl -sf "http://localhost:8080/actuator/health" || echo "Aguardando app subir — veja logs abaixo."

echo "Pronto. Confira no navegador a versao em application.properties (login / rodape)."
echo "Logs: docker compose -f $COMPOSE_FILE logs -f app"
