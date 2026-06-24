#!/usr/bin/env bash
# Instala um timer systemd para atualizar o VPS automaticamente a partir do GitHub.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "ERRO: rode como root no VPS."
  exit 1
fi

APP_DIR="${APP_DIR:-/opt/clinica-afetto}"
BRANCH="${BRANCH:-main}"
INTERVAL="${INTERVAL:-5min}"
SERVICE_NAME="clinica-afetto-auto-deploy"

if [ ! -d "$APP_DIR/.git" ]; then
  echo "ERRO: $APP_DIR nao parece ser um clone Git."
  echo "Baixe o projeto em /opt/clinica-afetto antes de instalar."
  exit 1
fi

cd "$APP_DIR"
chmod +x scripts/auto-deploy-kinghost.sh scripts/deploy-kinghost.sh

cat >"/etc/systemd/system/${SERVICE_NAME}.service" <<SERVICE
[Unit]
Description=Clinica Afetto auto deploy from GitHub
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=oneshot
WorkingDirectory=${APP_DIR}
Environment=APP_DIR=${APP_DIR}
Environment=BRANCH=${BRANCH}
ExecStart=${APP_DIR}/scripts/auto-deploy-kinghost.sh
SERVICE

cat >"/etc/systemd/system/${SERVICE_NAME}.timer" <<TIMER
[Unit]
Description=Run Clinica Afetto auto deploy every ${INTERVAL}

[Timer]
OnBootSec=2min
OnUnitActiveSec=${INTERVAL}
AccuracySec=30s
Persistent=true
Unit=${SERVICE_NAME}.service

[Install]
WantedBy=timers.target
TIMER

systemctl daemon-reload
systemctl enable --now "${SERVICE_NAME}.timer"

echo "Auto-deploy instalado."
echo "Status: systemctl status ${SERVICE_NAME}.timer"
echo "Logs:   journalctl -u ${SERVICE_NAME}.service -f"
