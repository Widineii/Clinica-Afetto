package com.clinica.sistema.config;

import com.clinica.sistema.service.RelatorioMensalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RelatorioMensalScheduler {

    private static final Logger log = LoggerFactory.getLogger(RelatorioMensalScheduler.class);

    private final RelatorioMensalService relatorioMensalService;

    public RelatorioMensalScheduler(RelatorioMensalService relatorioMensalService) {
        this.relatorioMensalService = relatorioMensalService;
    }

    /** Dia 3 de cada mes as 03:00 — gera relatorio e limpa mes passado sem ninguem clicar em nada. */
    @Scheduled(cron = "${app.relatorio-mensal.cron-dia-3:0 0 3 3 * *}")
    public void fechamentoNoDia3() {
        executar("dia-3");
    }

    /** Dia 10 de cada mes as 04:00 — remove PDFs antigos para liberar espaco no banco. */
    @Scheduled(cron = "${app.relatorio-mensal.cron-dia-10-pdf:0 0 4 10 * *}")
    public void remocaoPdfNoDia10() {
        executarRemocaoPdf("dia-10");
    }

    /** Todo dia as 06:00 — se o dia 3 o servidor estava dormindo, tenta de novo ate arquivar. */
    @Scheduled(cron = "${app.relatorio-mensal.cron-fallback:0 0 6 * * *}")
    public void fechamentoComplementar() {
        executar("fallback");
        executarRemocaoPdf("fallback");
    }

    private void executar(String origem) {
        try {
            boolean executou = relatorioMensalService.executarFechamentoAutomaticoSeDevido();
            if (executou) {
                log.info("Fechamento mensal automatico concluido (origem={}).", origem);
            }
        } catch (RuntimeException e) {
            log.error("Falha no fechamento mensal automatico (origem={}).", origem, e);
        }
    }

    private void executarRemocaoPdf(String origem) {
        try {
            int removidos = relatorioMensalService.removerPdfsExpiradosSeDevido();
            if (removidos > 0) {
                log.info("Remocao de PDFs de relatorio concluida (origem={}, quantidade={}).", origem, removidos);
            }
        } catch (RuntimeException e) {
            log.error("Falha na remocao automatica de PDFs (origem={}).", origem, e);
        }
    }
}
