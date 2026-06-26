package com.clinica.sistema.config;

import com.clinica.sistema.service.BloqueioPagamentoEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BloqueioPagamentoEmailScheduler {

    private static final Logger log = LoggerFactory.getLogger(BloqueioPagamentoEmailScheduler.class);
    private static final String FUSO_BRASIL = "America/Sao_Paulo";

    private final BloqueioPagamentoEmailService bloqueioPagamentoEmailService;

    public BloqueioPagamentoEmailScheduler(BloqueioPagamentoEmailService bloqueioPagamentoEmailService) {
        this.bloqueioPagamentoEmailService = bloqueioPagamentoEmailService;
    }

    /** Bloqueio por pagamento: 1 e-mail por dia as 8h enquanto a agenda estiver bloqueada. */
    @Scheduled(
            cron = "${app.aviso-pagamento.email.cron-bloqueio-diario:0 0 8 * * *}",
            zone = FUSO_BRASIL
    )
    public void avisoBloqueioDiario() {
        if (!bloqueioPagamentoEmailService.avisoBloqueioAtivo()) {
            return;
        }
        try {
            bloqueioPagamentoEmailService.processarAvisoDiarioBloqueio();
        } catch (RuntimeException ex) {
            log.warn("Falha no e-mail diario de bloqueio por pagamento: {}", ex.getMessage());
        }
    }
}
