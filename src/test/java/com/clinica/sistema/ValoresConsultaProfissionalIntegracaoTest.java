package com.clinica.sistema;

import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.AtualizarValoresConsultaProfissionalForm;
import com.clinica.sistema.service.ValorConsultaService;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.service.UsuarioService;
import com.clinica.sistema.service.AgendamentoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ValoresConsultaProfissionalIntegracaoTest {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private SalaRepository salaRepository;

    @Autowired
    private ValorConsultaService valorConsultaService;

    @Autowired
    private AgendamentoService agendamentoService;

    @Test
    void listagemValoresCentralRenderizaPercentualIndicacao() {
        var linhas = usuarioService.listarProfissionaisParaGestaoValoresConsulta();
        assertTrue(!linhas.isEmpty());
        assertTrue(linhas.get(0).percentualIndicacaoResumo().endsWith("%"));
    }

    @Test
    void polyanaSalvaTaxaSalaDaJuliaEFormularioDaAgendaRecebeTaxa() {
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();

        AtualizarValoresConsultaProfissionalForm valores = new AtualizarValoresConsultaProfissionalForm();
        valores.setUsuarioId(julia.getId());
        valores.setValorAvulso(new BigDecimal("40.00"));
        valores.setValorSemanal(new BigDecimal("38.00"));
        valores.setValorQuinzenal(new BigDecimal("36.00"));
        valores.setValorMensal(new BigDecimal("32.00"));
        usuarioService.atualizarValoresConsultaProfissional(valores, polyana);

        Usuario juliaAtualizada = usuarioRepository.findById(julia.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("40.00").compareTo(juliaAtualizada.getValorConsultaAvulso()));

        AgendamentoForm form = new AgendamentoForm();
        form.setProfissionalId(julia.getId());
        form.setRecorrencia("AVULSO");
        usuarioService.preencherTaxaSalaPadraoNoForm(form, julia.getId(), "AVULSO");
        assertEquals(0, new BigDecimal("40.00").compareTo(form.getValorClinicaCobra()));
        assertNull(form.getValorProfissionalRecebe());

        String json = usuarioService.jsonValoresConsultaPadraoPorProfissional();
        assertTrue(json.contains("40.00"), () -> "JSON deveria expor taxa da Julia: " + json);
    }

    @Test
    void preencherTaxaSalaSobrescreveValorAntigoNoFormulario() {
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();

        AtualizarValoresConsultaProfissionalForm valores = new AtualizarValoresConsultaProfissionalForm();
        valores.setUsuarioId(julia.getId());
        valores.setValorAvulso(new BigDecimal("15.00"));
        usuarioService.atualizarValoresConsultaProfissional(valores, polyana);

        AgendamentoForm form = new AgendamentoForm();
        form.setProfissionalId(julia.getId());
        form.setValorClinicaCobra(new BigDecimal("10.00"));
        usuarioService.preencherTaxaSalaPadraoNoForm(form, julia.getId(), "AVULSO");
        assertEquals(0, new BigDecimal("15.00").compareTo(form.getValorClinicaCobra()));
    }

    @Test
    void taxasSalaReferenciaProfissionalUsaCadastroCentral() {
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();

        AtualizarValoresConsultaProfissionalForm valores = new AtualizarValoresConsultaProfissionalForm();
        valores.setUsuarioId(julia.getId());
        valores.setValorAvulso(new BigDecimal("30.00"));
        usuarioService.atualizarValoresConsultaProfissional(valores, polyana);

        var taxas = usuarioService.taxasSalaReferenciaProfissional(julia);
        assertEquals(0, new BigDecimal("30.00").compareTo(taxas.get("AVULSO")));
    }

    @Test
    void polyanaSalvaPercentualIndicacaoPersonalizado() {
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();

        AtualizarValoresConsultaProfissionalForm indicacao = new AtualizarValoresConsultaProfissionalForm();
        indicacao.setUsuarioId(julia.getId());
        indicacao.setPercentualTaxaIndicacao(new BigDecimal("25"));
        usuarioService.atualizarValoresConsultaProfissional(indicacao, polyana);

        Usuario juliaAtualizada = usuarioRepository.findById(julia.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("25.00").compareTo(juliaAtualizada.getPercentualTaxaIndicacao()));
        assertEquals(
                0,
                new BigDecimal("50.00").compareTo(
                        valorConsultaService.calcularTarifaClinicaIndicacao(new BigDecimal("200.00"), juliaAtualizada)
                )
        );
    }

    @Test
    void salvarValoresNaCentralAtualizaConsultasJaAgendadas() {
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();

        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(julia);
        agendamento.setSala(salaRepository.findAll().get(0));
        agendamento.setNomeCliente("Cliente teste valores");
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(2));
        agendamento.setDataHoraFim(LocalDateTime.now().plusDays(2).plusHours(1));
        agendamento.setTipoRecorrencia("AVULSO");
        agendamento.setValorProfissionalRecebe(new BigDecimal("100.00"));
        agendamento.setValorClinicaCobra(new BigDecimal("35.00"));
        agendamento.setValorLiquidoProfissional(new BigDecimal("65.00"));
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        agendamento = agendamentoRepository.save(agendamento);

        AtualizarValoresConsultaProfissionalForm valores = new AtualizarValoresConsultaProfissionalForm();
        valores.setUsuarioId(julia.getId());
        valores.setValorAvulso(new BigDecimal("210.00"));
        int atualizadas = usuarioService.atualizarValoresConsultaProfissional(valores, polyana);

        assertTrue(atualizadas >= 1);
        Agendamento atualizado = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(atualizado.getValorProfissionalRecebe()));
        assertEquals(0, new BigDecimal("210.00").compareTo(atualizado.getValorClinicaCobra()));
        assertEquals(0, new BigDecimal("-110.00").compareTo(atualizado.getValorLiquidoProfissional()));
    }

    @Test
    void alterarParaTodosAtualizaTodosProfissionaisEConsultas() {
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();

        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(julia);
        agendamento.setSala(salaRepository.findAll().get(0));
        agendamento.setNomeCliente("Cliente lote");
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(3));
        agendamento.setDataHoraFim(LocalDateTime.now().plusDays(3).plusHours(1));
        agendamento.setTipoRecorrencia("AVULSO");
        agendamento.setValorProfissionalRecebe(new BigDecimal("100.00"));
        agendamento.setValorClinicaCobra(new BigDecimal("35.00"));
        agendamento.setValorLiquidoProfissional(new BigDecimal("65.00"));
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        agendamento = agendamentoRepository.save(agendamento);

        AtualizarValoresConsultaProfissionalForm lote = new AtualizarValoresConsultaProfissionalForm();
        lote.setValorAvulso(new BigDecimal("150.00"));
        lote.setValorSemanal(new BigDecimal("140.00"));
        lote.setValorQuinzenal(new BigDecimal("130.00"));
        lote.setValorMensal(new BigDecimal("120.00"));
        lote.setPercentualTaxaIndicacao(new BigDecimal("25"));

        var resultado = usuarioService.atualizarValoresConsultaTodosProfissionais(lote, polyana, null);

        assertTrue(resultado.profissionaisAtualizados() >= 1);
        assertTrue(resultado.consultasAtualizadas() >= 1);

        Usuario juliaAtualizada = usuarioRepository.findById(julia.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("150.00").compareTo(juliaAtualizada.getValorConsultaAvulso()));

        Agendamento atualizado = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(atualizado.getValorProfissionalRecebe()));
        assertEquals(0, new BigDecimal("150.00").compareTo(atualizado.getValorClinicaCobra()));
        assertEquals(0, new BigDecimal("-50.00").compareTo(atualizado.getValorLiquidoProfissional()));
    }

    @Test
    void alterarParaTodosRespeitaExclusaoMenos() {
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();
        Usuario anapaula = usuarioRepository.findByLogin("anapaula").orElseThrow();

        AtualizarValoresConsultaProfissionalForm juliaValores = new AtualizarValoresConsultaProfissionalForm();
        juliaValores.setUsuarioId(julia.getId());
        juliaValores.setValorAvulso(new BigDecimal("99.00"));
        juliaValores.setValorSemanal(new BigDecimal("99.00"));
        juliaValores.setValorQuinzenal(new BigDecimal("99.00"));
        juliaValores.setValorMensal(new BigDecimal("99.00"));
        usuarioService.atualizarValoresConsultaProfissional(juliaValores, polyana);

        AtualizarValoresConsultaProfissionalForm lote = new AtualizarValoresConsultaProfissionalForm();
        lote.setValorAvulso(new BigDecimal("250.00"));
        lote.setValorSemanal(new BigDecimal("240.00"));
        lote.setValorQuinzenal(new BigDecimal("230.00"));
        lote.setValorMensal(new BigDecimal("220.00"));

        var resultado = usuarioService.atualizarValoresConsultaTodosProfissionais(
                lote,
                polyana,
                List.of(julia.getId())
        );

        assertEquals(1, resultado.profissionaisExcluidos());

        Usuario juliaAtualizada = usuarioRepository.findById(julia.getId()).orElseThrow();
        Usuario anaAtualizada = usuarioRepository.findById(anapaula.getId()).orElseThrow();

        assertEquals(0, new BigDecimal("99.00").compareTo(juliaAtualizada.getValorConsultaAvulso()));
        assertEquals(0, new BigDecimal("250.00").compareTo(anaAtualizada.getValorConsultaAvulso()));
    }

    @Test
    void propagacaoAtualizaClinicaEmAgendamentoAntigoComoDemoFin() {
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();

        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(julia);
        agendamento.setSala(salaRepository.findAll().get(0));
        agendamento.setNomeCliente("DEMO-FIN-11 Julia");
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(10));
        agendamento.setDataHoraFim(LocalDateTime.now().plusDays(10).plusHours(1));
        agendamento.setTipoRecorrencia("AVULSO");
        agendamento.setValorProfissionalRecebe(new BigDecimal("150.00"));
        agendamento.setValorClinicaCobra(new BigDecimal("35.00"));
        agendamento.setValorLiquidoProfissional(new BigDecimal("115.00"));
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        agendamento = agendamentoRepository.save(agendamento);

        AtualizarValoresConsultaProfissionalForm valores = new AtualizarValoresConsultaProfissionalForm();
        valores.setUsuarioId(julia.getId());
        valores.setValorAvulso(new BigDecimal("50.00"));

        int atualizadas = usuarioService.atualizarValoresConsultaProfissional(valores, polyana);

        assertTrue(atualizadas >= 1);
        Agendamento atualizado = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("150.00").compareTo(atualizado.getValorProfissionalRecebe()));
        assertEquals(0, new BigDecimal("50.00").compareTo(atualizado.getValorClinicaCobra()));
        assertEquals(0, new BigDecimal("100.00").compareTo(atualizado.getValorLiquidoProfissional()));
    }

    @Test
    void taxaSalaPendenteRecalculaClinicaELiquidoMantendoProfRecebe() {
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();

        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(julia);
        agendamento.setSala(salaRepository.findAll().get(0));
        agendamento.setNomeCliente("Pendente taxa sala Julia");
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(4));
        agendamento.setDataHoraFim(LocalDateTime.now().plusDays(4).plusHours(1));
        agendamento.setTipoRecorrencia("AVULSO");
        agendamento.setValorProfissionalRecebe(new BigDecimal("170.00"));
        agendamento.setValorClinicaCobra(new BigDecimal("30.00"));
        agendamento.setValorLiquidoProfissional(new BigDecimal("140.00"));
        agendamento.setValorPagamento(new BigDecimal("30.00"));
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        agendamento = agendamentoRepository.save(agendamento);

        AtualizarValoresConsultaProfissionalForm valores = new AtualizarValoresConsultaProfissionalForm();
        valores.setUsuarioId(julia.getId());
        valores.setValorAvulso(new BigDecimal("50.00"));

        int atualizadas = usuarioService.atualizarValoresConsultaProfissional(valores, polyana);

        assertEquals(1, atualizadas);
        Agendamento atualizado = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("170.00").compareTo(atualizado.getValorProfissionalRecebe()));
        assertEquals(0, new BigDecimal("50.00").compareTo(atualizado.getValorClinicaCobra()));
        assertEquals(0, new BigDecimal("120.00").compareTo(atualizado.getValorLiquidoProfissional()));
        assertEquals(0, new BigDecimal("50.00").compareTo(atualizado.getValorPagamento()));
    }

    @Test
    void salvarValoresNaoAlteraConsultaJaPaga() {
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();

        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(julia);
        agendamento.setSala(salaRepository.findAll().get(0));
        agendamento.setNomeCliente("DEMO-FIN-TEST Julia PAGO");
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(5));
        agendamento.setDataHoraFim(LocalDateTime.now().plusDays(5).plusHours(1));
        agendamento.setTipoRecorrencia("AVULSO");
        agendamento.setValorProfissionalRecebe(new BigDecimal("135.00"));
        agendamento.setValorClinicaCobra(new BigDecimal("35.00"));
        agendamento.setValorLiquidoProfissional(new BigDecimal("100.00"));
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        agendamento.setValorPagamento(new BigDecimal("35.00"));
        agendamento = agendamentoRepository.save(agendamento);

        AtualizarValoresConsultaProfissionalForm valores = new AtualizarValoresConsultaProfissionalForm();
        valores.setUsuarioId(julia.getId());
        valores.setValorAvulso(new BigDecimal("220.00"));

        usuarioService.atualizarValoresConsultaProfissional(valores, polyana);

        Agendamento atualizado = agendamentoRepository.findById(agendamento.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("135.00").compareTo(atualizado.getValorProfissionalRecebe()));
        assertEquals(0, new BigDecimal("35.00").compareTo(atualizado.getValorClinicaCobra()));
        assertEquals(0, new BigDecimal("100.00").compareTo(atualizado.getValorLiquidoProfissional()));
        assertEquals(0, new BigDecimal("35.00").compareTo(atualizado.getValorPagamento()));
    }

    @Test
    void taxaSalaPendentePorTipoRecorrenciaAtualizaClinica() {
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();

        Agendamento semanal = criarAgendamentoPendente(julia, "SEMANAL", "170.00", "35.00", "135.00");
        Agendamento quinzenal = criarAgendamentoPendente(julia, "QUINZENAL", "180.00", "35.00", "145.00");
        Agendamento mensal = criarAgendamentoPendente(julia, "MENSAL", "200.00", "32.00", "168.00");

        AtualizarValoresConsultaProfissionalForm valores = new AtualizarValoresConsultaProfissionalForm();
        valores.setUsuarioId(julia.getId());
        valores.setValorSemanal(new BigDecimal("40.00"));
        valores.setValorQuinzenal(new BigDecimal("38.00"));
        valores.setValorMensal(new BigDecimal("30.00"));

        int atualizadas = usuarioService.atualizarValoresConsultaProfissional(valores, polyana);

        assertEquals(3, atualizadas);
        Agendamento semanalAtualizado = agendamentoRepository.findById(semanal.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("170.00").compareTo(semanalAtualizado.getValorProfissionalRecebe()));
        assertEquals(0, new BigDecimal("40.00").compareTo(semanalAtualizado.getValorClinicaCobra()));
        assertEquals(0, new BigDecimal("130.00").compareTo(semanalAtualizado.getValorLiquidoProfissional()));

        Agendamento quinzenalAtualizado = agendamentoRepository.findById(quinzenal.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("180.00").compareTo(quinzenalAtualizado.getValorProfissionalRecebe()));
        assertEquals(0, new BigDecimal("38.00").compareTo(quinzenalAtualizado.getValorClinicaCobra()));
        assertEquals(0, new BigDecimal("142.00").compareTo(quinzenalAtualizado.getValorLiquidoProfissional()));

        Agendamento mensalAtualizado = agendamentoRepository.findById(mensal.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("200.00").compareTo(mensalAtualizado.getValorProfissionalRecebe()));
        assertEquals(0, new BigDecimal("30.00").compareTo(mensalAtualizado.getValorClinicaCobra()));
        assertEquals(0, new BigDecimal("170.00").compareTo(mensalAtualizado.getValorLiquidoProfissional()));
    }

    @Test
    void taxaSalaPagaPorTipoRecorrenciaNaoAlteraValores() {
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();

        Agendamento semanal = criarAgendamentoPago(julia, "SEMANAL", "170.00", "35.00", "135.00");
        Agendamento quinzenal = criarAgendamentoPago(julia, "QUINZENAL", "180.00", "35.00", "145.00");
        Agendamento mensal = criarAgendamentoPago(julia, "MENSAL", "200.00", "32.00", "168.00");

        AtualizarValoresConsultaProfissionalForm valores = new AtualizarValoresConsultaProfissionalForm();
        valores.setUsuarioId(julia.getId());
        valores.setValorSemanal(new BigDecimal("99.00"));
        valores.setValorQuinzenal(new BigDecimal("88.00"));
        valores.setValorMensal(new BigDecimal("77.00"));

        usuarioService.atualizarValoresConsultaProfissional(valores, polyana);

        assertValoresInalterados(agendamentoRepository.findById(semanal.getId()).orElseThrow(),
                "170.00", "35.00", "135.00");
        assertValoresInalterados(agendamentoRepository.findById(quinzenal.getId()).orElseThrow(),
                "180.00", "35.00", "145.00");
        assertValoresInalterados(agendamentoRepository.findById(mensal.getId()).orElseThrow(),
                "200.00", "32.00", "168.00");
    }

    private Agendamento criarAgendamentoPendente(
            Usuario profissional,
            String tipoRecorrencia,
            String profRecebe,
            String clinicaCobra,
            String liquido
    ) {
        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(profissional);
        agendamento.setSala(salaRepository.findAll().get(0));
        agendamento.setNomeCliente("Pendente " + tipoRecorrencia);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(6));
        agendamento.setDataHoraFim(LocalDateTime.now().plusDays(6).plusHours(1));
        agendamento.setTipoRecorrencia(tipoRecorrencia);
        agendamento.setValorProfissionalRecebe(new BigDecimal(profRecebe));
        agendamento.setValorClinicaCobra(new BigDecimal(clinicaCobra));
        agendamento.setValorLiquidoProfissional(new BigDecimal(liquido));
        agendamento.setValorPagamento(new BigDecimal(clinicaCobra));
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        return agendamentoRepository.save(agendamento);
    }

    private Agendamento criarAgendamentoPago(
            Usuario profissional,
            String tipoRecorrencia,
            String profRecebe,
            String clinicaCobra,
            String liquido
    ) {
        Agendamento agendamento = criarAgendamentoPendente(profissional, tipoRecorrencia, profRecebe, clinicaCobra, liquido);
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        return agendamentoRepository.save(agendamento);
    }

    private void assertValoresInalterados(
            Agendamento agendamento,
            String profRecebe,
            String clinicaCobra,
            String liquido
    ) {
        assertEquals(0, new BigDecimal(profRecebe).compareTo(agendamento.getValorProfissionalRecebe()));
        assertEquals(0, new BigDecimal(clinicaCobra).compareTo(agendamento.getValorClinicaCobra()));
        assertEquals(0, new BigDecimal(liquido).compareTo(agendamento.getValorLiquidoProfissional()));
    }

    @Test
    void alterarValorProfissionalSerieAtualizaPendentesIgnoraPagos() {
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();
        String serieId = "test-serie-valor-" + System.nanoTime();

        Agendamento pago = criarAgendamentoSerie(julia, serieId, "SEMANAL", 2,
                "170.00", "35.00", "135.00", PagamentoStatus.PAGO);
        Agendamento pendente1 = criarAgendamentoSerie(julia, serieId, "SEMANAL", 9,
                "170.00", "35.00", "135.00", PagamentoStatus.AGUARDANDO_PAGAMENTO);
        Agendamento pendente2 = criarAgendamentoSerie(julia, serieId, "SEMANAL", 16,
                "170.00", "35.00", "135.00", PagamentoStatus.AGUARDANDO_PAGAMENTO);

        int atualizados = agendamentoService.alterarValorProfissionalSerie(
                pendente1.getId(),
                new BigDecimal("200.00"),
                julia
        );

        assertEquals(2, atualizados);
        Agendamento pagoAtualizado = agendamentoRepository.findById(pago.getId()).orElseThrow();
        assertValoresInalterados(pagoAtualizado, "170.00", "35.00", "135.00");

        Agendamento pendente1Atualizado = agendamentoRepository.findById(pendente1.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("200.00").compareTo(pendente1Atualizado.getValorProfissionalRecebe()));
        assertEquals(0, new BigDecimal("35.00").compareTo(pendente1Atualizado.getValorClinicaCobra()));
        assertEquals(0, new BigDecimal("165.00").compareTo(pendente1Atualizado.getValorLiquidoProfissional()));

        Agendamento pendente2Atualizado = agendamentoRepository.findById(pendente2.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("200.00").compareTo(pendente2Atualizado.getValorProfissionalRecebe()));
        assertEquals(0, new BigDecimal("165.00").compareTo(pendente2Atualizado.getValorLiquidoProfissional()));
    }

    @Test
    void alterarValorProfissionalSerieQuinzenalAtualizaPendentes() {
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();
        String serieId = "test-serie-quinz-" + System.nanoTime();

        criarAgendamentoSerie(julia, serieId, "QUINZENAL", 14,
                "170.00", "35.00", "135.00", PagamentoStatus.PAGO);
        Agendamento pendente = criarAgendamentoSerie(julia, serieId, "QUINZENAL", 28,
                "170.00", "35.00", "135.00", PagamentoStatus.AGUARDANDO_PAGAMENTO);

        int atualizados = agendamentoService.alterarValorProfissionalSerie(
                pendente.getId(),
                new BigDecimal("190.00"),
                julia
        );

        assertEquals(1, atualizados);
        Agendamento pendenteAtualizado = agendamentoRepository.findById(pendente.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("190.00").compareTo(pendenteAtualizado.getValorProfissionalRecebe()));
        assertEquals(0, new BigDecimal("155.00").compareTo(pendenteAtualizado.getValorLiquidoProfissional()));
    }

    @Test
    void alterarValorProfissionalSerieMensalAtualizaPendentes() {
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();
        Agendamento pago = criarAgendamentoMensal(julia, "Cliente mensal valor", 7,
                "150.00", "32.00", "118.00", PagamentoStatus.PAGO);
        Agendamento pendente = criarAgendamentoMensal(julia, "Cliente mensal valor", 37,
                "150.00", "32.00", "118.00", PagamentoStatus.AGUARDANDO_PAGAMENTO);

        int atualizados = agendamentoService.alterarValorProfissionalSerie(
                pendente.getId(),
                new BigDecimal("180.00"),
                julia
        );

        assertEquals(1, atualizados);
        Agendamento pagoAtualizado = agendamentoRepository.findById(pago.getId()).orElseThrow();
        assertValoresInalterados(pagoAtualizado, "150.00", "32.00", "118.00");
        Agendamento pendenteAtualizado = agendamentoRepository.findById(pendente.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("180.00").compareTo(pendenteAtualizado.getValorProfissionalRecebe()));
        assertEquals(0, new BigDecimal("148.00").compareTo(pendenteAtualizado.getValorLiquidoProfissional()));
    }

    private Agendamento criarAgendamentoMensal(
            Usuario profissional,
            String nomeCliente,
            int diasAFrente,
            String profRecebe,
            String clinicaCobra,
            String liquido,
            PagamentoStatus status
    ) {
        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(profissional);
        agendamento.setSala(salaRepository.findAll().get(0));
        agendamento.setNomeCliente(nomeCliente);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(diasAFrente));
        agendamento.setDataHoraFim(LocalDateTime.now().plusDays(diasAFrente).plusHours(1));
        agendamento.setTipoRecorrencia("MENSAL");
        agendamento.setFixo(false);
        agendamento.setSerieFixaId("mensal-" + profissional.getId() + "-" + nomeCliente.trim().toLowerCase());
        agendamento.setValorProfissionalRecebe(new BigDecimal(profRecebe));
        agendamento.setValorClinicaCobra(new BigDecimal(clinicaCobra));
        agendamento.setValorLiquidoProfissional(new BigDecimal(liquido));
        agendamento.setStatusPagamento(status);
        if (status != PagamentoStatus.PAGO) {
            agendamento.setValorPagamento(new BigDecimal(clinicaCobra));
        }
        return agendamentoRepository.save(agendamento);
    }

    private Agendamento criarAgendamentoSerie(
            Usuario profissional,
            String serieFixaId,
            String tipoRecorrencia,
            int diasAFrente,
            String profRecebe,
            String clinicaCobra,
            String liquido,
            PagamentoStatus status
    ) {
        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(profissional);
        agendamento.setSala(salaRepository.findAll().get(0));
        agendamento.setNomeCliente("Cliente valor serie");
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(diasAFrente));
        agendamento.setDataHoraFim(LocalDateTime.now().plusDays(diasAFrente).plusHours(1));
        agendamento.setTipoRecorrencia(tipoRecorrencia);
        agendamento.setFixo(true);
        agendamento.setSerieFixaId(serieFixaId);
        agendamento.setValorProfissionalRecebe(new BigDecimal(profRecebe));
        agendamento.setValorClinicaCobra(new BigDecimal(clinicaCobra));
        agendamento.setValorLiquidoProfissional(new BigDecimal(liquido));
        agendamento.setStatusPagamento(status);
        if (status != PagamentoStatus.PAGO) {
            agendamento.setValorPagamento(new BigDecimal(clinicaCobra));
        }
        return agendamentoRepository.save(agendamento);
    }
}
