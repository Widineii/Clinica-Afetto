package com.clinica.sistema.config;

import com.clinica.sistema.service.PagamentoConsultaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PagamentoExpiracaoScheduler {

    private static final Logger log = LoggerFactory.getLogger(PagamentoExpiracaoScheduler.class);

    private final PagamentoConsultaService pagamentoConsultaService;

    public PagamentoExpiracaoScheduler(PagamentoConsultaService pagamentoConsultaService) {
        this.pagamentoConsultaService = pagamentoConsultaService;
    }

    @Scheduled(fixedDelayString = "${app.pagamento.cron-expiracao-ms:30000}")
    public void expirarPagamentosVencidos() {
        int removidos = pagamentoConsultaService.expirarPagamentosVencidos();
        if (removidos > 0) {
            log.info("Removidos {} agendamento(s) com pagamento expirado.", removidos);
        }
        int janelas = pagamentoConsultaService.abrirJanelasPagamentoVespera();
        if (janelas > 0) {
            log.info("Abertas {} janela(s) de pagamento da vespera.", janelas);
        }
        int liberados = pagamentoConsultaService.liberarVagasPorFaltaPagamento();
        if (liberados > 0) {
            log.info("Liberadas {} vaga(s) por falta de pagamento ate 23:59.", liberados);
        }
    }
}
