package com.clinica.sistema.config;

import com.clinica.sistema.model.AvisoPagamentoEmailJanela;
import com.clinica.sistema.service.AvisoPagamentoEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AvisoPagamentoEmailScheduler {

    private static final Logger log = LoggerFactory.getLogger(AvisoPagamentoEmailScheduler.class);
    private static final String FUSO_BRASIL = "America/Sao_Paulo";

    private final AvisoPagamentoEmailService avisoPagamentoEmailService;

    public AvisoPagamentoEmailScheduler(AvisoPagamentoEmailService avisoPagamentoEmailService) {
        this.avisoPagamentoEmailService = avisoPagamentoEmailService;
    }

    /** Diário: 8h. */
    @Scheduled(cron = "${app.aviso-pagamento.email.cron-diario-manha:0 0 8 * * *}", zone = FUSO_BRASIL)
    public void avisoDiarioManha() {
        executar("diario 8h", AvisoPagamentoEmailJanela.DIARIO_MANHA);
    }

    /** Diário: 17h. */
    @Scheduled(cron = "${app.aviso-pagamento.email.cron-diario-tarde:0 0 17 * * *}", zone = FUSO_BRASIL)
    public void avisoDiarioTarde() {
        executar("diario 17h", AvisoPagamentoEmailJanela.DIARIO_TARDE);
    }

    @Scheduled(cron = "${app.aviso-pagamento.email.cron-semanal-sabado:0 0 17 * * SAT}", zone = FUSO_BRASIL)
    public void avisoSemanalSabado() {
        executar("semanal sabado 17h", AvisoPagamentoEmailJanela.SEMANAL_SABADO_TARDE);
    }

    @Scheduled(cron = "${app.aviso-pagamento.email.cron-semanal-domingo-manha:0 0 8 * * SUN}", zone = FUSO_BRASIL)
    public void avisoSemanalDomingoManha() {
        executar("semanal domingo 8h", AvisoPagamentoEmailJanela.SEMANAL_DOMINGO_MANHA);
    }

    @Scheduled(cron = "${app.aviso-pagamento.email.cron-semanal-domingo-tarde:0 0 17 * * SUN}", zone = FUSO_BRASIL)
    public void avisoSemanalDomingoTarde() {
        executar("semanal domingo 17h", AvisoPagamentoEmailJanela.SEMANAL_DOMINGO_TARDE);
    }

    @Scheduled(cron = "${app.aviso-pagamento.email.cron-mensal-dia-1:0 0 8 1 * *}", zone = FUSO_BRASIL)
    public void avisoMensalDia1() {
        executar("mensal dia 1", AvisoPagamentoEmailJanela.MENSAL_DIA1);
    }

    @Scheduled(cron = "${app.aviso-pagamento.email.cron-mensal-dia-5:0 0 8 5 * *}", zone = FUSO_BRASIL)
    public void avisoMensalDia5() {
        executar("mensal dia 5", AvisoPagamentoEmailJanela.MENSAL_DIA5);
    }

    @Scheduled(cron = "${app.aviso-pagamento.email.cron-mensal-dia-10-manha:0 0 8 10 * *}", zone = FUSO_BRASIL)
    public void avisoMensalDia10Manha() {
        executar("mensal dia 10 8h", AvisoPagamentoEmailJanela.MENSAL_DIA10_MANHA);
    }

    @Scheduled(cron = "${app.aviso-pagamento.email.cron-mensal-dia-10-tarde:0 0 17 10 * *}", zone = FUSO_BRASIL)
    public void avisoMensalDia10Tarde() {
        executar("mensal dia 10 17h", AvisoPagamentoEmailJanela.MENSAL_DIA10_TARDE);
    }

    private void executar(String rotulo, AvisoPagamentoEmailJanela janela) {
        if (!avisoPagamentoEmailService.avisoAutomaticoAtivo()) {
            return;
        }
        try {
            avisoPagamentoEmailService.processarJanela(janela);
        } catch (RuntimeException ex) {
            log.warn("Falha no aviso de pagamento por e-mail ({}): {}", rotulo, ex.getMessage());
        }
    }
}
