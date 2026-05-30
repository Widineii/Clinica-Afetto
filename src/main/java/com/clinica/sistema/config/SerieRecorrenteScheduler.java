package com.clinica.sistema.config;

import com.clinica.sistema.service.AgendamentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SerieRecorrenteScheduler {

    private static final Logger log = LoggerFactory.getLogger(SerieRecorrenteScheduler.class);

    private final AgendamentoService agendamentoService;

    public SerieRecorrenteScheduler(AgendamentoService agendamentoService) {
        this.agendamentoService = agendamentoService;
    }

    @Scheduled(cron = "${app.agendamento.cron-renovar-series:0 */10 * * * *}")
    public void renovarSeriesRecorrentes() {
        try {
            agendamentoService.renovarSeriesRecorrentesAtivas();
        } catch (RuntimeException ex) {
            log.warn("Falha ao renovar series recorrentes em background: {}", ex.getMessage());
        }
    }
}
