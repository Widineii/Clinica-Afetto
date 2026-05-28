package com.clinica.sistema.service;

import com.clinica.sistema.config.InfinitePayProperties;
import com.clinica.sistema.config.PagamentoProperties;
import com.clinica.sistema.exception.PagamentoWebhookNaoAutorizadoException;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setValorClinicaCobra(new BigDecimal("32.00"));
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(3).atTime(10, 0));
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

        pagamentoConsultaService.configurarPagamentosAoSalvar(java.util.List.of(agendamento), profissional);

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

        pagamentoConsultaService.configurarPagamentosAoSalvar(java.util.List.of(agendamento, segunda), profissional);

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
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setDonaClinica(false);

        var periodo = pagamentoConsultaService.resolverPeriodoSemanaPagamento(LocalDate.now());
        LocalDate diaFuturo = periodo.inicio().plusDays(2);
        if (!diaFuturo.isAfter(LocalDate.now())) {
            diaFuturo = LocalDate.now().plusDays(2);
        }

        Agendamento paga = new Agendamento();
        paga.setId(1L);
        paga.setProfissional(profissional);
        paga.setDataHoraInicio(diaFuturo.atTime(10, 0));
        paga.setStatusPagamento(PagamentoStatus.PAGO);

        Agendamento futura = new Agendamento();
        futura.setId(2L);
        futura.setProfissional(profissional);
        futura.setDataHoraInicio(diaFuturo.plusDays(1).atTime(11, 0));
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
    }

    @Test
    void listarConsultasAdiantamentoSemanaIgnoraPendenciasObrigatorias() {
        Usuario profissional = new Usuario();
        profissional.setId(10L);
        profissional.setDonaClinica(false);

        Agendamento futuraSemana = new Agendamento();
        futuraSemana.setId(1L);
        futuraSemana.setProfissional(profissional);
        futuraSemana.setDataHoraInicio(LocalDate.now().plusDays(2).atTime(10, 0));
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

        pagamentoConsultaService.configurarPagamentosAoSalvar(java.util.List.of(agendamento), polyana);

        assertEquals(PagamentoStatus.PAGO, agendamento.getStatusPagamento());
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
    void webhookRejeitaSemSegredoQuandoProducao() {
        when(pagamentoProperties.getWebhookSecret()).thenReturn("segredo-prod");

        assertThrows(
                PagamentoWebhookNaoAutorizadoException.class,
                () -> pagamentoConsultaService.validarAutenticacaoWebhook(null)
        );
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

        assertEquals("Aguardando confirmacoes",
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
    void rotuloPendenteNaGradeParaPagamentoFuturoDeOutroProfissional() {
        Usuario julia = new Usuario();
        julia.setId(1L);
        Usuario carol = new Usuario();
        carol.setId(2L);

        agendamento.setProfissional(julia);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(10).atTime(10, 0));

        when(authService.isAdmin(carol)).thenReturn(false);
        when(authService.isDonaClinica(carol)).thenReturn(false);

        assertTrue(agendamento.isReservaPendenteNaGrade());
        assertEquals("Aguardando confirmacoes",
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
        assertEquals("Sistema do usuario Julia foi bloqueado por nao pagar.", bloqueados.get(0).getMensagemBloqueio());
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

        Agendamento pendente = new Agendamento();
        pendente.setId(11L);
        pendente.setProfissional(julia);
        LocalDate dataPendente = LocalDate.now().plusDays(1);
        pendente.setDataHoraInicio(dataPendente.atTime(9, 0));
        pendente.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(julia)).thenReturn(false);
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
}
