package com.clinica.sistema.service;

import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValorConsultaServiceTest {

    private ValorConsultaService service;
    private Sala sala1;
    private Sala sala4;

    @BeforeEach
    void setUp() {
        service = new ValorConsultaService();

        sala1 = new Sala();
        sala1.setId(1L);
        sala1.setNome("Sala 1");

        sala4 = new Sala();
        sala4.setId(4L);
        sala4.setNome("Sala 4");
    }

    @Test
    void sala4DeveCobrarVinteECinco() {
        assertEquals(new BigDecimal("25.00"), service.calcularTarifaClinicaPadrao(sala4, "AVULSO"));
        assertEquals(new BigDecimal("25.00"), service.calcularTarifaClinicaPadrao(sala4, "SEMANAL"));
    }

    @Test
    void fixoSemanalDeveCobrarTrintaECinco() {
        assertEquals(new BigDecimal("35.00"), service.calcularTarifaClinicaPadrao(sala1, "SEMANAL"));
    }

    @Test
    void avulsoEQuinzenalDevemCobrarTrintaECinco() {
        assertEquals(new BigDecimal("35.00"), service.calcularTarifaClinicaPadrao(sala1, "AVULSO"));
        assertEquals(new BigDecimal("35.00"), service.calcularTarifaClinicaPadrao(sala1, "QUINZENAL"));
    }

    @Test
    void mensalDeveCobrarTrintaEDois() {
        assertEquals(new BigDecimal("32.00"), service.calcularTarifaClinicaPadrao(sala1, "MENSAL"));
    }

    @Test
    void turnoLocacaoDeveCobrarQuinhentos() {
        assertEquals(new BigDecimal("500.00"), service.calcularTarifaClinicaPadrao(sala1, "AVULSO", "TURNO_MANHA"));
        assertEquals(new BigDecimal("500.00"), service.calcularTarifaClinicaPadrao(sala4, "SEMANAL", "TURNO_TARDE"));
    }

    @Test
    void turnoLocacaoIgnoraIndicacaoMesmoComCheckboxMarcado() {
        AgendamentoForm form = new AgendamentoForm();
        form.setTurnoLocacao("TURNO_MANHA");
        form.setValorProfissionalRecebe(new BigDecimal("800.00"));
        form.setIndicacaoDona(true);

        var agendamento = new com.clinica.sistema.model.Agendamento();
        service.aplicarValores(agendamento, form, sala1, "SEMANAL", true);

        assertEquals(new BigDecimal("500.00"), agendamento.getValorClinicaCobra());
        assertEquals(new BigDecimal("300.00"), agendamento.getValorLiquidoProfissional());
        assertFalse(agendamento.getIndicacaoDona());
    }

    @Test
    void indicacaoDeveCobrarTrintaPorCento() {
        assertEquals(new BigDecimal("60.00"), service.calcularTarifaClinicaIndicacao(new BigDecimal("200.00")));
    }

    @Test
    void liquidoDeveSubtrairClinicaDoValorRecebido() {
        assertEquals(new BigDecimal("115.00"), service.calcularLiquido(new BigDecimal("150.00"), new BigDecimal("35.00")));
        assertEquals(new BigDecimal("140.00"), service.calcularLiquido(new BigDecimal("200.00"), new BigDecimal("60.00")));
    }

    @Test
    void indicacaoDeveIgnorarTarifaManual() {
        AgendamentoForm form = new AgendamentoForm();
        form.setValorProfissionalRecebe(new BigDecimal("200.00"));
        form.setValorClinicaCobra(new BigDecimal("35.00"));
        form.setIndicacaoDona(true);

        var agendamento = new com.clinica.sistema.model.Agendamento();
        service.aplicarValores(agendamento, form, sala1, "AVULSO");

        assertEquals(new BigDecimal("200.00"), agendamento.getValorProfissionalRecebe());
        assertEquals(new BigDecimal("60.00"), agendamento.getValorClinicaCobra());
        assertEquals(new BigDecimal("140.00"), agendamento.getValorLiquidoProfissional());
        assertTrue(agendamento.getIndicacaoDona());
    }

    @Test
    void indicacaoNaSala4DeveUsarTrintaPorCento() {
        AgendamentoForm form = new AgendamentoForm();
        form.setValorProfissionalRecebe(new BigDecimal("150.00"));
        form.setValorClinicaCobra(new BigDecimal("25.00"));
        form.setIndicacaoDona(true);

        var agendamento = new com.clinica.sistema.model.Agendamento();
        service.aplicarValores(agendamento, form, sala4, "AVULSO");

        assertEquals(new BigDecimal("45.00"), agendamento.getValorClinicaCobra());
        assertEquals(new BigDecimal("105.00"), agendamento.getValorLiquidoProfissional());
    }

    @Test
    void isSala4ReconheceNomeDaSala() {
        assertTrue(service.isSala4(sala4));
        assertFalse(service.isSala4(sala1));
    }

    @Test
    void demaisConsultasDaSerieNaoDevemUsarIndicacao() {
        AgendamentoForm form = new AgendamentoForm();
        form.setValorProfissionalRecebe(new BigDecimal("200.00"));
        form.setIndicacaoDona(true);

        var agendamento = new com.clinica.sistema.model.Agendamento();
        service.aplicarValores(agendamento, form, sala1, "SEMANAL", false);

        assertFalse(agendamento.getIndicacaoDona());
        assertEquals(new BigDecimal("35.00"), agendamento.getValorClinicaCobra());
        assertEquals(new BigDecimal("165.00"), agendamento.getValorLiquidoProfissional());
    }

    @Test
    void consultaPropriaDonaDeveRegistrarSomenteValorRecebido() {
        AgendamentoForm form = new AgendamentoForm();
        form.setValorProfissionalRecebe(new BigDecimal("180.00"));
        form.setIndicacaoDona(true);

        var agendamento = new com.clinica.sistema.model.Agendamento();
        service.aplicarValorConsultaPropriaDona(agendamento, form);

        assertEquals(new BigDecimal("180.00"), agendamento.getValorProfissionalRecebe());
        assertEquals(BigDecimal.ZERO, agendamento.getValorClinicaCobra());
        assertEquals(new BigDecimal("180.00"), agendamento.getValorLiquidoProfissional());
        assertFalse(agendamento.getIndicacaoDona());
    }

    @Test
    void deveRetornarValorPadraoCadastradoNoProfissional() {
        Usuario julia = new Usuario();
        julia.setValorConsultaAvulso(new BigDecimal("150.00"));
        julia.setValorConsultaSemanal(new BigDecimal("140.00"));
        julia.setValorConsultaQuinzenal(new BigDecimal("130.00"));
        julia.setValorConsultaMensal(new BigDecimal("120.00"));

        assertEquals(new BigDecimal("150.00"), service.valorPadraoProfissionalRecebe(julia, "AVULSO").orElseThrow());
        assertEquals(new BigDecimal("140.00"), service.valorPadraoProfissionalRecebe(julia, "SEMANAL").orElseThrow());
        assertEquals(new BigDecimal("130.00"), service.valorPadraoProfissionalRecebe(julia, "QUINZENAL").orElseThrow());
        assertEquals(new BigDecimal("120.00"), service.valorPadraoProfissionalRecebe(julia, "MENSAL").orElseThrow());
        assertEquals(new BigDecimal("150.00"), service.valorPadraoProfissionalRecebe(julia, "OUTRO").orElseThrow());
        assertTrue(service.valorPadraoProfissionalRecebe(null, "AVULSO").isEmpty());
    }

    @Test
    void aplicarValoresDeveUsarValorPadraoCadastradoQuandoFormularioVazio() {
        Usuario julia = new Usuario();
        julia.setValorConsultaAvulso(new BigDecimal("180.00"));

        AgendamentoForm form = new AgendamentoForm();
        var agendamento = new com.clinica.sistema.model.Agendamento();

        service.aplicarValores(agendamento, form, sala1, "AVULSO", true, julia);

        assertEquals(new BigDecimal("180.00"), agendamento.getValorProfissionalRecebe());
        assertEquals(new BigDecimal("35.00"), agendamento.getValorClinicaCobra());
    }
}
