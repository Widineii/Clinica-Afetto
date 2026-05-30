package com.clinica.sistema.service;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Sala;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InfinitePayServiceTest {

    private InfinitePayService service;
    private Sala sala1;
    private Sala sala4;

    @BeforeEach
    void setUp() {
        service = new InfinitePayService(
                new com.clinica.sistema.config.InfinitePayProperties(),
                new ValorConsultaService()
        );

        sala1 = new Sala();
        sala1.setId(1L);
        sala1.setNome("Sala 1");

        sala4 = new Sala();
        sala4.setId(4L);
        sala4.setNome("Sala 4");
    }

    @Test
    void deveUsarValorClinicaSalvoNoAgendamento() {
        Agendamento agendamento = agendamentoBase();
        agendamento.setValorClinicaCobra(new BigDecimal("32.00"));

        assertEquals(new BigDecimal("32.00"), service.valorPagamento(agendamento));
    }

    @Test
    void deveCalcularTarifaPadraoQuandoValorNaoFoiSalvo() {
        Agendamento fixo = agendamentoBase();
        fixo.setSala(sala1);
        fixo.setTipoRecorrencia("SEMANAL");
        fixo.setFixo(true);

        Agendamento avulso = agendamentoBase();
        avulso.setSala(sala1);
        avulso.setTipoRecorrencia("AVULSO");

        Agendamento sala4Agendamento = agendamentoBase();
        sala4Agendamento.setSala(sala4);
        sala4Agendamento.setTipoRecorrencia("SEMANAL");

        assertEquals(new BigDecimal("32.00"), service.valorPagamento(fixo));
        assertEquals(new BigDecimal("35.00"), service.valorPagamento(avulso));
        assertEquals(new BigDecimal("25.00"), service.valorPagamento(sala4Agendamento));
    }

    @Test
    void deveExtrairSlugDoCheckoutUrl() {
        com.clinica.sistema.config.InfinitePayProperties props =
                new com.clinica.sistema.config.InfinitePayProperties();
        props.setHandle("afettopsicologia");
        InfinitePayService svc = new InfinitePayService(props, new ValorConsultaService());

        assertEquals(
                "1RxANX7tdF",
                svc.extrairSlugDoCheckoutUrl("https://checkout.infinitepay.io/afettopsicologia/1RxANX7tdF")
        );
        assertEquals(
                "abcdef123",
                svc.extrairSlugDoCheckoutUrl("https://checkout.infinitepay.io/pay/abcdef123")
        );
    }

    @Test
    void indicacaoDeveUsarTrintaPorCentoNoPix() {
        Agendamento agendamento = agendamentoBase();
        agendamento.setIndicacaoDona(true);
        agendamento.setValorProfissionalRecebe(new BigDecimal("200.00"));

        assertEquals(new BigDecimal("60.00"), service.valorPagamento(agendamento));
    }

    private Agendamento agendamentoBase() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setNomeCliente("Cliente teste");
        return agendamento;
    }
}
