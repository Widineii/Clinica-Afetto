package com.clinica.sistema.config;



import com.clinica.sistema.service.IndicacaoReservaService;

import com.clinica.sistema.service.PagamentoConsultaService;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Component;



@Component

public class PagamentoExpiracaoScheduler {



    private static final Logger log = LoggerFactory.getLogger(PagamentoExpiracaoScheduler.class);



    private final PagamentoConsultaService pagamentoConsultaService;

    private final IndicacaoReservaService indicacaoReservaService;



    public PagamentoExpiracaoScheduler(

            PagamentoConsultaService pagamentoConsultaService,

            IndicacaoReservaService indicacaoReservaService

    ) {

        this.pagamentoConsultaService = pagamentoConsultaService;

        this.indicacaoReservaService = indicacaoReservaService;

    }



    @Scheduled(fixedDelayString = "${app.pagamento.cron-expiracao-ms:30000}")

    public void expirarPagamentosVencidos() {

        int confirmados = pagamentoConsultaService.reconciliarTodosPagamentosInfinitePayPendentes();

        if (confirmados > 0) {

            log.info("Confirmados automaticamente {} pedido(s) InfinitePay.", confirmados);

        }

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

        int legadosDinheiro = pagamentoConsultaService.processarPagamentosDinheiroLegadosVencidos();

        if (legadosDinheiro > 0) {

            log.info("Processados {} registro(s) legados de pagamento em dinheiro vencidos.", legadosDinheiro);

        }

        int indicacoesVencidas = indicacaoReservaService.processarIndicacoesNaoPagasVencidas();

        if (indicacoesVencidas > 0) {

            log.info("Liberadas {} indicação(ões) aprovadas sem PIX no prazo.", indicacoesVencidas);

        }

    }

}


