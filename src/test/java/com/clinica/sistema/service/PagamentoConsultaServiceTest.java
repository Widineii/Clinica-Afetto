package com.clinica.sistema.service;

import com.clinica.sistema.config.InfinitePayProperties;
import com.clinica.sistema.config.PagamentoProperties;
import com.clinica.sistema.exception.PagamentoWebhookNaoAutorizadoException;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.clinica.sistema.exception.HorarioJaReservadoPorOutroProfissionalException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagamentoConsultaServiceTest {

    @Mock
    private com.clinica.sistema.repository.AgendamentoRepository repository;

    @Mock
    private com.clinica.sistema.repository.UsuarioRepository usuarioRepository;

    @Mock
    private InfinitePayService infinitePayService;

    @Mock
    private AuthService authService;

    @Mock
    private PagamentoProperties pagamentoProperties;

    @Mock
    private InfinitePayProperties infinitePayProperties;

    @InjectMocks
    private PagamentoConsultaService pagamentoConsultaService;

    private Agendamento agendamento;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(pagamentoProperties.getMensalDiaLimite()).thenReturn(15);
        agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setValorClinicaCobra(new BigDecimal("32.00"));
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(3).atTime(10, 0));
    }

    @Test
    void indicacaoDeveAguardarAprovacaoSemGerarPix() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setDonaClinica(false);

        agendamento.setIndicacaoDona(true);
        when(infinitePayService.resolverValorTaxaClinica(any())).thenReturn(new BigDecimal("60.00"));
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);

        pagamentoConsultaService.configurarPagamentosAoSalvar(java.util.List.of(agendamento), profissional, profissional);

        assertEquals(PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO, agendamento.getStatusPagamento());
        verify(infinitePayService, never()).gerarLinkPagamento(any());
    }

    @Test
    void indicacaoPorGestorParaOutroProfissionalDeveAguardarAprovacao() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        Usuario dona = new Usuario();
        dona.setId(99L);
        dona.setDonaClinica(true);

        agendamento.setIndicacaoDona(true);
        when(infinitePayService.resolverValorTaxaClinica(any())).thenReturn(new BigDecimal("60.00"));
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);
        when(authService.isDonaClinica(dona)).thenReturn(true);

        pagamentoConsultaService.configurarPagamentosAoSalvar(java.util.List.of(agendamento), profissional, dona);

        assertEquals(PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO, agendamento.getStatusPagamento());
        verify(infinitePayService, never()).gerarLinkPagamento(any());
    }

    @Test
    void deveAbrirConfirmacaoImediataNaPrimeiraConsultaDaSerie() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setDonaClinica(false);

        when(pagamentoProperties.getPrazoConfirmacaoMinutos()).thenReturn(5);
        when(infinitePayService.resolverValorTaxaClinica(any())).thenReturn(new BigDecimal("32.00"));
        when(infinitePayService.gerarLinkPagamento(any())).thenReturn(
                new com.clinica.sistema.dto.LinkPagamentoGerado("ag-1-test", "http://localhost/link", "slug")
        );
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);

        pagamentoConsultaService.configurarPagamentosAoSalvar(java.util.List.of(agendamento), profissional, profissional);

        assertEquals(PagamentoStatus.ESPERANDO_CONFIRMACAO, agendamento.getStatusPagamento());
        assertEquals(new BigDecimal("32.00"), agendamento.getValorPagamento());
        assertEquals("http://localhost/link", agendamento.getPagamentoLink());
        assertNotNull(agendamento.getPagamentoExpiraEm());
        assertTrue(agendamento.getPagamentoExpiraEm().isAfter(LocalDateTime.now()));
    }

    @Test
    void consultasFuturasDaSerieFicamComPagamentoFuturo() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setDonaClinica(false);

        when(pagamentoProperties.getPrazoConfirmacaoMinutos()).thenReturn(5);
        Agendamento segunda = new Agendamento();
        segunda.setId(2L);
        segunda.setDataHoraInicio(LocalDate.now().plusDays(10).atTime(10, 0));

        when(infinitePayService.resolverValorTaxaClinica(any())).thenReturn(new BigDecimal("32.00"));
        when(infinitePayService.gerarLinkPagamento(any())).thenReturn(
                new com.clinica.sistema.dto.LinkPagamentoGerado("ag-1-test", "http://localhost/link", "slug")
        );
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);

        pagamentoConsultaService.configurarPagamentosAoSalvar(
                java.util.List.of(agendamento, segunda),
                profissional,
                profissional
        );

        assertEquals(PagamentoStatus.ESPERANDO_CONFIRMACAO, agendamento.getStatusPagamento());
        assertEquals(PagamentoStatus.PAGAMENTO_FUTURO, segunda.getStatusPagamento());
    }

    @Test
    void deveAbrirJanelaUmDiaAntes() {
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(1).atTime(9, 0));
        assertTrue(pagamentoConsultaService.deveAbrirPagamentoAgora(agendamento));

        agendamento.setDataHoraInicio(LocalDate.now().plusDays(2).atTime(9, 0));
        assertFalse(pagamentoConsultaService.deveAbrirPagamentoAgora(agendamento));
    }

    @Test
    void noDomingoPeriodoAdiantamentoUsaProximaSemana() {
        LocalDate domingo = LocalDate.of(2026, 5, 31);
        var periodo = pagamentoConsultaService.resolverPeriodoSemanaPagamento(domingo);
        assertEquals(LocalDate.of(2026, 6, 1), periodo.inicio());
        assertEquals(LocalDate.of(2026, 6, 7), periodo.fim());
    }

    @Test
    void deSegundaASabadoPeriodoAdiantamentoUsaSemanaCorrente() {
        LocalDate quinta = LocalDate.of(2026, 5, 28);
        var periodo = pagamentoConsultaService.resolverPeriodoSemanaPagamento(quinta);
        assertEquals(LocalDate.of(2026, 5, 25), periodo.inicio());
        assertEquals(LocalDate.of(2026, 5, 31), periodo.fim());
    }

    @Test
    void consultaPagaNaoApareceEmPendenciasNemNaSemana() {
        comDataReferencia(LocalDate.of(2026, 5, 28), LocalTime.of(10, 0), () -> {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setDonaClinica(false);
        profissional.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);

        LocalDate diaAdiantavel = proximoDiaAdiantavelNaSemanaAtual();
        LocalDate diaPaga = LocalDate.now().plusDays(1);
        if (diaPaga.equals(diaAdiantavel)) {
            diaPaga = LocalDate.now();
        }

        Agendamento paga = new Agendamento();
        paga.setId(1L);
        paga.setProfissional(profissional);
        paga.setDataHoraInicio(diaPaga.atTime(10, 0));
        paga.setStatusPagamento(PagamentoStatus.PAGO);

        Agendamento futura = new Agendamento();
        futura.setId(2L);
        futura.setProfissional(profissional);
        futura.setDataHoraInicio(diaAdiantavel.atTime(11, 0));
        futura.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(10L))
                .thenReturn(java.util.List.of(paga, futura));

        var pendencias = pagamentoConsultaService.listarPendenciasObrigatoriasParaBloqueio(profissional);
        var semana = pagamentoConsultaService.listarConsultasAdiantamentoSemanaAtual(profissional);

        assertTrue(pendencias.stream().noneMatch(a -> a.getId().equals(1L)));
        assertTrue(semana.stream().noneMatch(a -> a.getId().equals(1L)));
        assertEquals(1, semana.size());
        assertEquals(2L, semana.get(0).getId());
        });
    }

    @Test
    void listarConsultasAdiantamentoSemanaIgnoraPendenciasObrigatorias() {
        comDataReferencia(LocalDate.of(2026, 5, 28), LocalTime.of(10, 0), () -> {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setDonaClinica(false);

        Agendamento futuraSemana = new Agendamento();
        futuraSemana.setId(1L);
        futuraSemana.setProfissional(profissional);
        futuraSemana.setDataHoraInicio(proximoDiaAdiantavelNaSemanaAtual().atTime(10, 0));
        futuraSemana.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);

        Agendamento bloqueadaHoje = new Agendamento();
        bloqueadaHoje.setId(2L);
        bloqueadaHoje.setProfissional(profissional);
        bloqueadaHoje.setDataHoraInicio(LocalDate.now().atTime(15, 0));
        bloqueadaHoje.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(10L))
                .thenReturn(java.util.List.of(bloqueadaHoje, futuraSemana));

        var semana = pagamentoConsultaService.listarConsultasAdiantamentoSemanaAtual(profissional);

        assertEquals(1, semana.size());
        assertEquals(1L, semana.get(0).getId());
        });
    }

    @Test
    void confirmarPagamentoSemanaMarcaTodasConsultasComoPago() {
        Usuario profissional = new Usuario();
        profissional.setId(1L);

        Agendamento primeira = new Agendamento();
        primeira.setId(10L);
        primeira.setProfissional(profissional);
        primeira.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        primeira.setPagamentoOrderNsu("sem-1-abc12345");
        primeira.setPagamentoLink("http://localhost/pix");
        primeira.setPagamentoExpiraEm(LocalDateTime.now().plusMinutes(5));

        Agendamento segunda = new Agendamento();
        segunda.setId(11L);
        segunda.setProfissional(profissional);
        segunda.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        segunda.setPagamentoOrderNsu("sem-1-abc12345");
        segunda.setPagamentoLink("http://localhost/pix");
        segunda.setPagamentoExpiraEm(LocalDateTime.now().plusMinutes(5));

        when(repository.findAllByPagamentoOrderNsuOrderByDataHoraInicioAsc("sem-1-abc12345"))
                .thenReturn(java.util.List.of(primeira, segunda));
        when(repository.save(any(Agendamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        pagamentoConsultaService.confirmarPagamentoPorOrderNsu("sem-1-abc12345");

        assertEquals(PagamentoStatus.PAGO, primeira.getStatusPagamento());
        assertEquals(PagamentoStatus.PAGO, segunda.getStatusPagamento());
        verify(repository, org.mockito.Mockito.times(2)).save(any(Agendamento.class));
    }

    @Test
    void donaClinicaNaoPrecisaConfirmarPagamento() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);
        agendamento.setProfissional(polyana);
        when(authService.profissionalIgnoraValoresEPagamento(polyana)).thenReturn(true);

        pagamentoConsultaService.configurarPagamentosAoSalvar(java.util.List.of(agendamento), polyana, polyana);

        assertEquals(PagamentoStatus.PAGO, agendamento.getStatusPagamento());
    }

    @Test
    void gestorAgendandoParaOutroDeixaPrimeiraConsultaPagaSemQr() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);

        Usuario anaPaula = new Usuario();
        anaPaula.setId(11L);
        anaPaula.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(authService.profissionalIgnoraValoresEPagamento(anaPaula)).thenReturn(false);

        pagamentoConsultaService.configurarPagamentosAoSalvar(
                java.util.List.of(agendamento),
                anaPaula,
                polyana
        );

        assertEquals(PagamentoStatus.PAGO, agendamento.getStatusPagamento());
        assertNotNull(agendamento.getDataPagamento());
        verify(infinitePayService, never()).gerarLinkPagamento(any());
    }

    @Test
    void gestorAgendandoSerieFixaDeixaPrimeiraPagaERestoComRegraDoProfissional() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);

        Usuario anaPaula = new Usuario();
        anaPaula.setId(11L);
        anaPaula.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);

        Agendamento segunda = new Agendamento();
        segunda.setId(2L);
        segunda.setDataHoraInicio(LocalDate.now().plusWeeks(1).atTime(10, 0));

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(authService.profissionalIgnoraValoresEPagamento(anaPaula)).thenReturn(false);

        pagamentoConsultaService.configurarPagamentosAoSalvar(
                java.util.List.of(agendamento, segunda),
                anaPaula,
                polyana
        );

        assertEquals(PagamentoStatus.PAGO, agendamento.getStatusPagamento());
        assertEquals(PagamentoStatus.PAGAMENTO_FUTURO, segunda.getStatusPagamento());
        verify(infinitePayService, never()).gerarLinkPagamento(any());
    }

    @Test
    void gestorAgendandoQuinzenalDeixaPrimeiraPagaERestoDiarioComoPagamentoFuturo() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);

        Usuario anaPaula = new Usuario();
        anaPaula.setId(11L);
        anaPaula.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);

        Agendamento segunda = new Agendamento();
        segunda.setId(2L);
        segunda.setDataHoraInicio(LocalDate.now().plusWeeks(2).atTime(10, 0));

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(authService.profissionalIgnoraValoresEPagamento(anaPaula)).thenReturn(false);

        pagamentoConsultaService.configurarPagamentosAoSalvar(
                java.util.List.of(agendamento, segunda),
                anaPaula,
                polyana
        );

        assertEquals(PagamentoStatus.PAGO, agendamento.getStatusPagamento());
        assertEquals(PagamentoStatus.PAGAMENTO_FUTURO, segunda.getStatusPagamento());
    }

    @Test
    void bloqueiaSalaNoDiaSemPagamento() {
        agendamento.setDataHoraInicio(LocalDate.now().atTime(20, 0));
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        assertTrue(pagamentoConsultaService.bloqueadoPorPagamento(agendamento));

        agendamento.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        assertFalse(pagamentoConsultaService.bloqueadoPorPagamento(agendamento));

        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        assertFalse(pagamentoConsultaService.bloqueadoPorPagamento(agendamento));
    }

    @Test
    void indicacaoSoBloqueiaAgendaAposPrazoPosAtendimento() {
        when(pagamentoProperties.getIndicacaoDiasLimitePosAtendimento()).thenReturn(2);

        agendamento.setIndicacaoDona(true);
        agendamento.setIndicacaoAprovadaEm(LocalDateTime.now().minusDays(5));
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);

        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0));
        assertFalse(pagamentoConsultaService.bloqueadoPorPagamento(agendamento));

        agendamento.setDataHoraInicio(LocalDateTime.now().minusHours(2));
        assertFalse(pagamentoConsultaService.bloqueadoPorPagamento(agendamento));
        assertTrue(pagamentoConsultaService.podePagarAgora(agendamento));

        agendamento.setDataHoraInicio(LocalDateTime.now().minusDays(4));
        assertTrue(pagamentoConsultaService.bloqueadoPorPagamento(agendamento));
        assertTrue(pagamentoConsultaService.podePagarAgora(agendamento));
    }

    @Test
    void indicacaoApareceEmMeusPagamentosSomenteAposHorarioDaConsulta() {
        when(pagamentoProperties.getIndicacaoDiasLimitePosAtendimento()).thenReturn(2);

        Usuario profissional = new Usuario();
        profissional.setId(10L);

        Agendamento indicacao = new Agendamento();
        indicacao.setId(50L);
        indicacao.setProfissional(profissional);
        indicacao.setIndicacaoDona(true);
        indicacao.setIndicacaoAprovadaEm(LocalDateTime.now().minusDays(1));
        indicacao.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        indicacao.setDataHoraInicio(LocalDateTime.now().plusHours(2).withMinute(0).withSecond(0).withNano(0));

        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(10L))
                .thenReturn(java.util.List.of(indicacao));

        assertTrue(pagamentoConsultaService.listarPagamentosPendentesProximoDia(profissional).isEmpty());

        indicacao.setDataHoraInicio(LocalDateTime.now().minusMinutes(5));
        assertEquals(1, pagamentoConsultaService.listarPagamentosPendentesProximoDia(profissional).size());
        assertFalse(pagamentoConsultaService.profissionalBloqueadoPorPendenciaPagamento(profissional));
    }

    @Test
    void webhookAceitaSemHeaderQuandoSegredoConfigurado() {
        when(pagamentoProperties.getWebhookSecret()).thenReturn("segredo-prod");

        pagamentoConsultaService.validarAutenticacaoWebhook(null);
    }

    @Test
    void webhookRejeitaHeaderIncorreto() {
        when(pagamentoProperties.getWebhookSecret()).thenReturn("segredo-prod");

        assertThrows(
                PagamentoWebhookNaoAutorizadoException.class,
                () -> pagamentoConsultaService.validarAutenticacaoWebhook("outro-segredo")
        );
    }

    @Test
    void confirmarPagamentoWebhookAceitaQrExpirado() {
        Agendamento consulta = new Agendamento();
        consulta.setId(1L);
        consulta.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        consulta.setPagamentoOrderNsu("ag-1-teste");
        consulta.setPagamentoLink("https://checkout.infinitepay.io/pay/x");
        consulta.setPagamentoExpiraEm(LocalDateTime.now().minusMinutes(1));

        when(repository.findAllByPagamentoOrderNsuOrderByDataHoraInicioAsc("ag-1-teste"))
                .thenReturn(java.util.List.of(consulta));
        when(repository.save(any(Agendamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        pagamentoConsultaService.confirmarPagamentoPorWebhook("ag-1-teste");

        assertEquals(PagamentoStatus.PAGO, consulta.getStatusPagamento());
    }

    @Test
    void confirmarPagamentoWebhookExigeQrAtivo() {
        Agendamento consulta = new Agendamento();
        consulta.setId(1L);
        consulta.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        consulta.setPagamentoOrderNsu("ag-1-teste");

        when(repository.findAllByPagamentoOrderNsuOrderByDataHoraInicioAsc("ag-1-teste"))
                .thenReturn(java.util.List.of(consulta));

        assertThrows(
                RuntimeException.class,
                () -> pagamentoConsultaService.confirmarPagamentoPorOrderNsu("ag-1-teste")
        );
    }

    @Test
    void profissionalNaoBloqueadoAposHorarioConsultaHoje() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setDonaClinica(false);

        Agendamento hojePassado = new Agendamento();
        hojePassado.setId(1L);
        hojePassado.setProfissional(profissional);
        hojePassado.setDataHoraInicio(LocalDateTime.now().minusHours(2));
        hojePassado.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(10L))
                .thenReturn(java.util.List.of(hojePassado));

        assertTrue(pagamentoConsultaService.listarPendenciasObrigatoriasParaBloqueio(profissional).isEmpty());
    }

    @Test
    void listarPagamentosPendentesProximoDiaFiltraSomenteAmanha() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setDonaClinica(false);

        Agendamento amanha = new Agendamento();
        amanha.setId(1L);
        amanha.setProfissional(profissional);
        amanha.setDataHoraInicio(LocalDate.now().plusDays(1).atTime(9, 0));
        amanha.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);

        Agendamento depoisDeAmanha = new Agendamento();
        depoisDeAmanha.setId(2L);
        depoisDeAmanha.setProfissional(profissional);
        depoisDeAmanha.setDataHoraInicio(LocalDate.now().plusDays(2).atTime(9, 0));
        depoisDeAmanha.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(10L))
                .thenReturn(java.util.List.of(amanha, depoisDeAmanha));

        var exibicao = pagamentoConsultaService.listarPagamentosPendentesProximoDia(profissional);
        var bloqueio = pagamentoConsultaService.listarPendenciasObrigatoriasParaBloqueio(profissional);

        assertEquals(1, exibicao.size());
        assertEquals(1L, exibicao.get(0).getId());
        assertTrue(bloqueio.size() >= 1);
    }

    @Test
    void listarPendenciasObrigatoriasIncluiSomenteBloqueioOuPagamentoDoDia() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setDonaClinica(false);

        Agendamento bloqueadoHoje = new Agendamento();
        bloqueadoHoje.setId(1L);
        bloqueadoHoje.setProfissional(profissional);
        bloqueadoHoje.setDataHoraInicio(LocalDateTime.now().plusHours(2));
        bloqueadoHoje.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);

        Agendamento pagamentoAbertoAmanha = new Agendamento();
        pagamentoAbertoAmanha.setId(2L);
        pagamentoAbertoAmanha.setProfissional(profissional);
        pagamentoAbertoAmanha.setDataHoraInicio(LocalDate.now().plusDays(1).atTime(9, 0));
        pagamentoAbertoAmanha.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);

        Agendamento pagamentoFuturo = new Agendamento();
        pagamentoFuturo.setId(3L);
        pagamentoFuturo.setProfissional(profissional);
        pagamentoFuturo.setDataHoraInicio(LocalDate.now().plusDays(5).atTime(9, 0));
        pagamentoFuturo.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(10L))
                .thenReturn(java.util.List.of(bloqueadoHoje, pagamentoAbertoAmanha, pagamentoFuturo));

        var pendencias = pagamentoConsultaService.listarPendenciasObrigatoriasParaBloqueio(profissional);

        assertEquals(2, pendencias.size());
        assertEquals(1L, pendencias.get(0).getId());
        assertEquals(2L, pendencias.get(1).getId());
    }

    @Test
    void rotuloEsperandoNaGradeParaOutroProfissional() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        Usuario carol = new Usuario();
        carol.setId(2L);

        agendamento.setProfissional(julia);
        agendamento.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);

        when(authService.isAdmin(carol)).thenReturn(false);
        when(authService.isDonaClinica(carol)).thenReturn(false);

        assertEquals("Aguardando confirmações",
                pagamentoConsultaService.rotuloEsperandoNaGrade(agendamento, carol));
    }

    @Test
    void rotuloEsperandoNaGradeParaProfissionalDoAgendamento() {
        Usuario julia = new Usuario();
        julia.setId(1L);

        agendamento.setProfissional(julia);
        agendamento.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);

        assertEquals("Esperando pagamento",
                pagamentoConsultaService.rotuloEsperandoNaGrade(agendamento, julia));
    }

    @Test
    void rotuloPagoNaGradeParaOutroProfissional() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        Usuario carol = new Usuario();
        carol.setId(2L);

        agendamento.setProfissional(julia);
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);

        when(authService.isAdmin(carol)).thenReturn(false);
        when(authService.isDonaClinica(carol)).thenReturn(false);

        assertEquals("Sala confirmada",
                pagamentoConsultaService.rotuloPagoNaGrade(agendamento, carol));
    }

    @Test
    void rotuloPagoNaGradeParaProfissionalDoAgendamento() {
        Usuario julia = new Usuario();
        julia.setId(1L);

        agendamento.setProfissional(julia);
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);

        assertEquals("Pago", pagamentoConsultaService.rotuloPagoNaGrade(agendamento, julia));
    }

    @Test
    void rotuloEsperandoNaGradeMensalMostraJanelaFutura() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        julia.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);

        LocalDate referencia = LocalDate.of(2026, 5, 15);
        agendamento.setProfissional(julia);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataHoraInicio(referencia.atTime(10, 0));
        agendamento.setDataReferenciaMesPagamento(LocalDate.of(2026, 5, 1));

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);

        assertEquals("Você vai pagar do dia 01 ao 15/06",
                pagamentoConsultaService.rotuloEsperandoNaGrade(agendamento, julia));
    }

    @Test
    void rotuloMensalNaJanelaDePagamentoMostraAviso01Ao10() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        julia.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);

        agendamento.setProfissional(julia);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataReferenciaMesPagamento(LocalDate.of(2026, 6, 1));
        agendamento.setDataHoraInicio(LocalDate.of(2026, 6, 29).atTime(10, 0));

        assertEquals("Você tem do dia 01 ao 15 para pagar",
                pagamentoConsultaService.rotuloPagamentoFuturoMensal(agendamento, LocalDate.of(2026, 7, 5)));
    }

    @Test
    void realocacaoMensalDeJunhoParaJulhoMantemFaturaDeJunho() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        julia.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);

        agendamento.setProfissional(julia);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataReferenciaMesPagamento(LocalDate.of(2026, 6, 1));
        agendamento.setDataHoraInicio(LocalDate.of(2026, 7, 7).atTime(10, 0));

        assertEquals("06/2026", pagamentoConsultaService.rotuloMesCobranca(agendamento));
        assertEquals("01 ao 15/07", pagamentoConsultaService.formatarJanelaPagamentoMensal(agendamento));
        assertEquals("Você vai pagar do dia 01 ao 15/07",
                pagamentoConsultaService.rotuloPagamentoFuturoMensal(agendamento, LocalDate.of(2026, 6, 30)));
    }

    @Test
    void referenciaMesPreservaRotuloAposRealocacao() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        julia.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);

        agendamento.setProfissional(julia);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataReferenciaMesPagamento(LocalDate.of(2026, 5, 1));
        agendamento.setDataHoraInicio(LocalDate.of(2026, 6, 8).atTime(10, 0));

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);

        assertEquals("Você vai pagar do dia 01 ao 15/06",
                pagamentoConsultaService.rotuloEsperandoNaGrade(agendamento, julia));
        assertEquals("05/2026", pagamentoConsultaService.rotuloMesCobranca(agendamento));
    }

    @Test
    void rotuloEsperandoNaGradeSemanalMostraDataPagamento() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        julia.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);

        LocalDate referencia = LocalDate.of(2026, 6, 1);
        agendamento.setProfissional(julia);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataHoraInicio(referencia.atTime(10, 0));
        agendamento.setDataReferenciaSemanaPagamento(referencia);

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);

        assertEquals("Você vai pagar no dia 07/06",
                pagamentoConsultaService.rotuloEsperandoNaGrade(agendamento, julia));
    }

    @Test
    void referenciaSemanaPreservaRotuloAposRealocacao() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        julia.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);

        LocalDate referenciaOriginal = LocalDate.of(2026, 6, 1);
        agendamento.setProfissional(julia);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataReferenciaSemanaPagamento(referenciaOriginal);
        agendamento.setDataHoraInicio(LocalDate.of(2026, 6, 8).atTime(10, 0));

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);

        assertEquals("Você vai pagar no dia 07/06",
                pagamentoConsultaService.rotuloEsperandoNaGrade(agendamento, julia));
    }

    @Test
    void resolverSemanaPorDataReferenciaIncluiDomingoNaMesmaSemana() {
        LocalDate domingo = LocalDate.of(2026, 6, 7);
        var periodo = pagamentoConsultaService.resolverSemanaPorDataReferencia(domingo);
        assertEquals(LocalDate.of(2026, 6, 1), periodo.inicio());
        assertEquals(LocalDate.of(2026, 6, 7), periodo.fim());
    }

    @Test
    void polyanaVeRotuloDePagamentoDoProfissionalNaGrade() {
        Usuario anaPaula = new Usuario();
        anaPaula.setId(1L);
        anaPaula.setNome("Ana Paula");
        anaPaula.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);

        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);

        agendamento.setProfissional(anaPaula);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataHoraInicio(LocalDate.of(2026, 6, 15).atTime(15, 0));
        agendamento.setDataReferenciaMesPagamento(LocalDate.of(2026, 6, 1));

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);

        assertEquals(
                "Ana Paula: pagamento do dia 01 ao 15/07",
                pagamentoConsultaService.rotuloEsperandoNaGrade(agendamento, polyana)
        );
    }

    @Test
    void polyanaVeRotuloConfirmadoQuandoPrimeiraConsultaEstaPaga() {
        Usuario anaPaula = new Usuario();
        anaPaula.setId(1L);
        anaPaula.setNome("Ana Paula");

        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);

        agendamento.setProfissional(anaPaula);
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);

        assertEquals(
                "Confirmado — acerto com a profissional",
                pagamentoConsultaService.rotuloPagoNaGrade(agendamento, polyana)
        );
    }

    @Test
    void rotuloEsperandoNaGradeDeProfissionalMensalParaOutroMostraSalaConfirmada() {
        Usuario anaPaula = new Usuario();
        anaPaula.setId(1L);
        anaPaula.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);
        Usuario julia = new Usuario();
        julia.setId(2L);
        julia.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);

        agendamento.setProfissional(anaPaula);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(10).atTime(15, 0));
        agendamento.setDataReferenciaMesPagamento(LocalDate.now().plusDays(10).withDayOfMonth(1));

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);

        assertTrue(pagamentoConsultaService.exibirSalaConfirmadaNaGrade(agendamento, julia));
        assertEquals("Sala confirmada",
                pagamentoConsultaService.rotuloEsperandoNaGrade(agendamento, julia));
    }

    @Test
    void rotuloEsperandoNaGradeDeProfissionalSemanalParaOutroMostraSalaConfirmada() {
        Usuario anaPaula = new Usuario();
        anaPaula.setId(1L);
        anaPaula.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);
        Usuario julia = new Usuario();
        julia.setId(2L);

        agendamento.setProfissional(anaPaula);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(5).atTime(15, 0));

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);

        assertEquals("Sala confirmada",
                pagamentoConsultaService.rotuloEsperandoNaGrade(agendamento, julia));
    }

    @Test
    void rotuloPendenteNaGradeParaPagamentoFuturoDeOutroProfissional() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        Usuario carol = new Usuario();
        carol.setId(2L);

        agendamento.setProfissional(julia);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(10).atTime(10, 0));
        julia.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);

        when(authService.isAdmin(carol)).thenReturn(false);
        when(authService.isDonaClinica(carol)).thenReturn(false);

        assertTrue(agendamento.isReservaPendenteNaGrade());
        assertEquals("Aguardando confirmações",
                pagamentoConsultaService.rotuloEsperandoNaGrade(agendamento, carol));
    }

    @Test
    void expirarPrimeiroPagamentoDaSerieRemoveTodaSerieNaoPaga() {
        Agendamento expirado = new Agendamento();
        expirado.setId(99L);
        expirado.setSerieFixaId("serie-julia-1");
        expirado.setDataHoraInicio(LocalDate.now().plusDays(1).atTime(10, 0));
        expirado.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        expirado.setPagamentoExpiraEm(LocalDateTime.now().minusMinutes(1));

        when(repository.findByStatusPagamentoAndPagamentoExpiraEmBefore(
                eq(PagamentoStatus.ESPERANDO_CONFIRMACAO),
                any(LocalDateTime.class)
        )).thenReturn(java.util.List.of(expirado));
        when(repository.findFirstBySerieFixaIdOrderByDataHoraInicioAsc("serie-julia-1"))
                .thenReturn(java.util.Optional.of(expirado));
        when(repository.deleteBySerieFixaIdAndStatusPagamentoNot("serie-julia-1", PagamentoStatus.PAGO))
                .thenReturn(3);

        int removidos = pagamentoConsultaService.expirarPagamentosVencidos();

        assertEquals(3, removidos);
        verify(repository, never()).deleteById(99L);
        verify(repository).deleteBySerieFixaIdAndStatusPagamentoNot("serie-julia-1", PagamentoStatus.PAGO);
    }

    @Test
    void expirarPagamentoDaProximaConsultaApenasExpiraQrSemExcluir() {
        Agendamento primeira = new Agendamento();
        primeira.setId(10L);
        primeira.setSerieFixaId("serie-julia-2");
        primeira.setDataHoraInicio(LocalDate.now().plusDays(2).atTime(10, 0));

        Agendamento proxima = new Agendamento();
        proxima.setId(11L);
        proxima.setSerieFixaId("serie-julia-2");
        proxima.setDataHoraInicio(LocalDate.now().plusDays(9).atTime(10, 0));
        proxima.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        proxima.setPagamentoExpiraEm(LocalDateTime.now().minusMinutes(1));
        proxima.setPagamentoLink("http://localhost/link");
        proxima.setPagamentoOrderNsu("ord-1");
        proxima.setPagamentoSlug("slug-1");
        proxima.setPagamentoIniciadoEm(LocalDateTime.now().minusMinutes(6));

        when(repository.findByStatusPagamentoAndPagamentoExpiraEmBefore(
                eq(PagamentoStatus.ESPERANDO_CONFIRMACAO),
                any(LocalDateTime.class)
        )).thenReturn(java.util.List.of(proxima));
        when(repository.findFirstBySerieFixaIdOrderByDataHoraInicioAsc("serie-julia-2"))
                .thenReturn(java.util.Optional.of(primeira));

        int processados = pagamentoConsultaService.expirarPagamentosVencidos();

        assertEquals(1, processados);
        assertEquals(PagamentoStatus.PAGAMENTO_FUTURO, proxima.getStatusPagamento());
        assertEquals(null, proxima.getPagamentoLink());
        assertEquals(null, proxima.getPagamentoOrderNsu());
        verify(repository, never()).deleteById(11L);
        verify(repository).save(proxima);
    }

    @Test
    void expirarPagamentoAvulsoRemoveSomenteHorario() {
        Agendamento avulso = new Agendamento();
        avulso.setId(77L);
        avulso.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        avulso.setPagamentoExpiraEm(LocalDateTime.now().minusMinutes(1));

        when(repository.findByStatusPagamentoAndPagamentoExpiraEmBefore(
                eq(PagamentoStatus.ESPERANDO_CONFIRMACAO),
                any(LocalDateTime.class)
        )).thenReturn(java.util.List.of(avulso));

        int removidos = pagamentoConsultaService.expirarPagamentosVencidos();

        assertEquals(1, removidos);
        verify(repository).deleteById(77L);
        verify(repository, never()).deleteBySerieFixaIdAndStatusPagamentoNot(any(), any());
    }

    @Test
    void listarProfissionaisBloqueadosPorPagamentoRetornaSomenteComPendencia() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        julia.setNome("Julia");
        julia.setLogin("julia");
        julia.setCargo("ROLE_PROFISSIONAL");

        Usuario carol = new Usuario();
        carol.setId(2L);
        carol.setNome("Carol");
        carol.setLogin("carol");
        carol.setCargo("ROLE_PROFISSIONAL");

        Agendamento pendenteJulia = new Agendamento();
        pendenteJulia.setId(10L);
        pendenteJulia.setProfissional(julia);
        pendenteJulia.setDataHoraInicio(LocalDateTime.now().plusHours(2));
        pendenteJulia.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);

        when(usuarioRepository.findByCargoOrderByNomeAsc("ROLE_PROFISSIONAL"))
                .thenReturn(java.util.List.of(carol, julia));
        when(authService.profissionalIgnoraValoresEPagamento(julia)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(carol)).thenReturn(false);
        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);
        when(authService.isAdmin(carol)).thenReturn(false);
        when(authService.isDonaClinica(carol)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(1L))
                .thenReturn(java.util.List.of(pendenteJulia));
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(2L))
                .thenReturn(java.util.List.of());

        var bloqueados = pagamentoConsultaService.listarProfissionaisBloqueadosPorPagamento();

        assertEquals(1, bloqueados.size());
        assertEquals(1L, bloqueados.get(0).getProfissionalId());
        assertEquals("Julia", bloqueados.get(0).getNome());
        assertEquals("Sistema do usuário Julia foi bloqueado por não pagar.", bloqueados.get(0).getMensagemBloqueio());
        assertEquals(1, bloqueados.get(0).getPendencias());
    }

    @Test
    void profissionalEstaBloqueadoPorPagamentoQuandoTemPendencia() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        julia.setDonaClinica(false);

        Agendamento pendente = new Agendamento();
        pendente.setId(10L);
        pendente.setProfissional(julia);
        pendente.setDataHoraInicio(LocalDateTime.now().plusHours(2));
        pendente.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);

        when(usuarioRepository.findById(1L)).thenReturn(java.util.Optional.of(julia));
        when(authService.profissionalIgnoraValoresEPagamento(julia)).thenReturn(false);
        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(1L))
                .thenReturn(java.util.List.of(pendente));

        assertTrue(pagamentoConsultaService.profissionalEstaBloqueadoPorPagamento(1L));
    }

    @Test
    void mensagemBloqueioPagamentoDeveTrazerDataDaPendencia() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        julia.setDonaClinica(false);
        julia.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);

        Agendamento pendente = new Agendamento();
        pendente.setId(11L);
        pendente.setProfissional(julia);
        LocalDate dataPendente = LocalDate.now().plusDays(1);
        pendente.setDataHoraInicio(dataPendente.atTime(9, 0));
        pendente.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);

        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(1L))
                .thenReturn(java.util.List.of(pendente));

        String mensagem = pagamentoConsultaService.mensagemBloqueioPagamento(julia);

        assertTrue(mensagem.contains(dataPendente.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))));
        assertTrue(mensagem.contains("dia anterior"));
    }

    @Test
    void liberaVagaAposMeiaNoiteDoDiaDaConsulta() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        julia.setDonaClinica(false);

        Agendamento amanha = new Agendamento();
        amanha.setId(50L);
        amanha.setProfissional(julia);
        amanha.setDataHoraInicio(LocalDate.now().plusDays(1).atTime(11, 0));
        amanha.setDataHoraFim(LocalDate.now().plusDays(1).atTime(12, 0));
        amanha.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);

        when(repository.findByStatusPagamentoInAndDataHoraInicioGreaterThanEqual(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(java.util.List.of(amanha));
        when(authService.profissionalIgnoraValoresEPagamento(julia)).thenReturn(false);

        int liberados = pagamentoConsultaService.liberarVagasPorFaltaPagamento();

        assertEquals(0, liberados);

        amanha.setDataHoraInicio(LocalDate.now().atTime(11, 0));
        amanha.setDataHoraFim(LocalDate.now().atTime(12, 0));

        liberados = pagamentoConsultaService.liberarVagasPorFaltaPagamento();

        assertEquals(1, liberados);
        assertEquals(PagamentoStatus.LIBERADO_FALTA_PAGAMENTO, amanha.getStatusPagamento());
        assertNotNull(amanha.getLiberadoEm());
    }

    @Test
    void pagarAposLiberacaoFalhaQuandoOutroReservou() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        Usuario carol = new Usuario();
        carol.setId(2L);

        Agendamento liberado = new Agendamento();
        liberado.setId(10L);
        liberado.setProfissional(julia);
        liberado.setSala(new com.clinica.sistema.model.Sala());
        liberado.getSala().setId(1L);
        liberado.getSala().setNome("Sala 1");
        liberado.setDataHoraInicio(LocalDate.now().plusDays(1).atTime(11, 0));
        liberado.setDataHoraFim(LocalDate.now().plusDays(1).atTime(12, 0));
        liberado.setStatusPagamento(PagamentoStatus.LIBERADO_FALTA_PAGAMENTO);

        Agendamento deCarol = new Agendamento();
        deCarol.setId(20L);
        deCarol.setProfissional(carol);

        when(repository.findById(10L)).thenReturn(java.util.Optional.of(liberado));
        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);
        when(repository.findFirstOcupacaoAtivaNoHorarioExceto(1L, liberado.getDataHoraInicio(),
                liberado.getDataHoraFim(), 10L)).thenReturn(java.util.Optional.of(deCarol));

        HorarioJaReservadoPorOutroProfissionalException erro = assertThrows(
                HorarioJaReservadoPorOutroProfissionalException.class,
                () -> pagamentoConsultaService.pagarAgora(10L, julia)
        );

        assertTrue(erro.getMessage().contains("preenchida"));
    }

    @Test
    void liberadoPodePagarAposMeiaNoiteSeVagaAindaLivre() {
        Agendamento liberado = new Agendamento();
        liberado.setId(11L);
        liberado.setProfissional(new Usuario());
        liberado.getProfissional().setId(1L);
        liberado.setSala(new com.clinica.sistema.model.Sala());
        liberado.getSala().setId(1L);
        liberado.setDataHoraInicio(LocalDate.now().plusDays(1).atTime(11, 0));
        liberado.setDataHoraFim(LocalDate.now().plusDays(1).atTime(12, 0));
        liberado.setStatusPagamento(PagamentoStatus.LIBERADO_FALTA_PAGAMENTO);

        when(repository.findFirstOcupacaoAtivaNoHorarioExceto(
                eq(1L),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(11L)
        )).thenReturn(java.util.Optional.empty());
        when(authService.profissionalIgnoraValoresEPagamento(liberado.getProfissional())).thenReturn(false);

        assertTrue(pagamentoConsultaService.podePagarAgora(liberado));
        assertTrue(pagamentoConsultaService.podeRecuperarVagaComPagamento(liberado));
    }

    @Test
    void liberadoNaoPodePagarAposHorarioDaConsulta() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        julia.setDonaClinica(false);

        Agendamento liberadoAtrasado = new Agendamento();
        liberadoAtrasado.setId(30L);
        liberadoAtrasado.setProfissional(julia);
        liberadoAtrasado.setSala(new com.clinica.sistema.model.Sala());
        liberadoAtrasado.getSala().setId(1L);
        liberadoAtrasado.setDataHoraInicio(LocalDateTime.now().minusHours(1));
        liberadoAtrasado.setDataHoraFim(LocalDateTime.now());
        liberadoAtrasado.setStatusPagamento(PagamentoStatus.LIBERADO_FALTA_PAGAMENTO);

        when(authService.profissionalIgnoraValoresEPagamento(julia)).thenReturn(false);
        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(1L))
                .thenReturn(java.util.List.of(liberadoAtrasado));

        assertFalse(pagamentoConsultaService.podePagarAgora(liberadoAtrasado));
        assertFalse(pagamentoConsultaService.podeRecuperarVagaComPagamento(liberadoAtrasado));
        assertTrue(pagamentoConsultaService.listarPendenciasObrigatoriasParaBloqueio(julia).isEmpty());
    }

    @Test
    void liberadoNaoOcupaVagaNaGrade() {
        Agendamento liberado = new Agendamento();
        liberado.setStatusPagamento(PagamentoStatus.LIBERADO_FALTA_PAGAMENTO);
        assertFalse(pagamentoConsultaService.ocupaVagaNaGrade(liberado));
    }

    @Test
    void rotuloLiberadoAposHorarioMostraPagamentoExpirado() {
        Agendamento liberadoAtrasado = new Agendamento();
        liberadoAtrasado.setProfissional(new Usuario());
        liberadoAtrasado.getProfissional().setId(1L);
        liberadoAtrasado.setDataHoraInicio(LocalDateTime.now().minusHours(1));
        liberadoAtrasado.setStatusPagamento(PagamentoStatus.LIBERADO_FALTA_PAGAMENTO);

        assertEquals("Pagamento expirado", pagamentoConsultaService.rotuloStatusPagamento(liberadoAtrasado));
    }

    @Test
    void naoLiberaVagaQuandoEsperandoConfirmacaoComQrAtivo() {
        Usuario julia = new Usuario();
        julia.setId(1L);

        Agendamento esperando = new Agendamento();
        esperando.setId(60L);
        esperando.setProfissional(julia);
        esperando.setDataHoraInicio(LocalDate.now().atTime(11, 0));
        esperando.setDataHoraFim(LocalDate.now().atTime(12, 0));
        esperando.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        esperando.setPagamentoExpiraEm(LocalDateTime.now().plusMinutes(3));
        esperando.setPagamentoLink("http://localhost/link");

        when(repository.findByStatusPagamentoInAndDataHoraInicioGreaterThanEqual(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(java.util.List.of(esperando));

        int liberados = pagamentoConsultaService.liberarVagasPorFaltaPagamento();

        assertEquals(0, liberados);
        assertEquals(PagamentoStatus.ESPERANDO_CONFIRMACAO, esperando.getStatusPagamento());
    }

    @Test
    void deveMigrarAgendamentosFuturosDeDiarioParaMensal() {
        Usuario carol = new Usuario();
        carol.setId(5L);
        carol.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);

        Agendamento futuro = new Agendamento();
        futuro.setId(20L);
        futuro.setProfissional(carol);
        futuro.setDataHoraInicio(LocalDate.now().plusDays(5).atTime(15, 0));
        futuro.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        futuro.setPagamentoOrderNsu("ordem-antiga");
        futuro.setPagamentoLink("http://localhost/link");
        futuro.setPagamentoExpiraEm(LocalDateTime.now().plusMinutes(3));

        when(authService.profissionalIgnoraValoresEPagamento(carol)).thenReturn(false);
        when(repository.findByProfissionalIdAndDataHoraInicioGreaterThanOrderByDataHoraInicioAsc(
                eq(5L),
                any(LocalDateTime.class)
        )).thenReturn(java.util.List.of(futuro));
        when(repository.save(futuro)).thenReturn(futuro);

        int migrados = pagamentoConsultaService.migrarAgendamentosAoAlterarPeriodicidade(
                carol,
                PeriodicidadePagamento.DIARIO,
                PeriodicidadePagamento.MENSAL
        );

        assertEquals(1, migrados);
        assertEquals(PagamentoStatus.PAGAMENTO_FUTURO, futuro.getStatusPagamento());
        assertEquals(LocalDate.now().plusDays(5).withDayOfMonth(1), futuro.getDataReferenciaMesPagamento());
        assertEquals(null, futuro.getDataReferenciaSemanaPagamento());
        assertEquals(null, futuro.getPagamentoOrderNsu());
        assertEquals(null, futuro.getPagamentoLink());
        verify(repository).save(futuro);
    }

    @Test
    void naoDeveMigrarAgendamentosPagosAoAlterarPeriodicidade() {
        Usuario carol = new Usuario();
        carol.setId(5L);
        carol.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);

        Agendamento pago = new Agendamento();
        pago.setId(21L);
        pago.setProfissional(carol);
        pago.setDataHoraInicio(LocalDate.now().plusDays(2).atTime(10, 0));
        pago.setStatusPagamento(PagamentoStatus.PAGO);

        when(authService.profissionalIgnoraValoresEPagamento(carol)).thenReturn(false);
        when(repository.findByProfissionalIdAndDataHoraInicioGreaterThanOrderByDataHoraInicioAsc(
                eq(5L),
                any(LocalDateTime.class)
        )).thenReturn(java.util.List.of(pago));

        int migrados = pagamentoConsultaService.migrarAgendamentosAoAlterarPeriodicidade(
                carol,
                PeriodicidadePagamento.DIARIO,
                PeriodicidadePagamento.MENSAL
        );

        assertEquals(0, migrados);
        assertEquals(PagamentoStatus.PAGO, pago.getStatusPagamento());
        verify(repository, never()).save(pago);
    }

    @Test
    void deveMigrarAgendamentosDeSemanalParaMensalRecalculandoReferencia() {
        Usuario carol = new Usuario();
        carol.setId(5L);
        carol.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);

        LocalDate consulta = LocalDate.now().plusDays(10);
        Agendamento futuro = new Agendamento();
        futuro.setId(22L);
        futuro.setProfissional(carol);
        futuro.setDataHoraInicio(consulta.atTime(9, 0));
        futuro.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        futuro.setDataReferenciaSemanaPagamento(consulta.minusDays(2));

        when(authService.profissionalIgnoraValoresEPagamento(carol)).thenReturn(false);
        when(repository.findByProfissionalIdAndDataHoraInicioGreaterThanOrderByDataHoraInicioAsc(
                eq(5L),
                any(LocalDateTime.class)
        )).thenReturn(java.util.List.of(futuro));
        when(repository.save(futuro)).thenReturn(futuro);

        int migrados = pagamentoConsultaService.migrarAgendamentosAoAlterarPeriodicidade(
                carol,
                PeriodicidadePagamento.SEMANAL,
                PeriodicidadePagamento.MENSAL
        );

        assertEquals(1, migrados);
        assertEquals(consulta.withDayOfMonth(1), futuro.getDataReferenciaMesPagamento());
        assertEquals(null, futuro.getDataReferenciaSemanaPagamento());
    }

    // --- Cobertura completa: diario, semanal, mensal e migracoes ---

    @Test
    void janelaPagamentoSemanalSoSabadoEDomingo() {
        DayOfWeek dia = LocalDate.now().getDayOfWeek();
        boolean esperado = dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
        assertEquals(esperado, pagamentoConsultaService.estaEmJanelaPagamentoSemanal());
    }

    @Test
    void janelaPagamentoMensalDoDia01Ao15() {
        int dia = LocalDate.now().getDayOfMonth();
        boolean esperado = dia >= 1 && dia <= 15;
        assertEquals(esperado, pagamentoConsultaService.estaEmJanelaPagamentoMensal());
    }

    @Test
    void profissionalSemanalForaDaJanelaNaoListaConsultasParaPagamento() {
        assumeFalse(
                pagamentoConsultaService.estaEmJanelaPagamentoSemanal(),
                "Lista vazia fora da janela: valido de segunda a sexta"
        );

        Usuario julia = new Usuario();
        julia.setId(3L);
        julia.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);

        assertTrue(pagamentoConsultaService.listarConsultasAdiantamentoSemanaAtual(julia).isEmpty());
    }

    @Test
    void profissionalSemanalNaJanelaListaConsultasNaoPagasDaSemana() {
        assumeTrue(
                pagamentoConsultaService.estaEmJanelaPagamentoSemanal(),
                "Lista na janela: valido sabado e domingo"
        );

        Usuario julia = new Usuario();
        julia.setId(3L);
        julia.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);

        LocalDate referencia = LocalDate.now();
        Agendamento consulta = new Agendamento();
        consulta.setId(31L);
        consulta.setProfissional(julia);
        consulta.setDataHoraInicio(referencia.atTime(10, 0));
        consulta.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        consulta.setDataReferenciaSemanaPagamento(referencia);

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(julia)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(3L))
                .thenReturn(java.util.List.of(consulta));

        var consultas = pagamentoConsultaService.listarConsultasAdiantamentoSemanaAtual(julia);
        assertFalse(consultas.isEmpty());
        assertEquals(31L, consultas.get(0).getId());
    }

    @Test
    void gerarPixSemanaRejeitaProfissionalSemanalForaDaJanela() {
        assumeFalse(pagamentoConsultaService.estaEmJanelaPagamentoSemanal());

        Usuario julia = new Usuario();
        julia.setId(3L);
        julia.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> pagamentoConsultaService.gerarPagamentoUnicoSemanaAtual(julia)
        );
        assertEquals("Pagamento semanal disponível apenas sábado e domingo.", ex.getMessage());
    }

    @Test
    void gerarPixSemanaProfissionalDiarioMarcaConsultasEmEsperandoConfirmacao() {
        comDataReferencia(LocalDate.of(2026, 5, 28), LocalTime.of(10, 0), () -> {
        Usuario carol = new Usuario();
        carol.setId(4L);
        carol.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);

        LocalDate diaConsulta = proximoDiaAdiantavelNaSemanaAtual();

        Agendamento consulta = new Agendamento();
        consulta.setId(40L);
        consulta.setProfissional(carol);
        consulta.setDataHoraInicio(diaConsulta.atTime(14, 0));
        consulta.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        consulta.setValorClinicaCobra(new BigDecimal("35.00"));

        when(authService.isAdmin(carol)).thenReturn(false);
        when(authService.isDonaClinica(carol)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(carol)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(4L))
                .thenReturn(java.util.List.of(consulta));
        when(pagamentoProperties.getPrazoConfirmacaoMinutos()).thenReturn(5);
        when(infinitePayService.resolverValorTaxaClinica(consulta)).thenReturn(new BigDecimal("35.00"));
        when(infinitePayService.gerarLinkPagamentoSemana(java.util.List.of(consulta)))
                .thenReturn(new com.clinica.sistema.dto.LinkPagamentoGerado(
                        "sem-4-abc12345", "http://localhost/pix-semana", "slug-semana"
                ));
        when(repository.save(consulta)).thenReturn(consulta);

        String orderNsu = pagamentoConsultaService.gerarPagamentoUnicoSemanaAtual(carol);

        assertEquals("sem-4-abc12345", orderNsu);
        assertEquals(PagamentoStatus.ESPERANDO_CONFIRMACAO, consulta.getStatusPagamento());
        assertEquals("http://localhost/pix-semana", consulta.getPagamentoLink());
        verify(repository).save(consulta);
        });
    }

    @Test
    void listarConsultasPagamentoMensalRetornaConsultasDoMesAnterior() {
        Usuario anaPaula = new Usuario();
        anaPaula.setId(7L);
        anaPaula.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);

        YearMonth mesAnterior = YearMonth.from(LocalDate.now()).minusMonths(1);
        LocalDate diaConsulta = mesAnterior.atDay(12);

        Agendamento doMesAnterior = new Agendamento();
        doMesAnterior.setId(50L);
        doMesAnterior.setProfissional(anaPaula);
        doMesAnterior.setDataHoraInicio(diaConsulta.atTime(9, 0));
        doMesAnterior.setDataReferenciaMesPagamento(mesAnterior.atDay(1));
        doMesAnterior.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);

        Agendamento doMesAtual = new Agendamento();
        doMesAtual.setId(51L);
        doMesAtual.setProfissional(anaPaula);
        doMesAtual.setDataHoraInicio(LocalDate.now().plusDays(5).atTime(9, 0));
        doMesAtual.setDataReferenciaMesPagamento(LocalDate.now().withDayOfMonth(1));
        doMesAtual.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);

        when(authService.isAdmin(anaPaula)).thenReturn(false);
        when(authService.isDonaClinica(anaPaula)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(anaPaula)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(7L))
                .thenReturn(java.util.List.of(doMesAnterior, doMesAtual));

        var consultas = pagamentoConsultaService.listarConsultasPagamentoMensal(anaPaula);

        assertEquals(1, consultas.size());
        assertEquals(50L, consultas.get(0).getId());
    }

    @Test
    void listarConsultasPagamentoMensalVazioParaProfissionalDiario() {
        Usuario carol = new Usuario();
        carol.setId(4L);
        carol.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);

        assertTrue(pagamentoConsultaService.listarConsultasPagamentoMensal(carol).isEmpty());
    }

    @Test
    void gerarPixMesAnteriorRejeitaProfissionalDiario() {
        Usuario carol = new Usuario();
        carol.setId(4L);
        carol.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> pagamentoConsultaService.gerarPagamentoUnicoMesAnterior(carol)
        );
        assertEquals("Pagamento mensal não se aplica a este profissional.", ex.getMessage());
    }

    @Test
    void gerarPixMesAnteriorMarcaConsultasEmEsperandoConfirmacao() {
        Usuario anaPaula = new Usuario();
        anaPaula.setId(7L);
        anaPaula.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);

        YearMonth mesAnterior = YearMonth.from(LocalDate.now()).minusMonths(1);
        Agendamento consulta = new Agendamento();
        consulta.setId(60L);
        consulta.setProfissional(anaPaula);
        consulta.setDataHoraInicio(mesAnterior.atDay(8).atTime(11, 0));
        consulta.setDataReferenciaMesPagamento(mesAnterior.atDay(1));
        consulta.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        consulta.setValorClinicaCobra(new BigDecimal("35.00"));

        when(authService.isAdmin(anaPaula)).thenReturn(false);
        when(authService.isDonaClinica(anaPaula)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(anaPaula)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(7L))
                .thenReturn(java.util.List.of(consulta));
        when(pagamentoProperties.getPrazoConfirmacaoMinutos()).thenReturn(5);
        when(infinitePayService.resolverValorTaxaClinica(consulta)).thenReturn(new BigDecimal("35.00"));
        when(infinitePayService.gerarLinkPagamentoMes(java.util.List.of(consulta)))
                .thenReturn(new com.clinica.sistema.dto.LinkPagamentoGerado(
                        "mes-7-abc12345", "http://localhost/pix-mes", "slug-mes"
                ));
        when(repository.save(consulta)).thenReturn(consulta);

        String orderNsu = pagamentoConsultaService.gerarPagamentoUnicoMesAnterior(anaPaula);

        assertEquals("mes-7-abc12345", orderNsu);
        assertEquals(PagamentoStatus.ESPERANDO_CONFIRMACAO, consulta.getStatusPagamento());
        assertEquals("http://localhost/pix-mes", consulta.getPagamentoLink());
    }

    @Test
    void confirmarPagamentoMensalMarcaTodasConsultasComoPago() {
        Usuario anaPaula = new Usuario();
        anaPaula.setId(7L);

        Agendamento primeira = new Agendamento();
        primeira.setId(70L);
        primeira.setProfissional(anaPaula);
        primeira.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        primeira.setPagamentoOrderNsu("mes-7-abc12345");
        primeira.setPagamentoLink("http://localhost/pix");
        primeira.setPagamentoExpiraEm(LocalDateTime.now().plusMinutes(5));

        Agendamento segunda = new Agendamento();
        segunda.setId(71L);
        segunda.setProfissional(anaPaula);
        segunda.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        segunda.setPagamentoOrderNsu("mes-7-abc12345");
        segunda.setPagamentoLink("http://localhost/pix");
        segunda.setPagamentoExpiraEm(LocalDateTime.now().plusMinutes(5));

        when(repository.findAllByPagamentoOrderNsuOrderByDataHoraInicioAsc("mes-7-abc12345"))
                .thenReturn(java.util.List.of(primeira, segunda));
        when(repository.save(any(Agendamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        pagamentoConsultaService.confirmarPagamentoPorOrderNsu("mes-7-abc12345");

        assertEquals(PagamentoStatus.PAGO, primeira.getStatusPagamento());
        assertEquals(PagamentoStatus.PAGO, segunda.getStatusPagamento());
    }

    @Test
    void deveMigrarAgendamentosDeMensalParaDiario() {
        Usuario carol = new Usuario();
        carol.setId(5L);
        carol.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);

        LocalDate consulta = LocalDate.now().plusDays(4);
        Agendamento futuro = new Agendamento();
        futuro.setId(80L);
        futuro.setProfissional(carol);
        futuro.setDataHoraInicio(consulta.atTime(15, 0));
        futuro.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        futuro.setDataReferenciaMesPagamento(consulta.withDayOfMonth(1));

        when(authService.profissionalIgnoraValoresEPagamento(carol)).thenReturn(false);
        when(repository.findByProfissionalIdAndDataHoraInicioGreaterThanOrderByDataHoraInicioAsc(
                eq(5L), any(LocalDateTime.class)
        )).thenReturn(java.util.List.of(futuro));
        when(repository.save(futuro)).thenReturn(futuro);

        int migrados = pagamentoConsultaService.migrarAgendamentosAoAlterarPeriodicidade(
                carol,
                PeriodicidadePagamento.MENSAL,
                PeriodicidadePagamento.DIARIO
        );

        assertEquals(1, migrados);
        assertEquals(PagamentoStatus.PAGAMENTO_FUTURO, futuro.getStatusPagamento());
        assertEquals(null, futuro.getDataReferenciaMesPagamento());
        assertEquals(null, futuro.getDataReferenciaSemanaPagamento());
    }

    @Test
    void deveMigrarAgendamentosDeDiarioParaSemanalRecalculandoReferencia() {
        Usuario carol = new Usuario();
        carol.setId(5L);
        carol.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);

        LocalDate consulta = LocalDate.now().plusDays(6);
        Agendamento futuro = new Agendamento();
        futuro.setId(81L);
        futuro.setProfissional(carol);
        futuro.setDataHoraInicio(consulta.atTime(10, 0));
        futuro.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);

        when(authService.profissionalIgnoraValoresEPagamento(carol)).thenReturn(false);
        when(repository.findByProfissionalIdAndDataHoraInicioGreaterThanOrderByDataHoraInicioAsc(
                eq(5L), any(LocalDateTime.class)
        )).thenReturn(java.util.List.of(futuro));
        when(repository.save(futuro)).thenReturn(futuro);

        int migrados = pagamentoConsultaService.migrarAgendamentosAoAlterarPeriodicidade(
                carol,
                PeriodicidadePagamento.DIARIO,
                PeriodicidadePagamento.SEMANAL
        );

        assertEquals(1, migrados);
        assertEquals(PagamentoStatus.PAGAMENTO_FUTURO, futuro.getStatusPagamento());
        assertEquals(consulta, futuro.getDataReferenciaSemanaPagamento());
        assertEquals(null, futuro.getDataReferenciaMesPagamento());
    }

    @Test
    void deveMigrarAgendamentosDeMensalParaSemanalRecalculandoReferencia() {
        Usuario carol = new Usuario();
        carol.setId(5L);
        carol.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);

        LocalDate consulta = LocalDate.now().plusDays(8);
        Agendamento futuro = new Agendamento();
        futuro.setId(82L);
        futuro.setProfissional(carol);
        futuro.setDataHoraInicio(consulta.atTime(11, 0));
        futuro.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        futuro.setDataReferenciaMesPagamento(consulta.withDayOfMonth(1));

        when(authService.profissionalIgnoraValoresEPagamento(carol)).thenReturn(false);
        when(repository.findByProfissionalIdAndDataHoraInicioGreaterThanOrderByDataHoraInicioAsc(
                eq(5L), any(LocalDateTime.class)
        )).thenReturn(java.util.List.of(futuro));
        when(repository.save(futuro)).thenReturn(futuro);

        int migrados = pagamentoConsultaService.migrarAgendamentosAoAlterarPeriodicidade(
                carol,
                PeriodicidadePagamento.MENSAL,
                PeriodicidadePagamento.SEMANAL
        );

        assertEquals(1, migrados);
        assertEquals(PagamentoStatus.PAGAMENTO_FUTURO, futuro.getStatusPagamento());
        assertEquals(consulta, futuro.getDataReferenciaSemanaPagamento());
        assertEquals(null, futuro.getDataReferenciaMesPagamento());
    }

    @Test
    void polyanaVeRotuloSemanalDoProfissionalNaGrade() {
        Usuario anaPaula = new Usuario();
        anaPaula.setId(1L);
        anaPaula.setNome("Ana Paula");
        anaPaula.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);

        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);

        LocalDate referencia = LocalDate.of(2026, 6, 1);
        agendamento.setProfissional(anaPaula);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataHoraInicio(referencia.atTime(10, 0));
        agendamento.setDataReferenciaSemanaPagamento(referencia);

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);

        assertEquals(
                "Ana Paula: pagamento no dia 07/06",
                pagamentoConsultaService.rotuloEsperandoNaGrade(agendamento, polyana)
        );
    }

    @Test
    void notificacaoPagamentoDiarioLembraDataDoAgendamento() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);

        Agendamento amanha = new Agendamento();
        amanha.setId(1L);
        amanha.setProfissional(profissional);
        amanha.setDataHoraInicio(LocalDate.now().plusDays(1).atTime(9, 0));
        amanha.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);
        when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(10L))
                .thenReturn(java.util.List.of(amanha));

        var notificacao = pagamentoConsultaService.avaliarNotificacaoPagamentoProfissional(profissional);

        assertTrue(notificacao.isPresent());
        assertEquals("Pagamento do agendamento", notificacao.get().getMensagemResumo());
        assertTrue(notificacao.get().getMensagemPainel().contains("Não esqueça de pagar"));
        assertEquals(
                LocalDate.now().plusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                notificacao.get().getRotuloData()
        );
    }

    @Test
    void notificacaoPagamentoOcultaParaDonaClinica() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);

        assertTrue(pagamentoConsultaService.avaliarNotificacaoPagamentoProfissional(polyana).isEmpty());
    }

    @Test
    void notificacaoPagamentoMensalRespeitaJanelaDoMes() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(profissional)).thenReturn(false);

        if (pagamentoConsultaService.estaEmJanelaPagamentoMensal()) {
            Agendamento consulta = new Agendamento();
            consulta.setId(1L);
            consulta.setProfissional(profissional);
            consulta.setDataHoraInicio(YearMonth.now().minusMonths(1).atDay(5).atTime(10, 0));
            consulta.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
            when(repository.findByProfissionalIdOrderByDataHoraInicioAsc(10L))
                    .thenReturn(java.util.List.of(consulta));

            var notificacao = pagamentoConsultaService.avaliarNotificacaoPagamentoProfissional(profissional);

            assertTrue(notificacao.isPresent());
            assertEquals("Pagamento mensal", notificacao.get().getMensagemResumo());
            assertTrue(notificacao.get().getMensagemPainel().contains("até o dia 15"));
        } else {
            assertTrue(pagamentoConsultaService.avaliarNotificacaoPagamentoProfissional(profissional).isEmpty());
        }
    }

    private void comDataReferencia(LocalDate dia, LocalTime hora, Runnable teste) {
        LocalDateTime agora = LocalDateTime.of(dia, hora);
        try (MockedStatic<LocalDate> dataMock = Mockito.mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<LocalDateTime> dataHoraMock = Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS)) {
            dataMock.when(LocalDate::now).thenReturn(dia);
            dataHoraMock.when(LocalDateTime::now).thenReturn(agora);
            teste.run();
        }
    }

    private LocalDate proximoDiaAdiantavelNaSemanaAtual() {
        var periodo = pagamentoConsultaService.resolverPeriodoSemanaPagamento(LocalDate.now());
        for (LocalDate dia = LocalDate.now().plusDays(1); !dia.isAfter(periodo.fim()); dia = dia.plusDays(1)) {
            if (LocalDate.now().isBefore(dia.minusDays(1))) {
                return dia;
            }
        }
        LocalDate ultimoDiaSemana = periodo.fim();
        if (ultimoDiaSemana.isAfter(LocalDate.now()) && LocalDate.now().isBefore(ultimoDiaSemana.minusDays(1))) {
            return ultimoDiaSemana;
        }
        return LocalDate.now().plusDays(3);
    }
}
