@echo off
chcp 65001 >nul
setlocal EnableExtensions

cd /d "%~dp0.."

set "MSG=%~*"
if "%MSG%"=="" set "MSG=Atualizacao Agenda Afetto"

echo.
echo === Subir para GitHub (branch main) ===
echo.

git status --short
if errorlevel 1 (
    echo ERRO: pasta nao e um repositorio git.
    exit /b 1
)

git add -A
git reset HEAD c2.txt l2.html login.html dashboard-live.html m2.html mensagem.html post.html cookies.txt 2>nul

git diff --cached --quiet
if not errorlevel 1 (
    echo Nada novo para commitar. So fazendo push...
    git push origin main
    if errorlevel 1 (
        echo ERRO: push falhou.
        exit /b 1
    )
    echo.
    echo OK: GitHub atualizado.
    exit /b 0
)

git commit -m "%MSG%"
if errorlevel 1 (
    echo ERRO: commit falhou.
    exit /b 1
)

git push origin main
if errorlevel 1 (
    echo ERRO: push falhou.
    exit /b 1
)

echo.
echo OK: codigo no GitHub. Commit: %MSG%
echo.
exit /b 0
