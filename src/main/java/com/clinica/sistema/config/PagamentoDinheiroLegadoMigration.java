package com.clinica.sistema.config;

import com.clinica.sistema.service.IndicacaoReservaService;
import com.clinica.sistema.service.PagamentoConsultaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
public class PagamentoDinheiroLegadoMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PagamentoDinheiroLegadoMigration.class);

    private final PagamentoConsultaService pagamentoConsultaService;
    private final IndicacaoReservaService indicacaoReservaService;

    public PagamentoDinheiroLegadoMigration(
            PagamentoConsultaService pagamentoConsultaService,
            IndicacaoReservaService indicacaoReservaService
    ) {
        this.pagamentoConsultaService = pagamentoConsultaService;
        this.indicacaoReservaService = indicacaoReservaService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int migrados = pagamentoConsultaService.migrarPagamentosDinheiroLegadosParaPix();
        if (migrados > 0) {
            log.info("Migrados {} agendamento(s) do fluxo legado de dinheiro para PIX.", migrados);
        }
        indicacaoReservaService.corrigirIndicacoesComStatusInconsistente();
        indicacaoReservaService.restaurarIndicacoesLiberadasIncorretamente();
        indicacaoReservaService.normalizarIndicacaoApenasPrimeiraConsultaSerie();
    }
}
