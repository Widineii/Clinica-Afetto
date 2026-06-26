package com.clinica.sistema.config;

import com.clinica.sistema.service.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditoriaRetencaoScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaRetencaoScheduler.class);
    private static final String FUSO_BRASIL = "America/Sao_Paulo";

    private final AuditoriaService auditoriaService;

    public AuditoriaRetencaoScheduler(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /** Todo dia as 03:00 remove auditoria mais antiga que os ultimos 3 meses calendario. */
    @Scheduled(cron = "${app.auditoria.cron-expurgo:0 0 3 * * *}", zone = FUSO_BRASIL)
    public void expurgarAuditoriaAntiga() {
        executar("cron");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void expurgarAuditoriaNaSubida() {
        executar("startup");
    }

    private void executar(String origem) {
        try {
            int removidos = auditoriaService.expurgarRegistrosAntigos();
            if (removidos > 0) {
                log.info(
                        "Auditoria: {} registro(s) removido(s) fora da retencao de {} mes(es) (origem={}).",
                        removidos,
                        auditoriaService.getMesesRetencao(),
                        origem
                );
            }
        } catch (RuntimeException e) {
            log.warn("Falha ao expurgar auditoria antiga (origem={}): {}", origem, e.getMessage());
        }
    }
}
