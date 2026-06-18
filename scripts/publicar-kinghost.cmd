@echo off
chcp 65001 >nul
setlocal EnableExtensions

echo.
echo ========================================
echo   Publicar Agenda Afetto na KingHost
echo ========================================
echo.

call "%~dp0subir-github.cmd" %*
if errorlevel 1 exit /b 1

echo Aguardando 3 segundos antes do deploy...
timeout /t 3 /nobreak >nul

call "%~dp0deploy-kinghost.cmd"
exit /b %ERRORLEVEL%
