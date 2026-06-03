package com.clinica.sistema.config;

import com.clinica.sistema.service.WhatsAppLembreteConsultaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppLembreteScheduler {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppLembreteScheduler.class);

    private final WhatsAppLembreteConsultaService lembreteService;

    public WhatsAppLembreteScheduler(WhatsAppLembreteConsultaService lembreteService) {
        this.lembreteService = lembreteService;
    }

    /** Vespera (D-1): 09:00 e 18:00 — cobre manha e tarde sem depender de um unico horario. */
    @Scheduled(cron = "${app.whatsapp.meta.lembrete.cron:0 0 9,18 * * *}")
    public void enviarLembretesVespera() {
        if (!lembreteService.lembreteAutomaticoAtivo()) {
            return;
        }
        try {
            lembreteService.processarLembretesVesperaConsulta();
        } catch (RuntimeException ex) {
            log.warn("Falha ao processar lembretes WhatsApp: {}", ex.getMessage());
        }
    }
}
