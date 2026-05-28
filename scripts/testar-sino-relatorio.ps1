# Abre o teste local do sino de relatorio mensal
$base = "http://localhost:8081"
Write-Host ""
Write-Host "=== Teste do sino - Relatorio mensal ===" -ForegroundColor Cyan
Write-Host "URL:  $base/agendamentos/dashboard"
Write-Host "Login: admin"
Write-Host "Senha: Luquinha12@"
Write-Host ""
Write-Host "Passo a passo:"
Write-Host "  1. Faca login (se pedir)"
Write-Host "  2. No topo direito, veja o icone de SINO (bolinha laranja)"
Write-Host "  3. Clique no sino -> leia a mensagem do mes passado"
Write-Host "  4. Clique em 'Abrir relatorio e baixar PDF'"
Write-Host "  5. Na pagina de relatorio: aviso verde + botao Baixar PDF"
Write-Host ""
Write-Host "Cenario 'ainda nao gerou' (opcional):"
Write-Host "  - Com o servidor rodando (perfil local), no PowerShell:"
Write-Host "    Invoke-WebRequest -Uri '$base/agendamentos/relatorio/dev/resetar-arquivo-mes-passado' -Method POST -UseBasicParsing"
Write-Host "  - Atualize a agenda (F5): o sino dira 'pronto para gerar'"
Write-Host ""

try {
    $r = Invoke-WebRequest -Uri "$base/login" -UseBasicParsing -TimeoutSec 3
    if ($r.StatusCode -eq 200) {
        Write-Host "Servidor OK na porta 8081." -ForegroundColor Green
    }
} catch {
    Write-Host "Servidor ainda nao respondeu. Aguarde ~30s e rode de novo, ou inicie:" -ForegroundColor Yellow
    Write-Host "  .\mvnw.cmd spring-boot:run `"-Dspring-boot.run.profiles=local`""
}

Start-Process "$base/login"
