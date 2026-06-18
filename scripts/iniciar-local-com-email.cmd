@echo off
chcp 65001 >nul
title Agenda Afetto - local com e-mail real
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar-local-com-email.ps1"
pause
