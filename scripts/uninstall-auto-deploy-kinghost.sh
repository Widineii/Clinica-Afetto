#!/usr/bin/env bash
# Remove o timer de auto-deploy do VPS.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "ERRO: rode como root no VPS."
  exit 1
fi

SERVICE_NAME="clinica-afetto-auto-deploy"

systemctl disable --now "${SERVICE_NAME}.timer" 2>/dev/null || true
rm -f "/etc/systemd/system/${SERVICE_NAME}.service" "/etc/systemd/system/${SERVICE_NAME}.timer"
systemctl daemon-reload

echo "Auto-deploy removido."
