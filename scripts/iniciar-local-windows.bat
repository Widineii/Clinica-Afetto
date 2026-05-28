@echo off
chcp 65001 >nul
title Agenda Afetto - servidor local
cd /d "%~dp0.."

echo.
echo  Agenda Afetto - iniciando na porta 8081...
echo  Quando aparecer "Started", abra no navegador:
echo  http://localhost:8081/login
echo.
echo  Login: admin
echo  Senha: Luquinha12@
echo.

call mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

pause
