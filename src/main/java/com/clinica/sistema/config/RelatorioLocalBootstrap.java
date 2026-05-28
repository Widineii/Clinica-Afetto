package com.clinica.sistema.config;

import com.clinica.sistema.service.RelatorioMensalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * No perfil local, gera o arquivo do mes passado apos o seed para o botao de PDF ja aparecer.
 */
@Profile("local")
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RelatorioLocalBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RelatorioLocalBootstrap.class);

    private final RelatorioMensalService relatorioMensalService;

    @Value("${app.relatorio-mensal.auto-fechamento-local:true}")
    private boolean autoFechamentoLocal;

    public RelatorioLocalBootstrap(RelatorioMensalService relatorioMensalService) {
        this.relatorioMensalService = relatorioMensalService;
    }

    @Override
    public void run(String... args) {
        if (!autoFechamentoLocal) {
            log.info("Relatorio local: fechamento automatico desligado (agenda limpa).");
            return;
        }
        if (!relatorioMensalService.podeExecutarFechamentoAutomatico()) {
            log.info(
                    "Relatorio local: fechamento do mes passado so apos o dia {} do mes atual.",
                    3
            );
            return;
        }
        boolean arquivou = relatorioMensalService.executarFechamentoAutomaticoSeDevido();
        if (arquivou) {
            log.info("Relatorio local: mes passado arquivado automaticamente para download do PDF.");
        }
    }
}
