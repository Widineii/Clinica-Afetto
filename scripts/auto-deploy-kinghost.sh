#!/usr/bin/env bash
# Mantem o VPS na mesma versao da branch main do GitHub.
# Rode pelo systemd timer instalado por install-auto-deploy-kinghost.sh.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/clinica-afetto}"
BRANCH="${BRANCH:-main}"
REMOTE="${REMOTE:-origin}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.kinghost.yml}"
LOCK_FILE="/tmp/clinica-afetto-auto-deploy.lock"

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "Auto-deploy ja esta rodando. Saindo."
  exit 0
fi

cd "$APP_DIR"

echo "==> $(date -Is) conferindo $REMOTE/$BRANCH em $(pwd)"
git fetch "$REMOTE" "$BRANCH"

LOCAL_COMMIT="$(git rev-parse HEAD)"
REMOTE_COMMIT="$(git rev-parse "$REMOTE/$BRANCH")"

if [ "$LOCAL_COMMIT" = "$REMOTE_COMMIT" ]; then
  echo "==> Sem atualizacao. Commit atual: $LOCAL_COMMIT"
  exit 0
fi

echo "==> Atualizacao encontrada:"
echo "    local : $LOCAL_COMMIT"
echo "    remoto: $REMOTE_COMMIT"

git reset --hard "$REMOTE/$BRANCH"
chmod +x scripts/deploy-kinghost.sh

echo "==> Rebuild e restart..."
docker compose -f "$COMPOSE_FILE" up -d --build

echo "==> Health:"
sleep 8
curl -sf "http://localhost:8080/actuator/health" || {
  echo "ERRO: app nao respondeu healthcheck. Veja logs:"
  docker compose -f "$COMPOSE_FILE" logs --tail=120 app
  exit 1
}

echo
echo "==> Deploy automatico concluido no commit $REMOTE_COMMIT"
