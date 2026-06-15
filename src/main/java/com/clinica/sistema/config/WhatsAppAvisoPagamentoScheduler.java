package com.clinica.sistema.config;

import com.clinica.sistema.service.WhatsAppAvisoPagamentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppAvisoPagamentoScheduler {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppAvisoPagamentoScheduler.class);
    private static final String FUSO_BRASIL = "America/Sao_Paulo";

    private final WhatsAppAvisoPagamentoService avisoPagamentoService;

    public WhatsAppAvisoPagamentoScheduler(WhatsAppAvisoPagamentoService avisoPagamentoService) {
        this.avisoPagamentoService = avisoPagamentoService;
    }

    @Scheduled(
            cron = "${app.whatsapp.meta.aviso-pagamento.cron-diario:0 0 10 * * *}",
            zone = FUSO_BRASIL
    )
    public void avisoPagamentoDiario() {
        executar("diario", avisoPagamentoService::processarAvisosDiario);
    }

    @Scheduled(
            cron = "${app.whatsapp.meta.aviso-pagamento.cron-semanal-sabado:0 0 13 * * SAT}",
            zone = FUSO_BRASIL
    )
    public void avisoPagamentoSemanalSabado() {
        executar("semanal sabado", avisoPagamentoService::processarAvisosSemanalSabado);
    }

    @Scheduled(
            cron = "${app.whatsapp.meta.aviso-pagamento.cron-semanal-domingo:0 0 13 * * SUN}",
            zone = FUSO_BRASIL
    )
    public void avisoPagamentoSemanalDomingo() {
        executar("semanal domingo", avisoPagamentoService::processarAvisosSemanalDomingo);
    }

    @Scheduled(
            cron = "${app.whatsapp.meta.aviso-pagamento.cron-mensal-dia-5:0 0 13 5 * *}",
            zone = FUSO_BRASIL
    )
    public void avisoPagamentoMensalDia5() {
        executar("mensal dia 5", avisoPagamentoService::processarAvisosMensalDia5);
    }

    @Scheduled(
            cron = "${app.whatsapp.meta.aviso-pagamento.cron-mensal-dia-10:0 0 13 10 * *}",
            zone = FUSO_BRASIL
    )
    public void avisoPagamentoMensalDia10() {
        executar("mensal dia 10", avisoPagamentoService::processarAvisosMensalDia10);
    }

    private void executar(String rotulo, Runnable tarefa) {
        if (!avisoPagamentoService.avisoAutomaticoAtivo()) {
            return;
        }
        try {
            tarefa.run();
        } catch (RuntimeException ex) {
            log.warn("Falha no aviso WhatsApp de pagamento ({}): {}", rotulo, ex.getMessage());
        }
    }
}
