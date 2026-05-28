# Abre o teste local do relatorio semanal
$base = "http://localhost:8081"
Write-Host ""
Write-Host "=== Teste do relatorio semanal ===" -ForegroundColor Cyan
Write-Host "URL direta: $base/agendamentos/relatorio/semanal"
Write-Host "Login: admin / Luquinha12@"
Write-Host ""
Write-Host "Passo a passo:"
Write-Host "  1. Faca login"
Write-Host "  2. Abra Relatorio mensal -> botao 'Relatorio semanal'"
Write-Host "     OU use a URL direta acima"
Write-Host "  3. (Local) Clique em 'Popular dados de teste da semana'"
Write-Host "  4. Veja a tabela (3 colunas) e totais da semana (segunda ate hoje)"
Write-Host "  5. Consultas com menos de 24h NAO entram (ex.: recente-menos-24h)"
Write-Host "  6. Clique 'Baixar PDF semanal'"
Write-Host "  7. Volte para agenda: dados da sessao sao apagados"
Write-Host ""

try {
    $r = Invoke-WebRequest -Uri "$base/login" -UseBasicParsing -TimeoutSec 3
    if ($r.StatusCode -eq 200) {
        Write-Host "Servidor OK na porta 8081." -ForegroundColor Green
    }
} catch {
    Write-Host "Servidor parado. Inicie com:" -ForegroundColor Yellow
    Write-Host "  cd clinica-agenda-main\clinica-agenda-main"
    Write-Host "  .\mvnw.cmd spring-boot:run `"-Dspring-boot.run.profiles=local`""
}

Start-Process "$base/agendamentos/relatorio/semanal"
