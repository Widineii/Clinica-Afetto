@echo off
chcp 65001 >nul
setlocal EnableExtensions

set "ENV_FILE=%~dp0kinghost.env"
if not exist "%ENV_FILE%" (
    echo.
    echo ERRO: falta o arquivo scripts\kinghost.env
    echo.
    echo Faca UMA VEZ:
    echo   copy scripts\kinghost.env.example scripts\kinghost.env
    echo   notepad scripts\kinghost.env
    echo.
    echo Preencha IP do VPS, usuario root e caminho da chave SSH.
    exit /b 1
)

for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ENV_FILE%") do (
    if /i "%%a"=="KINGHOST_HOST" set "KINGHOST_HOST=%%b"
    if /i "%%a"=="KINGHOST_USER" set "KINGHOST_USER=%%b"
    if /i "%%a"=="KINGHOST_SSH_KEY" set "KINGHOST_SSH_KEY=%%b"
)

set "KINGHOST_HOST=%KINGHOST_HOST: =%"
set "KINGHOST_USER=%KINGHOST_USER: =%"
set "KINGHOST_SSH_KEY=%KINGHOST_SSH_KEY: =%"

if "%KINGHOST_HOST%"=="" (
    echo ERRO: KINGHOST_HOST vazio em kinghost.env
    exit /b 1
)
if "%KINGHOST_USER%"=="" set "KINGHOST_USER=root"

echo.
echo === Deploy KingHost ===
echo Servidor: %KINGHOST_USER%@%KINGHOST_HOST%
echo.

where ssh >nul 2>&1
if errorlevel 1 (
    echo ERRO: comando ssh nao encontrado. Instale OpenSSH Client no Windows.
    exit /b 1
)

set "SSH_OPTS="
if not "%KINGHOST_SSH_KEY%"=="" (
    if not exist "%KINGHOST_SSH_KEY%" (
        echo ERRO: chave SSH nao encontrada: %KINGHOST_SSH_KEY%
        exit /b 1
    )
    set "SSH_OPTS=-i %KINGHOST_SSH_KEY%"
)

ssh %SSH_OPTS% -o StrictHostKeyChecking=accept-new %KINGHOST_USER%@%KINGHOST_HOST% "set -euo pipefail; cd /opt/clinica-afetto; git fetch origin main; git reset --hard origin/main; chmod +x scripts/deploy-kinghost.sh; ./scripts/deploy-kinghost.sh"

if errorlevel 1 (
    echo.
    echo ERRO: deploy falhou.
    exit /b 1
)

echo.
echo OK: deploy concluido.
echo Site: http://%KINGHOST_HOST%:8080/login
echo.
exit /b 0
