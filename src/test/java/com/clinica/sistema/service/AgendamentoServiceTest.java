package com.clinica.sistema.service;

import com.clinica.sistema.dto.AcompanhamentoAgendaFiltros;
import com.clinica.sistema.dto.AgendaGradeCelula;
import com.clinica.sistema.dto.AgendaSalaLinha;
import com.clinica.sistema.dto.AgendaSalaView;
import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.MensalAgendamentoLinha;
import com.clinica.sistema.dto.SerieAgendamentoLinha;
import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.dto.RelocacaoAgendamentoForm;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.model.EncerramentoSerieRegistro;
import com.clinica.sistema.repository.EncerramentoSerieRegistroRepository;
import com.clinica.sistema.service.NovoAgendamentoNotificacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.clinica.sistema.model.PagamentoStatus;

@ExtendWith(MockitoExtension.class)
class AgendamentoServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SalaRepository salaRepository;

    @Mock
    private EncerramentoSerieRegistroRepository encerramentoSerieRegistroRepository;

    @Mock
    private NovoAgendamentoNotificacaoService novoAgendamentoNotificacaoService;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private AuthService authService;

    @Mock
    private PagamentoConsultaService pagamentoConsultaService;

    private ValorConsultaService valorConsultaService = criarValorConsultaService();

    @Spy
    private FeriadoBeloHorizonteService feriadoBeloHorizonteService = new FeriadoBeloHorizonteService();

    private static ValorConsultaService criarValorConsultaService() {
        SalaRepository salaRepository = org.mockito.Mockito.mock(SalaRepository.class);
        org.mockito.Mockito.when(salaRepository.findByNomeIgnoreCase("Sala 4"))
                .thenReturn(java.util.Optional.empty());
        return new ValorConsultaService(salaRepository);
    }

    private AgendamentoService agendamentoService;

    private Usuario profissional;
    private Sala sala;

    @BeforeEach
    void setUp() {
        agendamentoService = new AgendamentoService(
                agendamentoRepository,
                usuarioRepository,
                salaRepository,
                authService,
                valorConsultaService,
                pagamentoConsultaService,
                encerramentoSerieRegistroRepository,
                novoAgendamentoNotificacaoService,
                feriadoBeloHorizonteService,
                auditoriaService,
                4,
                14
        );

        profissional = new Usuario();
        profissional.setId(10L);
        profissional.setNome("Julia");
        profissional.setCargo("ROLE_PROFISSIONAL");

        sala = new Sala();
        sala.setId(3L);
        sala.setNome("Sala 3");

        lenient().when(pagamentoConsultaService.ocupaVagaNaGrade(any(Agendamento.class)))
                .thenAnswer(invocation -> {
                    Agendamento agendamento = invocation.getArgument(0);
                    return agendamento != null
                            && !PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(agendamento.getStatusPagamento());
                });
        lenient().when(agendamentoRepository.findFirstOcupacaoProfissionalAtivaNoHorarioExceto(
                anyLong(), any(LocalDateTime.class), any(LocalDateTime.class), anyLong()
        )).thenReturn(Optional.empty());
        lenient().when(agendamentoRepository.findCandidatosConflitoProfissionalNoHorario(
                anyLong(), any(LocalDateTime.class), any(LocalDateTime.class), anyLong(), any(LocalDateTime.class)
        )).thenReturn(List.of());
        lenient().when(agendamentoRepository.findCandidatosConflitoSalaNoHorario(
                anyLong(), any(LocalDateTime.class), any(LocalDateTime.class), anyLong(), any(LocalDateTime.class)
        )).thenReturn(List.of());
        lenient().when(pagamentoConsultaService.agendamentoOcupaHorarioParaNovaReserva(any(Agendamento.class)))
                .thenAnswer(invocation -> {
                    Agendamento agendamento = invocation.getArgument(0);
                    return agendamento != null
                            && !PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(agendamento.getStatusPagamento());
                });
    }

    @Test
    void deveSalvarAgendamentoValido() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(9, 0)));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        mockSalaLivre(sala.getId());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento agendamento = assertDoesNotThrow(() -> agendamentoService.salvar(form, profissional));

        assertEquals("Joao da Silva", agendamento.getNomeCliente());
        assertEquals(LocalDateTime.of(form.getDataAtendimento(), LocalTime.of(9, 0)), agendamento.getDataHoraInicio());
        assertEquals(LocalDateTime.of(form.getDataAtendimento(), LocalTime.of(10, 0)), agendamento.getDataHoraFim());
    }

    @Test
    void naoDevePermitirDomingo() {
        AgendamentoForm form = novoForm(proximoDomingo(LocalTime.of(9, 0)));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> agendamentoService.salvar(form, profissional));

        assertEquals("A clínica funciona somente de segunda a sábado.", exception.getMessage());
        verify(agendamentoRepository, never()).save(any(Agendamento.class));
    }

    @Test
    void devePermitirUltimoHorarioDas21Horas() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(21, 0)));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        mockSalaLivre(sala.getId());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento agendamento = assertDoesNotThrow(() -> agendamentoService.salvar(form, profissional));

        assertEquals(LocalDateTime.of(form.getDataAtendimento(), LocalTime.of(22, 0)), agendamento.getDataHoraFim());
    }

    @Test
    void deveCriarAgendamentoFixoParaAsProximasSemanas() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(9, 0)));
        form.setRecorrencia("SEMANAL");

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        mockSalaLivre(sala.getId());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento agendamento = assertDoesNotThrow(() -> agendamentoService.salvar(form, profissional));

        assertEquals(Boolean.TRUE, agendamento.getFixo());
        verify(agendamentoRepository, times(2)).saveAll(any());
        verify(agendamentoRepository, times(12)).findCandidatosConflitoSalaNoHorario(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class), eq(-1L), any(LocalDateTime.class)
        );
    }

    @Test
    void deveCriarAgendamentoQuinzenal() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(19, 0)));
        form.setRecorrencia("QUINZENAL");

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        mockSalaLivre(sala.getId());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento agendamento = assertDoesNotThrow(() -> agendamentoService.salvar(form, profissional));

        assertEquals("QUINZENAL", agendamento.getRecorrencia());
        assertEquals(Boolean.TRUE, agendamento.getFixo());
        verify(agendamentoRepository, times(6)).findCandidatosConflitoSalaNoHorario(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class), eq(-1L), any(LocalDateTime.class)
        );
    }

    @Test
    void deveCriarAgendamentoMensal() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(10, 0)));
        form.setRecorrencia("MENSAL");

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        mockSalaLivre(sala.getId());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento agendamento = assertDoesNotThrow(() -> agendamentoService.salvar(form, profissional));

        assertEquals("MENSAL", agendamento.getRecorrencia());
        assertEquals(Boolean.FALSE, agendamento.getFixo());
        assertEquals(null, agendamento.getSerieFixaId());
        verify(agendamentoRepository, times(1)).findCandidatosConflitoSalaNoHorario(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class), eq(-1L), any(LocalDateTime.class)
        );
    }

    @Test
    void devePrepararFormularioProximaConsultaMensal() {
        Agendamento origem = new Agendamento();
        origem.setId(99L);
        origem.setProfissional(profissional);
        origem.setSala(sala);
        origem.setNomeCliente("Maria");
        origem.setRecorrencia("MENSAL");
        origem.setDataHoraInicio(LocalDateTime.of(2027, 4, 15, 10, 0));
        origem.setDataHoraFim(origem.getDataHoraInicio().plusHours(1));
        origem.setValorProfissionalRecebe(new BigDecimal("150.00"));
        origem.setValorClinicaCobra(new BigDecimal("35.00"));

        when(agendamentoRepository.findById(99L)).thenReturn(Optional.of(origem));
        when(authService.isAdmin(profissional)).thenReturn(false);

        var preparacao = agendamentoService.prepararProximaConsultaMensal(99L, profissional);

        assertEquals("MENSAL", preparacao.form().getRecorrencia());
        assertEquals("Maria", preparacao.form().getNomeCliente());
        assertEquals(LocalDate.of(2027, 5, 15), preparacao.form().getDataAtendimento());
        assertEquals(LocalTime.of(10, 0), preparacao.form().getHorarioAtendimento());
        assertTrue(preparacao.mensagemInformativa().contains("Maria"));
        assertTrue(preparacao.form().isContinuacaoMensal());
        assertTrue(agendamentoService.podeMarcarProximaConsultaMensal(origem, profissional));
    }

    @Test
    void agruparMensaisDeveUnirDatasDoMesmoClienteNoMesmoCard() {
        Agendamento primeiro = agendamentoMensal("Maria", LocalDateTime.of(2026, 6, 4, 8, 0));
        Agendamento segundo = agendamentoMensal("Maria", LocalDateTime.of(2026, 7, 7, 8, 0));

        List<MensalAgendamentoLinha> linhas = agendamentoService.agruparMensaisAtivos(
                List.of(primeiro, segundo),
                profissional
        );

        assertEquals(1, linhas.size());
        assertEquals(2, linhas.get(0).getDatasHistorico().size());
        assertEquals("04/06", linhas.get(0).getDatasHistorico().get(0).getDataRotulo());
        assertEquals("07/07", linhas.get(0).getDatasHistorico().get(1).getDataRotulo());
        assertEquals(segundo.getId(), linhas.get(0).getAgendamentoReferenciaId());
    }

    @Test
    void agruparMensaisMantemPassadaRecenteERenovaQuatroFuturas() {
        LocalDateTime passado = LocalDateTime.now().minusDays(2).withHour(8).withMinute(0).withSecond(0).withNano(0);
        List<Agendamento> consultas = new ArrayList<>();
        consultas.add(agendamentoMensal("Maria", passado));
        for (int indice = 1; indice <= 6; indice++) {
            consultas.add(agendamentoMensal("Maria", passado.plusMonths(indice)));
        }

        List<MensalAgendamentoLinha> linhas = agendamentoService.agruparMensaisAtivos(consultas, profissional);

        assertEquals(1, linhas.size());
        assertEquals(5, linhas.get(0).getDatasHistorico().size());
        assertTrue(linhas.get(0).getDatasHistorico().get(0).isPassado());
        assertFalse(linhas.get(0).getDatasHistorico().get(4).isPassado());
    }

    private Agendamento agendamentoMensal(String cliente, LocalDateTime inicio) {
        Agendamento agendamento = new Agendamento();
        agendamento.setId((long) inicio.getMonthValue() + inicio.getDayOfMonth());
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setNomeCliente(cliente);
        agendamento.setRecorrencia("MENSAL");
        agendamento.setTipoRecorrencia("MENSAL");
        agendamento.setDataHoraInicio(inicio);
        agendamento.setDataHoraFim(inicio.plusHours(1));
        return agendamento;
    }

    @Test
    void continuacaoMensalDeveUsarRegraDePagamentoFuturo() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(11, 0)));
        form.setRecorrencia("MENSAL");
        form.setContinuacaoMensal(true);
        form.setAgendamentoOrigemId(10L);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        mockSalaLivre(sala.getId());
        lenient().when(agendamentoRepository.findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(Optional.empty());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agendamentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento origem = agendamentoMensal("Maria", LocalDateTime.of(2026, 6, 4, 8, 0));
        origem.setId(10L);
        origem.setHistoricoDatasMensal("04/06");
        when(agendamentoRepository.findById(10L)).thenReturn(Optional.of(origem));
        when(agendamentoRepository.findByProfissionalIdOrderByDataHoraInicioAsc(profissional.getId()))
                .thenReturn(List.of(origem));

        agendamentoService.salvar(form, profissional);

        verify(pagamentoConsultaService, never()).configurarPagamentosAoSalvar(any(), any(), any());
        verify(pagamentoConsultaService, times(1)).configurarPagamentoNovaOcorrenciaSerie(any(Agendamento.class));
    }

    @Test
    void renovacaoMensalComCincoConsultasDeveExigirPagamentoDaUltima() {
        Agendamento origem = agendamentoMensal("Maria", LocalDateTime.of(2026, 10, 4, 8, 0));
        origem.setId(60L);
        origem.setHistoricoDatasMensal("04/06|04/07|04/08|04/09|04/10");
        origem.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);

        assertTrue(agendamentoService.requerPagamentoUltimaConsultaParaRenovarMensal(origem));
        assertFalse(agendamentoService.podeMarcarProximaConsultaMensal(origem, profissional));

        origem.setStatusPagamento(PagamentoStatus.PAGO);
        assertFalse(agendamentoService.requerPagamentoUltimaConsultaParaRenovarMensal(origem));
        assertTrue(agendamentoService.podeMarcarProximaConsultaMensal(origem, profissional));
    }

    @Test
    void salvarContinuacaoMensalComCincoConsultasNaoPagasDeveFalhar() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(11, 0)));
        form.setRecorrencia("MENSAL");
        form.setContinuacaoMensal(true);
        form.setAgendamentoOrigemId(60L);

        Agendamento origem = agendamentoMensal("Maria", LocalDateTime.of(2026, 10, 4, 8, 0));
        origem.setId(60L);
        origem.setHistoricoDatasMensal("04/06|04/07|04/08|04/09|04/10");
        origem.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        when(agendamentoRepository.findById(60L)).thenReturn(Optional.of(origem));

        RuntimeException erro = assertThrows(RuntimeException.class, () -> agendamentoService.salvar(form, profissional));
        assertTrue(erro.getMessage().contains("pague a última"));
    }

    @Test
    void renovacaoMensalComCincoRegistrosSemHistoricoDeveExigirPagamentoDaUltima() {
        Agendamento origem = agendamentoMensal("Maria", LocalDateTime.of(2026, 10, 4, 8, 0));
        origem.setId(60L);
        origem.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        when(agendamentoRepository.countMensalByProfissionalIdAndNomeCliente(profissional.getId(), "Maria"))
                .thenReturn(5);

        assertTrue(agendamentoService.requerPagamentoUltimaConsultaParaRenovarMensal(origem));
        assertFalse(agendamentoService.podeMarcarProximaConsultaMensal(origem, profissional));
    }

    @Test
    void deveCancelarSerieMensalFuturaDoMesmoCliente() {
        Agendamento referencia = new Agendamento();
        referencia.setId(50L);
        referencia.setProfissional(profissional);
        referencia.setNomeCliente("Maria");
        referencia.setRecorrencia("MENSAL");
        referencia.setDataHoraInicio(LocalDateTime.now().plusDays(5).withHour(10).withMinute(0));

        Agendamento outra = new Agendamento();
        outra.setId(51L);
        outra.setProfissional(profissional);
        outra.setNomeCliente("Maria");
        outra.setRecorrencia("MENSAL");
        outra.setDataHoraInicio(LocalDateTime.now().plusDays(35).withHour(11).withMinute(0));

        when(agendamentoRepository.findById(50L)).thenReturn(Optional.of(referencia));
        when(agendamentoRepository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualOrderByDataHoraInicioAsc(
                eq(profissional.getId()), any(LocalDateTime.class)
        )).thenReturn(List.of(referencia, outra));
        when(authService.isAdmin(profissional)).thenReturn(false);

        int cancelados = agendamentoService.cancelarSerieMensal(50L, profissional);

        assertEquals(2, cancelados);
        verify(agendamentoRepository).deleteAll(List.of(referencia, outra));
    }

    @Test
    void naoDevePermitirProfissionalEmDuasSalasNoMesmoHorario() {
        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(7, 0)));
        form.setSalaId(2L);

        Sala sala1 = new Sala();
        sala1.setId(1L);
        sala1.setNome("Sala 1");

        Agendamento existente = new Agendamento();
        existente.setProfissional(profissional);
        existente.setSala(sala1);
        existente.setDataHoraInicio(LocalDateTime.of(form.getDataAtendimento(), LocalTime.of(7, 0)));
        existente.setDataHoraFim(existente.getDataHoraInicio().plusHours(1));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(2L)).thenReturn(Optional.of(sala));
        when(agendamentoRepository.findCandidatosConflitoProfissionalNoHorario(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class), eq(-1L), any(LocalDateTime.class)
        )).thenReturn(List.of(existente));
        when(pagamentoConsultaService.agendamentoOcupaHorarioParaNovaReserva(existente)).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> agendamentoService.salvar(form, profissional));

        assertTrue(exception.getMessage().contains("Sala 1"));
        assertTrue(exception.getMessage().contains("já tem consulta"));
        verify(agendamentoRepository, never()).save(any(Agendamento.class));
    }

    @Test
    void naoDevePermitirConflitoDeSala() {
        LocalDateTime inicio = proximaDataUtil(LocalTime.of(9, 0));
        AgendamentoForm form = novoForm(inicio);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        mockSalaOcupada(sala.getId(), inicio);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> agendamentoService.salvar(form, profissional));

        assertTrue(exception.getMessage().startsWith("Conflito de agenda: a Sala "));
        assertTrue(exception.getMessage().contains("Não é possível salvar este horário."));
        verify(agendamentoRepository, never()).save(any(Agendamento.class));
    }

    @Test
    void podeRealocarComPagamentoFuturoNoModoMensal() {
        profissional.setPeriodicidadePagamento(PeriodicidadePagamento.MENSAL);

        Agendamento agendamento = new Agendamento();
        agendamento.setId(2L);
        agendamento.setProfissional(profissional);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(2).atTime(10, 0));
        agendamento.setDataReferenciaMesPagamento(LocalDate.now().plusDays(2).withDayOfMonth(1));

        assertTrue(agendamentoService.podeRealocar(agendamento, profissional));
    }

    @Test
    void realocarPreservaReferenciaMesPagamento() {
        LocalDate mesOriginal = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        LocalDate dataOriginal = mesOriginal.plusDays(4);
        LocalDate novaData = mesOriginal.plusMonths(1).plusDays(2);

        Agendamento agendamento = new Agendamento();
        agendamento.setId(51L);
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setNomeCliente("Cliente teste");
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataReferenciaMesPagamento(mesOriginal);
        agendamento.setDataHoraInicio(dataOriginal.atTime(10, 0));
        agendamento.setDataHoraFim(agendamento.getDataHoraInicio().plusHours(1));

        Sala outraSala = new Sala();
        outraSala.setId(9L);
        outraSala.setNome("Sala 1");

        RelocacaoAgendamentoForm form = new RelocacaoAgendamentoForm();
        form.setSalaId(9L);
        form.setDataAtendimento(novaData);
        form.setHorarioAtendimento(LocalTime.of(14, 0));

        when(agendamentoRepository.findById(51L)).thenReturn(Optional.of(agendamento));
        when(salaRepository.findById(9L)).thenReturn(Optional.of(outraSala));
        when(agendamentoRepository.save(agendamento)).thenReturn(agendamento);

        Agendamento atualizado = agendamentoService.realocar(51L, form, profissional);

        assertEquals(mesOriginal, atualizado.getDataReferenciaMesPagamento());
        assertEquals(novaData, atualizado.getDataHoraInicio().toLocalDate());
    }

    @Test
    void podeRealocarComPagamentoFuturoNoModoDiario() {
        profissional.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);

        Agendamento agendamento = new Agendamento();
        agendamento.setId(3L);
        agendamento.setProfissional(profissional);
        agendamento.setFixo(true);
        agendamento.setTipoRecorrencia("SEMANAL");
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(14).atTime(10, 0));

        assertTrue(agendamentoService.podeRealocar(agendamento, profissional));
    }

    @Test
    void podeRealocarComPagamentoFuturoNoModoSemanal() {
        profissional.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);

        Agendamento agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setProfissional(profissional);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(2).atTime(10, 0));
        agendamento.setDataReferenciaSemanaPagamento(LocalDate.now().plusDays(2));

        assertTrue(agendamentoService.podeRealocar(agendamento, profissional));
    }

    @Test
    void podeRealocarSomenteAgendamentoPagoAntesDoHorario() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setProfissional(profissional);
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(2).atTime(10, 0));

        assertTrue(agendamentoService.podeRealocar(agendamento, profissional));

        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        assertFalse(agendamentoService.podeRealocar(agendamento, profissional));

        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        agendamento.setDataHoraInicio(LocalDateTime.now().minusHours(1));
        assertFalse(agendamentoService.podeRealocar(agendamento, profissional));
    }

    @Test
    void realocarPreservaReferenciaSemanaPagamento() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(50L);
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setNomeCliente("Cliente teste");
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        LocalDate inicioSemana = LocalDate.now().plusDays(5);
        agendamento.setDataReferenciaSemanaPagamento(inicioSemana.with(DayOfWeek.MONDAY));
        agendamento.setDataHoraInicio(inicioSemana.atTime(10, 0));
        agendamento.setDataHoraFim(agendamento.getDataHoraInicio().plusHours(1));

        Sala outraSala = new Sala();
        outraSala.setId(9L);
        outraSala.setNome("Sala 1");

        RelocacaoAgendamentoForm form = new RelocacaoAgendamentoForm();
        form.setSalaId(9L);
        form.setDataAtendimento(inicioSemana.plusDays(3));
        form.setHorarioAtendimento(LocalTime.of(14, 0));

        when(agendamentoRepository.findById(50L)).thenReturn(Optional.of(agendamento));
        when(salaRepository.findById(9L)).thenReturn(Optional.of(outraSala));
        when(agendamentoRepository.save(agendamento)).thenReturn(agendamento);

        Agendamento atualizado = agendamentoService.realocar(50L, form, profissional);

        assertEquals(inicioSemana.with(DayOfWeek.MONDAY), atualizado.getDataReferenciaSemanaPagamento());
        assertEquals(inicioSemana.plusDays(3), atualizado.getDataHoraInicio().toLocalDate());
    }

    @Test
    void realocarMudaSalaDataHorarioSemNovoPagamento() {
        LocalDateTime inicioSerie = proximaDataUtil(LocalTime.of(10, 0));
        Agendamento agendamento = new Agendamento();
        agendamento.setId(50L);
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setNomeCliente("Cliente teste");
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        agendamento.setDataHoraInicio(inicioSerie);
        agendamento.setDataHoraFim(inicioSerie.plusHours(1));
        agendamento.setFixo(true);
        agendamento.setSerieFixaId("semanal-test-1");
        agendamento.setTipoRecorrencia("SEMANAL");

        Sala outraSala = new Sala();
        outraSala.setId(9L);
        outraSala.setNome("Sala 1");

        LocalDate novaData = inicioSerie.toLocalDate().plusDays(1);
        if (novaData.getDayOfWeek() == DayOfWeek.SUNDAY) {
            novaData = novaData.plusDays(1);
        }

        RelocacaoAgendamentoForm form = new RelocacaoAgendamentoForm();
        form.setSalaId(9L);
        form.setDataAtendimento(novaData);
        form.setHorarioAtendimento(LocalTime.of(14, 0));

        when(agendamentoRepository.findById(50L)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.findBySerieFixaIdAndDataHoraInicioGreaterThanEqualOrderByDataHoraInicioAsc(
                eq("semanal-test-1"),
                eq(inicioSerie)
        )).thenReturn(List.of());
        when(salaRepository.findById(9L)).thenReturn(Optional.of(outraSala));
        when(agendamentoRepository.save(agendamento)).thenReturn(agendamento);

        Agendamento atualizado = agendamentoService.realocar(50L, form, profissional);

        assertEquals(PagamentoStatus.PAGO, atualizado.getStatusPagamento());
        assertEquals(9L, atualizado.getSala().getId());
        assertEquals(LocalTime.of(14, 0), atualizado.getDataHoraInicio().toLocalTime());
        assertEquals("semanal-test-1", atualizado.getSerieFixaId());
    }

    @Test
    void serieFixaSemanalPermiteOutroDiaDaSemanaAntesDaProximaOcorrencia() {
        LocalDateTime inicioSerie = proximaDataUtil(LocalTime.of(7, 0));
        Agendamento agendamento = new Agendamento();
        agendamento.setId(52L);
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        agendamento.setDataHoraInicio(inicioSerie);
        agendamento.setDataHoraFim(inicioSerie.plusHours(1));
        agendamento.setFixo(true);
        agendamento.setSerieFixaId("semanal-cadencia");
        agendamento.setTipoRecorrencia("SEMANAL");

        when(agendamentoRepository.findBySerieFixaIdAndDataHoraInicioGreaterThanEqualOrderByDataHoraInicioAsc(
                "semanal-cadencia",
                inicioSerie
        )).thenReturn(List.of());

        List<LocalDate> permitidas = agendamentoService.listarDatasPermitidasRealocacao(agendamento);
        assertFalse(permitidas.isEmpty());
        LocalDate outroDia = inicioSerie.toLocalDate().plusDays(1);
        while (outroDia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            outroDia = outroDia.plusDays(1);
        }
        assertTrue(permitidas.contains(outroDia));
        assertTrue(agendamentoService.dataPermitidaParaRealocacao(agendamento, outroDia));
        assertFalse(agendamentoService.dataPermitidaParaRealocacao(
                agendamento,
                inicioSerie.toLocalDate().plusWeeks(1)
        ));
    }

    @Test
    void realocarSerieFixaSemanalRejeitaDataDaProximaOcorrencia() {
        LocalDateTime inicioSerie = proximaDataUtil(LocalTime.of(7, 0));
        Agendamento agendamento = new Agendamento();
        agendamento.setId(53L);
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        agendamento.setDataHoraInicio(inicioSerie);
        agendamento.setDataHoraFim(inicioSerie.plusHours(1));
        agendamento.setFixo(true);
        agendamento.setSerieFixaId("semanal-cadencia-2");
        agendamento.setTipoRecorrencia("SEMANAL");

        RelocacaoAgendamentoForm form = new RelocacaoAgendamentoForm();
        form.setSalaId(sala.getId());
        form.setDataAtendimento(inicioSerie.plusWeeks(1).toLocalDate());
        form.setHorarioAtendimento(LocalTime.of(14, 0));

        when(agendamentoRepository.findById(53L)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.findBySerieFixaIdAndDataHoraInicioGreaterThanEqualOrderByDataHoraInicioAsc(
                "semanal-cadencia-2",
                inicioSerie
        )).thenReturn(List.of());
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> agendamentoService.realocar(53L, form, profissional)
        );
        assertTrue(ex.getMessage().contains("série fixa semanal"));
    }

    @Test
    void serieFixaQuinzenalPermiteOutroDiaAntesDaProximaOcorrencia() {
        LocalDateTime inicioSerie = proximaDataUtil(LocalTime.of(9, 0));
        Agendamento agendamento = new Agendamento();
        agendamento.setId(54L);
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        agendamento.setDataHoraInicio(inicioSerie);
        agendamento.setDataHoraFim(inicioSerie.plusHours(1));
        agendamento.setFixo(true);
        agendamento.setSerieFixaId("quinzenal-cadencia");
        agendamento.setTipoRecorrencia("QUINZENAL");

        when(agendamentoRepository.findBySerieFixaIdAndDataHoraInicioGreaterThanEqualOrderByDataHoraInicioAsc(
                "quinzenal-cadencia",
                inicioSerie
        )).thenReturn(List.of());

        List<LocalDate> permitidas = agendamentoService.listarDatasPermitidasRealocacao(agendamento);
        assertFalse(permitidas.isEmpty());
        LocalDate outroDia = inicioSerie.toLocalDate().plusDays(1);
        while (outroDia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            outroDia = outroDia.plusDays(1);
        }
        assertTrue(permitidas.contains(outroDia));
        assertFalse(agendamentoService.dataPermitidaParaRealocacao(
                agendamento,
                inicioSerie.toLocalDate().plusWeeks(2)
        ));
    }

    @Test
    void avulsoPermiteRealocarParaQualquerDataFutura() {
        LocalDateTime inicio = proximaDataUtil(LocalTime.of(9, 0));
        Agendamento agendamento = new Agendamento();
        agendamento.setId(55L);
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        agendamento.setDataHoraInicio(inicio);
        agendamento.setDataHoraFim(inicio.plusHours(1));
        agendamento.setFixo(false);
        agendamento.setTipoRecorrencia("AVULSO");

        assertTrue(agendamentoService.isRealocacaoAvulsa(agendamento));
        assertTrue(agendamentoService.podeRealocar(agendamento, profissional));
        assertTrue(agendamentoService.listarDatasPermitidasRealocacao(agendamento).isEmpty());
        LocalDate dataLivre = inicio.toLocalDate().plusMonths(1);
        assertTrue(agendamentoService.dataPermitidaParaRealocacao(agendamento, dataLivre));
    }

    @Test
    void realocarNaoPermiteDataNoPassado() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(51L);
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        agendamento.setDataHoraInicio(LocalDate.now().plusDays(1).atTime(10, 0));
        agendamento.setDataHoraFim(agendamento.getDataHoraInicio().plusHours(1));

        RelocacaoAgendamentoForm form = new RelocacaoAgendamentoForm();
        form.setSalaId(sala.getId());
        form.setDataAtendimento(LocalDate.now().minusDays(1));
        form.setHorarioAtendimento(LocalTime.of(15, 0));

        when(agendamentoRepository.findById(51L)).thenReturn(Optional.of(agendamento));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));

        RuntimeException erro = assertThrows(
                RuntimeException.class,
                () -> agendamentoService.realocar(51L, form, profissional)
        );
        assertTrue(erro.getMessage().contains("passado"));
    }

    @Test
    void profissionalPodeCancelarProprioAgendamentoComMaisDe24Horas() {
        Agendamento agendamento = agendamentoSerieSemanal(profissional);
        agendamento.setId(1L);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusHours(30));
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(pagamentoConsultaService.consultaJaFoiPaga(agendamento)).thenReturn(false);
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento));

        assertTrue(agendamentoService.podeCancelarAgendamento(agendamento, profissional));
        assertDoesNotThrow(() -> agendamentoService.cancelar(1L, profissional));
        verify(agendamentoRepository).deleteById(1L);
    }

    @Test
    void profissionalPodeCancelarAvulsoNaoPago() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setProfissional(profissional);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(3));
        agendamento.setTipoRecorrencia("AVULSO");
        agendamento.setFixo(false);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(pagamentoConsultaService.consultaJaFoiPaga(agendamento)).thenReturn(false);
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento));

        assertTrue(agendamentoService.podeCancelarAgendamento(agendamento, profissional));
        assertDoesNotThrow(() -> agendamentoService.cancelar(1L, profissional));
        verify(agendamentoRepository).deleteById(1L);
    }

    @Test
    void profissionalNaoPodeCancelarAvulsoJaPago() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setProfissional(profissional);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(3));
        agendamento.setTipoRecorrencia("AVULSO");
        agendamento.setFixo(false);
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(pagamentoConsultaService.consultaJaFoiPaga(agendamento)).thenReturn(true);
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento));

        assertFalse(agendamentoService.podeCancelarAgendamento(agendamento, profissional));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> agendamentoService.cancelar(1L, profissional));

        assertTrue(exception.getMessage().contains("já pago"));
        verify(agendamentoRepository, never()).deleteById(any());
    }

    @Test
    void profissionalNaoPodeCancelarAgendamentoJaPago() {
        Agendamento agendamento = agendamentoSerieSemanal(profissional);
        agendamento.setId(1L);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(3));
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(pagamentoConsultaService.consultaJaFoiPaga(agendamento)).thenReturn(true);
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento));

        assertFalse(agendamentoService.podeCancelarAgendamento(agendamento, profissional));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> agendamentoService.cancelar(1L, profissional));

        assertTrue(exception.getMessage().contains("já pago"));
        verify(agendamentoRepository, never()).deleteById(any());
    }

    @Test
    void profissionalNaoPodeCancelarAgendamentoComMenosDe24Horas() {
        Agendamento agendamento = agendamentoSerieSemanal(profissional);
        agendamento.setId(1L);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusHours(10));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(pagamentoConsultaService.consultaJaFoiPaga(agendamento)).thenReturn(false);
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento));

        assertFalse(agendamentoService.podeCancelarAgendamento(agendamento, profissional));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> agendamentoService.cancelar(1L, profissional));

        assertTrue(exception.getMessage().contains("24 horas"));
        verify(agendamentoRepository, never()).deleteById(any());
    }

    @Test
    void profissionalNaoPodeCancelarAgendamentoDeOutroProfissional() {
        Agendamento agendamento = agendamentoSerieSemanal(outroProfissional());
        agendamento.setId(1L);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(3));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento));

        assertFalse(agendamentoService.podeCancelarAgendamento(agendamento, profissional));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> agendamentoService.cancelar(1L, profissional));

        assertTrue(exception.getMessage().contains("próprios"));
        verify(agendamentoRepository, never()).deleteById(any());
    }

    private Usuario outroProfissional() {
        Usuario outro = new Usuario();
        outro.setId(99L);
        outro.setCargo("ROLE_PROFISSIONAL");
        return outro;
    }

    private Agendamento agendamentoSerieSemanal(Usuario profissionalDono) {
        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(profissionalDono);
        agendamento.setFixo(true);
        agendamento.setTipoRecorrencia("SEMANAL");
        agendamento.setSerieFixaId("serie-teste");
        return agendamento;
    }

    @Test
    void donaClinicaPodeCancelarDentroDasUltimas24Horas() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setLogin("polyana");
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(true);

        Agendamento agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setProfissional(polyana);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusHours(2));

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(agendamentoRepository.findById(1L)).thenReturn(Optional.of(agendamento));

        assertDoesNotThrow(() -> agendamentoService.cancelar(1L, polyana));
        verify(agendamentoRepository).deleteById(1L);
    }

    @Test
    void gestaoPodeCancelarERealocarOcorrenciaPassadaRecente() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);

        Usuario julia = new Usuario();
        julia.setId(2L);

        Agendamento passado = agendamentoSerieSemanal(julia);
        passado.setId(10L);
        passado.setDataHoraInicio(LocalDateTime.now().minusHours(4));
        passado.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(agendamentoRepository.findById(10L)).thenReturn(Optional.of(passado));

        assertTrue(agendamentoService.podeCancelarAgendamento(passado, polyana));
        assertTrue(agendamentoService.podeRealocar(passado, polyana));
        assertDoesNotThrow(() -> agendamentoService.cancelar(10L, polyana));
    }

    @Test
    void profissionalNaoPodeCancelarNemRealocarOcorrenciaPassada() {
        Agendamento passado = agendamentoSerieSemanal(profissional);
        passado.setId(11L);
        passado.setDataHoraInicio(LocalDateTime.now().minusHours(2));
        passado.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);

        assertFalse(agendamentoService.podeCancelarAgendamento(passado, profissional));
        assertFalse(agendamentoService.podeRealocar(passado, profissional));
    }

    @Test
    void serieFixaExibeOcorrenciasPassadasRecentesNaLista() {
        LocalDateTime passado = LocalDateTime.now().minusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime futuro = passado.plusWeeks(1);

        Agendamento ocorrenciaPassada = agendamentoSerie("semanal-janela-teste", passado);
        ocorrenciaPassada.setId(901L);
        ocorrenciaPassada.setProfissional(profissional);
        Agendamento ocorrenciaFutura = agendamentoSerie("semanal-janela-teste", futuro);
        ocorrenciaFutura.setId(902L);
        ocorrenciaFutura.setProfissional(profissional);

        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);
        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        lenient().when(pagamentoConsultaService.podeVerPagamento(any(), eq(polyana))).thenReturn(true);
        lenient().when(pagamentoConsultaService.exibirBotaoPagar(any())).thenReturn(false);

        List<SerieAgendamentoLinha> series = agendamentoService.agruparSeriesAtivas(
                List.of(ocorrenciaPassada, ocorrenciaFutura),
                Agendamento::isFixoSemanal,
                polyana
        );

        assertEquals(1, series.size());
        assertEquals(2, series.get(0).getProximasOcorrencias().size());
        assertTrue(series.get(0).getProximasOcorrencias().get(0).isPassado());
        assertFalse(series.get(0).getProximasOcorrencias().get(1).isPassado());
    }

    @Test
    void serieSemanalMantemPassadaRecenteERenovaOnzeFuturas() {
        LocalDateTime passado = LocalDateTime.now().minusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        List<Agendamento> agendamentos = new ArrayList<>();
        agendamentos.add(criarOcorrenciaSerieExibicao("semanal-renovacao-ui", passado, 901L));
        for (int i = 1; i <= 11; i++) {
            agendamentos.add(criarOcorrenciaSerieExibicao("semanal-renovacao-ui", passado.plusWeeks(i), 901L + i));
        }

        List<SerieAgendamentoLinha> series = agendamentoService.agruparSeriesAtivas(
                agendamentos,
                Agendamento::isFixoSemanal,
                profissional
        );

        assertEquals(1, series.size());
        assertEquals(12, series.get(0).getProximasOcorrencias().size());
        assertTrue(series.get(0).getProximasOcorrencias().get(0).isPassado());
        assertFalse(series.get(0).getProximasOcorrencias().get(11).isPassado());
    }

    @Test
    void serieQuinzenalMantemPassadaRecenteERenovaCincoFuturas() {
        LocalDateTime passado = LocalDateTime.now().minusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        List<Agendamento> agendamentos = new ArrayList<>();
        agendamentos.add(criarOcorrenciaQuinzenalExibicao("quinzenal-renovacao-ui", passado, 801L));
        for (int i = 1; i <= 5; i++) {
            agendamentos.add(criarOcorrenciaQuinzenalExibicao("quinzenal-renovacao-ui", passado.plusWeeks(2L * i), 801L + i));
        }

        List<SerieAgendamentoLinha> series = agendamentoService.agruparSeriesAtivas(
                agendamentos,
                Agendamento::isQuinzenal,
                profissional
        );

        assertEquals(1, series.size());
        assertEquals(6, series.get(0).getProximasOcorrencias().size());
        assertTrue(series.get(0).getProximasOcorrencias().get(0).isPassado());
        assertFalse(series.get(0).getProximasOcorrencias().get(5).isPassado());
    }

    private Agendamento criarOcorrenciaSerieExibicao(String serieFixaId, LocalDateTime inicio, long id) {
        Agendamento agendamento = agendamentoSerie(serieFixaId, inicio);
        agendamento.setId(id);
        agendamento.setProfissional(profissional);
        return agendamento;
    }

    private Agendamento criarOcorrenciaQuinzenalExibicao(String serieFixaId, LocalDateTime inicio, long id) {
        Agendamento agendamento = agendamentoSerie(serieFixaId, inicio);
        agendamento.setId(id);
        agendamento.setProfissional(profissional);
        return agendamento;
    }

    @Test
    void deveListarTodasOcorrenciasFuturasDaSerieFixaSemanal() {
        LocalDateTime primeiro = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime segundo = primeiro.plusWeeks(1);
        LocalDateTime terceiro = primeiro.plusWeeks(2);

        Agendamento ocorrencia1 = agendamentoSerie("semanal-serie-teste", primeiro);
        Agendamento ocorrencia2 = agendamentoSerie("semanal-serie-teste", segundo);
        Agendamento ocorrencia3 = agendamentoSerie("semanal-serie-teste", terceiro);

        List<Agendamento> lista = agendamentoService.listarProximasOcorrencias(
                List.of(ocorrencia1, ocorrencia2, ocorrencia3),
                Agendamento::isFixoSemanal
        );

        assertEquals(3, lista.size());
        assertEquals(primeiro, lista.get(0).getDataHoraInicio());
        assertEquals(terceiro, lista.get(2).getDataHoraInicio());
        assertEquals(3, agendamentoService.contarOcorrencias(List.of(ocorrencia1, ocorrencia2, ocorrencia3), Agendamento::isFixoSemanal));
    }

    @Test
    void devePreferirSalaComAgendamentosQuandoNenhumaSalaFoiInformada() {
        Sala sala1 = new Sala();
        sala1.setId(1L);
        sala1.setNome("Sala 1");

        Sala sala2 = new Sala();
        sala2.setId(2L);
        sala2.setNome("Sala 2");

        LocalDate sabado = LocalDate.of(2026, 5, 23);

        when(salaRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(sala1, sala2));

        Agendamento agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setSala(sala2);
        agendamento.setDataHoraInicio(LocalDateTime.of(2026, 5, 23, 17, 0));

        when(agendamentoRepository.contarAgendamentosPorSalaNoPeriodo(
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.<Object[]>of(new Object[]{2L, 1L}));

        Long salaResolvida = agendamentoService.resolverSalaIdParaGrade(null, sabado);

        assertEquals(2L, salaResolvida);
    }

    @Test
    void turnoLocacaoDeveMostrarValoresCompletosSomenteNaUltimaHoraDaGrade() {
        LocalDate dia = LocalDate.of(2026, 6, 6);
        LocalDate inicioSemana = dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        when(salaRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(sala));

        Agendamento turno = new Agendamento();
        turno.setId(200L);
        turno.setSala(sala);
        turno.setProfissional(profissional);
        turno.setNomeCliente("Cliente turno");
        turno.setTurnoLocacao("TURNO_MANHA");
        turno.setDataHoraInicio(LocalDateTime.of(dia, LocalTime.of(8, 0)));
        turno.setDataHoraFim(LocalDateTime.of(dia, LocalTime.of(13, 0)));

        when(agendamentoRepository.findBySalaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                eq(sala.getId()),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(turno));

        AgendaSalaView view = agendamentoService.montarAgendaSala(sala.getId(), dia);
        int indiceDia = view.getDiasSemana().indexOf(dia);

        AgendaSalaLinha linha8 = linhaPorHorario(view, LocalTime.of(8, 0));
        AgendaSalaLinha linha11 = linhaPorHorario(view, LocalTime.of(11, 0));
        AgendaSalaLinha linha12 = linhaPorHorario(view, LocalTime.of(12, 0));

        assertEquals(inicioSemana, view.getInicioSemana());
        assertFalse(linha8.getCelulas().get(indiceDia).isExibirDetalhesCompletos());
        assertFalse(linha11.getCelulas().get(indiceDia).isExibirDetalhesCompletos());
        assertTrue(linha12.getCelulas().get(indiceDia).isExibirDetalhesCompletos());
    }

    private AgendaSalaLinha linhaPorHorario(AgendaSalaView view, LocalTime horario) {
        return view.getLinhas().stream()
                .filter(linha -> linha.getHorario().equals(horario))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void deveExibirAgendamentoNaGradeMesmoComSegundosNoHorario() {
        LocalDate sabado = LocalDate.of(2026, 5, 23);
        LocalDate inicioSemana = sabado.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        when(salaRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(sala));

        Agendamento agendamento = new Agendamento();
        agendamento.setId(99L);
        agendamento.setSala(sala);
        agendamento.setProfissional(profissional);
        agendamento.setNomeCliente("gdsr");
        agendamento.setDataHoraInicio(LocalDateTime.of(2026, 5, 23, 17, 0, 45));
        agendamento.setFixo(true);

        when(agendamentoRepository.findBySalaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                eq(sala.getId()),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(agendamento));

        AgendaSalaView view = agendamentoService.montarAgendaSala(sala.getId(), sabado);

        int indiceSabado = view.getDiasSemana().indexOf(sabado);
        AgendaSalaLinha linha17 = view.getLinhas().stream()
                .filter(linha -> linha.getHorario().equals(LocalTime.of(17, 0)))
                .findFirst()
                .orElseThrow();

        assertEquals(inicioSemana, view.getInicioSemana());
        assertNotNull(linha17.getCelulas().get(indiceSabado));
        assertEquals("gdsr", linha17.getCelulas().get(indiceSabado).getAgendamento().getNomeCliente());
    }

    @Test
    void deveListarAgendamentosDoDiaDoProfissional() {
        LocalDateTime hoje = LocalDate.now().atTime(10, 0);
        Agendamento agendamento = new Agendamento();
        agendamento.setId(70L);
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setNomeCliente("Cliente do dia");
        agendamento.setDataHoraInicio(hoje);

        when(agendamentoRepository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                eq(profissional.getId()),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(agendamento));

        List<Agendamento> lista = agendamentoService.listarAgendamentosDoDia(profissional, false);

        assertEquals(1, lista.size());
        assertEquals("Cliente do dia", lista.get(0).getNomeCliente());
    }

    @Test
    void donaClinicaDeveVerSomenteAgendaDoDiaDelas() {
        Usuario polyana = new Usuario();
        polyana.setId(3L);
        polyana.setNome("Polyana");
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(true);

        when(agendamentoRepository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                eq(polyana.getId()),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of());

        List<Agendamento> lista = agendamentoService.listarAgendamentosDoDia(polyana, false);

        assertEquals(0, lista.size());
    }

    @Test
    void adminDeveVerAgendaDoDiaDeTodosProfissionais() {
        Usuario admin = new Usuario();
        admin.setId(1L);
        admin.setCargo("ROLE_ADMIN");

        Agendamento agendamento = new Agendamento();
        agendamento.setId(71L);
        agendamento.setNomeCliente("Cliente geral");

        when(agendamentoRepository.findByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(agendamento));

        List<Agendamento> lista = agendamentoService.listarAgendamentosDoDia(admin, true);

        assertEquals(1, lista.size());
    }

    @Test
    void adminPodeAbrirAcaoNaGradeDeAgendamentoDeOutroProfissional() {
        Usuario admin = new Usuario();
        admin.setId(1L);
        admin.setLogin("admin");
        admin.setCargo("ROLE_ADMIN");

        Usuario outro = new Usuario();
        outro.setId(20L);
        outro.setNome("Julia");
        outro.setCargo("ROLE_PROFISSIONAL");

        Agendamento agendamento = new Agendamento();
        agendamento.setId(50L);
        agendamento.setProfissional(outro);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(3));
        agendamento.setFixo(false);

        AgendaSalaView agenda = new AgendaSalaView();
        agenda.setLinhas(List.of(AgendaSalaLinha.comAgendamentoUnico(LocalTime.of(9, 0), agendamento)));

        when(authService.isAdmin(admin)).thenReturn(true);

        Map<Long, String> acoes = agendamentoService.montarAcoesGradePorId(agenda, admin);

        assertTrue(acoes.containsKey(50L));
        assertEquals("AVULSO", acoes.get(50L));
    }

    @Test
    void profissionalNaoPodeAbrirAcaoNaGradeDeOutroProfissional() {
        Usuario julia = new Usuario();
        julia.setId(20L);
        julia.setCargo("ROLE_PROFISSIONAL");

        Usuario maria = new Usuario();
        maria.setId(10L);
        maria.setNome("Maria");
        maria.setCargo("ROLE_PROFISSIONAL");

        Agendamento agendamento = new Agendamento();
        agendamento.setId(51L);
        agendamento.setProfissional(maria);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(3));

        AgendaSalaView agenda = new AgendaSalaView();
        agenda.setLinhas(List.of(AgendaSalaLinha.comAgendamentoUnico(LocalTime.of(10, 0), agendamento)));

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);

        Map<Long, String> acoes = agendamentoService.montarAcoesGradePorId(agenda, julia);

        assertFalse(acoes.containsKey(51L));
    }

    @Test
    void donaClinicaPodeAbrirAcaoNaGradeDeOutroProfissional() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setLogin("polyana");
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(true);

        Usuario julia = new Usuario();
        julia.setId(20L);
        julia.setNome("Julia");
        julia.setCargo("ROLE_PROFISSIONAL");

        Agendamento agendamento = new Agendamento();
        agendamento.setId(52L);
        agendamento.setProfissional(julia);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(2));

        AgendaSalaView agenda = new AgendaSalaView();
        agenda.setLinhas(List.of(AgendaSalaLinha.comAgendamentoUnico(LocalTime.of(11, 0), agendamento)));

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);

        Map<Long, String> acoes = agendamentoService.montarAcoesGradePorId(agenda, polyana);

        assertTrue(acoes.containsKey(52L));
    }

    @Test
    void donaClinicaPodeCancelarAgendamentoDeOutroProfissional() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setLogin("polyana");
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(true);

        Usuario julia = new Usuario();
        julia.setId(20L);
        julia.setCargo("ROLE_PROFISSIONAL");

        Agendamento agendamento = new Agendamento();
        agendamento.setId(53L);
        agendamento.setProfissional(julia);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(2));

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(agendamentoRepository.findById(53L)).thenReturn(Optional.of(agendamento));

        assertDoesNotThrow(() -> agendamentoService.cancelar(53L, polyana));
        verify(agendamentoRepository).deleteById(53L);
    }

    @Test
    void donaClinicaPodeAgendarParaOutroProfissional() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setLogin("polyana");
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(true);

        Usuario carol = new Usuario();
        carol.setId(20L);
        carol.setNome("Carol");
        carol.setCargo("ROLE_PROFISSIONAL");

        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(10, 0)));
        form.setProfissionalId(carol.getId());

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(authService.profissionalIgnoraValoresEPagamento(carol)).thenReturn(false);
        when(usuarioRepository.findById(carol.getId())).thenReturn(Optional.of(carol));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        mockSalaLivre(sala.getId());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Agendamento agendamento = assertDoesNotThrow(() -> agendamentoService.salvar(form, polyana));

        assertEquals(carol.getId(), agendamento.getProfissional().getId());
        verify(pagamentoConsultaService).configurarPagamentosAoSalvar(any(), eq(carol), eq(polyana));
    }

    @Test
    void profissionalNaoPodeAgendarParaOutroProfissional() {
        Usuario julia = new Usuario();
        julia.setId(30L);
        julia.setCargo("ROLE_PROFISSIONAL");

        Usuario carol = new Usuario();
        carol.setId(20L);
        carol.setCargo("ROLE_PROFISSIONAL");

        AgendamentoForm form = novoForm(proximaDataUtil(LocalTime.of(10, 0)));
        form.setProfissionalId(carol.getId());

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> agendamentoService.salvar(form, julia));

        assertEquals("Você só pode agendar para o seu próprio usuário.", exception.getMessage());
        verify(agendamentoRepository, never()).saveAll(any());
    }

    @Test
    void profissionalPodeEncerrarPropriaSerieFixa() {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(2L);
        agendamento.setProfissional(profissional);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(7));
        agendamento.setFixo(true);
        agendamento.setSerieFixaId("serie-1");
        agendamento.setTipoRecorrencia("SEMANAL");

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(agendamentoRepository.findById(2L)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.findBySerieFixaIdOrderByDataHoraInicioAsc("serie-1"))
                .thenReturn(Collections.singletonList(agendamento));

        assertDoesNotThrow(() -> agendamentoService.encerrarSerieFixa(2L, "Cliente desistiu", profissional));

        verify(agendamentoRepository).deleteAll(any());
    }

    @Test
    void profissionalNaoPodeEncerrarSerieDeOutro() {
        Usuario outroProfissional = new Usuario();
        outroProfissional.setId(20L);
        outroProfissional.setCargo("ROLE_PROFISSIONAL");

        Agendamento agendamento = new Agendamento();
        agendamento.setId(2L);
        agendamento.setProfissional(outroProfissional);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(7));
        agendamento.setFixo(true);
        agendamento.setSerieFixaId("serie-1");
        agendamento.setTipoRecorrencia("QUINZENAL");

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(authService.isDonaClinica(profissional)).thenReturn(false);
        when(agendamentoRepository.findById(2L)).thenReturn(Optional.of(agendamento));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> agendamentoService.encerrarSerieFixa(2L, "Cliente desistiu", profissional)
        );

        assertEquals(
                "Você só pode encerrar as suas próprias séries semanal ou quinzenal.",
                exception.getMessage()
        );
        verify(agendamentoRepository, never()).deleteAll(any());
    }

    @Test
    void donaClinicaPodeEncerrarSerieFixaFutura() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);

        Agendamento agendamento = new Agendamento();
        agendamento.setId(2L);
        agendamento.setProfissional(profissional);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(7));
        agendamento.setFixo(true);
        agendamento.setSerieFixaId("serie-1");
        agendamento.setTipoRecorrencia("SEMANAL");

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(agendamentoRepository.findById(2L)).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.findBySerieFixaIdOrderByDataHoraInicioAsc("serie-1"))
                .thenReturn(Collections.singletonList(agendamento));

        assertDoesNotThrow(() -> agendamentoService.encerrarSerieFixa(2L, "Cliente encerrou tratamento", polyana));

        verify(agendamentoRepository).deleteAll(any());
    }

    @Test
    void encerrarSerieFixaExigeMotivo() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);

        Agendamento agendamento = new Agendamento();
        agendamento.setId(2L);
        agendamento.setProfissional(profissional);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusDays(7));
        agendamento.setFixo(true);
        agendamento.setSerieFixaId("serie-1");
        agendamento.setTipoRecorrencia("QUINZENAL");

        when(agendamentoRepository.findById(2L)).thenReturn(Optional.of(agendamento));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> agendamentoService.encerrarSerieFixa(2L, "  ", polyana)
        );

        assertEquals("Informe o motivo do encerramento da série (mínimo 3 caracteres).", exception.getMessage());
        verify(agendamentoRepository, never()).deleteAll(any());
    }

    @Test
    void encerrarSerieFixaRemoveSerieCompleta() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);

        Agendamento futuro = new Agendamento();
        futuro.setId(3L);
        futuro.setProfissional(profissional);
        futuro.setDataHoraInicio(LocalDateTime.now().plusDays(14));
        futuro.setFixo(true);
        futuro.setSerieFixaId("serie-1");
        futuro.setTipoRecorrencia("SEMANAL");
        futuro.setNomeCliente("Maria Silva");

        Agendamento passado = new Agendamento();
        passado.setId(1L);
        passado.setProfissional(profissional);
        passado.setDataHoraInicio(LocalDateTime.now().minusDays(7));
        passado.setFixo(true);
        passado.setSerieFixaId("serie-1");
        passado.setTipoRecorrencia("SEMANAL");

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(agendamentoRepository.findById(3L)).thenReturn(Optional.of(futuro));
        when(agendamentoRepository.findBySerieFixaIdOrderByDataHoraInicioAsc("serie-1"))
                .thenReturn(List.of(passado, futuro));

        agendamentoService.encerrarSerieFixa(3L, "Cliente mudou de cidade", polyana);

        verify(encerramentoSerieRegistroRepository).save(any(EncerramentoSerieRegistro.class));
        verify(agendamentoRepository).deleteAll(List.of(passado, futuro));
        verify(agendamentoRepository, never()).save(any());
    }

    private Agendamento agendamentoSerie(String serieFixaId, LocalDateTime inicio) {
        Agendamento agendamento = new Agendamento();
        agendamento.setFixo(true);
        agendamento.setSerieFixaId(serieFixaId);
        agendamento.setTipoRecorrencia(serieFixaId.contains("quinzenal") ? "QUINZENAL" : "SEMANAL");
        agendamento.setDataHoraInicio(inicio);
        agendamento.setDataHoraFim(inicio.plusHours(1));
        return agendamento;
    }

    @Test
    void deveLimparAgendamentosSomenteDoMesPassado() {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);
        LocalDateTime inicio = mesPassado.atDay(1).atStartOfDay();
        LocalDateTime fim = YearMonth.now().atDay(1).atStartOfDay();

        when(agendamentoRepository.deleteAvulsosByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThan(inicio, fim))
                .thenReturn(42);

        long removidos = agendamentoService.limparAgendamentosDoMesPassado();

        assertEquals(42L, removidos);
        verify(agendamentoRepository).deleteAvulsosByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThan(inicio, fim);
    }

    @Test
    void deveMontarRelatorioMensalUsoSalasPorProfissional() {
        YearMonth maio = YearMonth.of(2026, 5);
        LocalDateTime inicio = maio.atDay(1).atStartOfDay();
        LocalDateTime fim = maio.plusMonths(1).atDay(1).atStartOfDay();

        when(agendamentoRepository.contarUsoSalasPorProfissionalNoPeriodo(inicio, fim))
                .thenReturn(List.of(
                        new Object[]{"Carol", "Sala 1", 10L},
                        new Object[]{"Julia", "Sala 1", 7L},
                        new Object[]{"Julia", "Sala 2", 9L}
                ));

        RelatorioMensalUsoSalasView relatorio = agendamentoService.montarRelatorioMensalUsoSalas(maio);

        assertEquals(26L, relatorio.getTotalGeral());
        assertEquals(2, relatorio.getProfissionais().size());
        assertEquals("Carol", relatorio.getProfissionais().get(0).getProfissionalNome());
        assertEquals(10L, relatorio.getProfissionais().get(0).getSalas().get(0).getQuantidade());
        assertEquals("Julia", relatorio.getProfissionais().get(1).getProfissionalNome());
        assertEquals(16L, relatorio.getProfissionais().get(1).getTotalHorarios());
        assertEquals(2, relatorio.getProfissionais().get(1).getSalas().size());
    }

    @Test
    void relatorioSemanalConsultaApenasHorariosAposRegra24h() {
        LocalDate inicio = LocalDate.of(2026, 5, 25);
        LocalDate fim = LocalDate.of(2026, 5, 28);
        LocalDateTime inicioDataHora = inicio.atStartOfDay();
        LocalDateTime fimDataHora = fim.plusDays(1).atStartOfDay();

        when(agendamentoRepository.contarUsoSalasPorProfissionalNoPeriodoAposRegra24h(
                eq(inicioDataHora),
                eq(fimDataHora),
                any(LocalDateTime.class)
        )).thenReturn(Collections.singletonList(new Object[]{"Julia", "Sala 1", 3L}));

        RelatorioMensalUsoSalasView relatorio = agendamentoService.montarRelatorioUsoSalasNoPeriodoAposRegra24h(
                inicio,
                fim,
                "Semana"
        );

        assertEquals(3L, relatorio.getTotalGeral());
        verify(agendamentoRepository).contarUsoSalasPorProfissionalNoPeriodoAposRegra24h(
                eq(inicioDataHora),
                eq(fimDataHora),
                any(LocalDateTime.class)
        );
        verify(agendamentoRepository, never()).contarUsoSalasPorProfissionalNoPeriodo(any(), any());
    }

    @Test
    void deveSepararSeriesSemanalEQuinzenal() {
        Agendamento semanal = agendamentoSerie("semanal-1", LocalDateTime.now().plusDays(3));
        Agendamento quinzenal = agendamentoSerie("quinzenal-1", LocalDateTime.now().plusDays(4));

        assertEquals(true, semanal.isFixoSemanal());
        assertEquals(false, semanal.isQuinzenal());
        assertEquals(false, quinzenal.isFixoSemanal());
        assertEquals(true, quinzenal.isQuinzenal());
    }

    @Test
    void deveEstenderSerieFixaAtivaAteHorizonteDeDozeSemanas() {
        String serieId = "semanal-renovacao";
        LocalDateTime ultimoHorario = LocalDateTime.now().plusWeeks(2);
        Agendamento ultimo = agendamentoSerie(serieId, ultimoHorario);
        ultimo.setNomeCliente("Cliente fixo");
        ultimo.setSala(sala);
        ultimo.setProfissional(profissional);

        when(agendamentoRepository.findSerieFixaIdsComOcorrenciasFuturas(any(LocalDateTime.class)))
                .thenReturn(List.of(serieId));
        when(agendamentoRepository.findFirstBySerieFixaIdOrderByDataHoraInicioDesc(serieId))
                .thenReturn(Optional.of(ultimo));
        when(agendamentoRepository.existsBySerieFixaIdAndDataHoraInicio(eq(serieId), any(LocalDateTime.class)))
                .thenReturn(false);
        mockSalaLivre(sala.getId());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> agendamentoService.renovarSeriesRecorrentesAtivas());

        verify(agendamentoRepository).saveAll(any());
    }

    @Test
    void deveAdicionarProximaDataQuandoSerieFixaFicaComMenosDeDozeFuturas() {
        String serieId = "semanal-rolagem";
        LocalDateTime ultimoHorario = LocalDateTime.now().plusWeeks(10);
        Agendamento ultimo = agendamentoSerie(serieId, ultimoHorario);
        ultimo.setId(50L);
        ultimo.setNomeCliente("Cliente");
        ultimo.setSala(sala);
        ultimo.setProfissional(profissional);
        List<Agendamento> onzeFuturas = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            Agendamento o = agendamentoSerie(serieId, LocalDateTime.now().plusWeeks(i + 1L));
            o.setId(500L + i);
            o.setNomeCliente("Cliente");
            o.setSala(sala);
            o.setProfissional(profissional);
            onzeFuturas.add(o);
        }

        when(agendamentoRepository.findSerieFixaIdsComOcorrenciasFuturas(any(LocalDateTime.class)))
                .thenReturn(List.of(serieId));
        when(agendamentoRepository.findBySerieFixaIdOrderByDataHoraInicioAsc(serieId))
                .thenReturn(onzeFuturas);
        when(agendamentoRepository.findFirstBySerieFixaIdOrderByDataHoraInicioDesc(serieId))
                .thenReturn(Optional.of(ultimo));
        when(agendamentoRepository.existsBySerieFixaIdAndDataHoraInicio(eq(serieId), any(LocalDateTime.class)))
                .thenReturn(false);
        mockSalaLivre(sala.getId());
        lenient().when(agendamentoRepository.findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(Optional.empty());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        agendamentoService.renovarSeriesRecorrentesAtivas();

        verify(agendamentoRepository).saveAll(any());
    }

    @Test
    void naoDeveRenovarSerieSemOcorrenciasFuturas() {
        when(agendamentoRepository.findSerieFixaIdsComOcorrenciasFuturas(any(LocalDateTime.class)))
                .thenReturn(List.of());

        agendamentoService.renovarSeriesRecorrentesAtivas();

        verify(agendamentoRepository, never()).saveAll(any());
    }

    @Test
    void deveAgruparSeriesFixasPorClienteESala() {
        Agendamento bernardo = agendamentoSerie("semanal-bernardo", LocalDateTime.now().plusDays(2));
        bernardo.setNomeCliente("Bernardo");
        bernardo.setSala(sala);

        Agendamento pedro = agendamentoSerie("semanal-pedro", LocalDateTime.now().plusDays(3));
        pedro.setNomeCliente("Pedro");
        Sala sala2 = new Sala();
        sala2.setId(2L);
        sala2.setNome("Sala 2");
        pedro.setSala(sala2);

        List<SerieAgendamentoLinha> linhas = agendamentoService.agruparSeriesAtivas(
                List.of(bernardo, pedro),
                Agendamento::isFixoSemanal
        );

        assertEquals(2, linhas.size());
        assertTrue(linhas.stream().anyMatch(l -> l.getRotuloCabecalho().contains("Bernardo - Sala 3")));
        assertTrue(linhas.stream().anyMatch(l -> l.getRotuloCabecalho().contains("Pedro - Sala 2")));
    }

    @Test
    void deveListarProximasDatasDaMesmaSerieFixa() {
        LocalDateTime primeiro = LocalDateTime.now().plusWeeks(1);
        LocalDateTime segundo = primeiro.plusWeeks(1);
        LocalDateTime terceiro = primeiro.plusWeeks(2);

        Agendamento ocorrencia1 = agendamentoSerie("semanal-bernardo", primeiro);
        ocorrencia1.setId(101L);
        ocorrencia1.setNomeCliente("Bernardo");
        ocorrencia1.setSala(sala);
        Agendamento ocorrencia2 = agendamentoSerie("semanal-bernardo", segundo);
        ocorrencia2.setId(102L);
        ocorrencia2.setNomeCliente("Bernardo");
        ocorrencia2.setSala(sala);
        Agendamento ocorrencia3 = agendamentoSerie("semanal-bernardo", terceiro);
        ocorrencia3.setId(103L);
        ocorrencia3.setNomeCliente("Bernardo");
        ocorrencia3.setSala(sala);

        List<SerieAgendamentoLinha> linhas = agendamentoService.agruparSeriesAtivas(
                List.of(ocorrencia1, ocorrencia2, ocorrencia3),
                Agendamento::isFixoSemanal
        );

        assertEquals(1, linhas.size());
        assertEquals(3, linhas.get(0).getProximasOcorrencias().size());
        assertEquals(
                primeiro.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")),
                linhas.get(0).getProximasOcorrencias().get(0).getDataRotulo()
        );
        assertEquals(101L, linhas.get(0).getProximasOcorrencias().get(0).getAgendamentoId());
    }

    @Test
    void cardSerieExibeTaxaClinicaDaPrimeiraConsultaPendente() {
        LocalDateTime pago = LocalDateTime.now().plusDays(2);
        LocalDateTime pendente = pago.plusWeeks(1);
        String serieId = "semanal-taxa-card";

        Agendamento ocorrenciaPaga = agendamentoSerie(serieId, pago);
        ocorrenciaPaga.setId(201L);
        ocorrenciaPaga.setNomeCliente("Cliente");
        ocorrenciaPaga.setSala(sala);
        ocorrenciaPaga.setStatusPagamento(PagamentoStatus.PAGO);
        ocorrenciaPaga.setValorProfissionalRecebe(new BigDecimal("170.00"));
        ocorrenciaPaga.setValorClinicaCobra(new BigDecimal("35.00"));
        ocorrenciaPaga.setValorLiquidoProfissional(new BigDecimal("135.00"));

        Agendamento ocorrenciaPendente = agendamentoSerie(serieId, pendente);
        ocorrenciaPendente.setId(202L);
        ocorrenciaPendente.setNomeCliente("Cliente");
        ocorrenciaPendente.setSala(sala);
        ocorrenciaPendente.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        ocorrenciaPendente.setValorProfissionalRecebe(new BigDecimal("170.00"));
        ocorrenciaPendente.setValorClinicaCobra(new BigDecimal("50.00"));
        ocorrenciaPendente.setValorLiquidoProfissional(new BigDecimal("120.00"));

        List<SerieAgendamentoLinha> linhas = agendamentoService.agruparSeriesAtivas(
                List.of(ocorrenciaPaga, ocorrenciaPendente),
                Agendamento::isFixoSemanal
        );

        assertEquals(1, linhas.size());
        String resumo = linhas.get(0).getValoresConsultaResumo();
        assertNotNull(resumo);
        assertTrue(resumo.contains("50,00"), () -> "Resumo atual: " + resumo);
        assertTrue(resumo.contains("120,00"), () -> "Resumo atual: " + resumo);
        assertFalse(resumo.contains("135,00"), () -> "Nao deve usar liquido da consulta paga: " + resumo);
    }

    @Test
    void deveLimitarQuinzenalASeisDatasMesmoSemTipoRecorrenciaNoBanco() {
        LocalDateTime primeiro = LocalDateTime.now().plusWeeks(1);
        List<Agendamento> ocorrencias = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Agendamento o = agendamentoSerie("quinzenal-rolagem", primeiro.plusWeeks(2L * i));
            o.setTipoRecorrencia(null);
            o.setId(300L + i);
            o.setNomeCliente("Cliente quinzenal");
            o.setSala(sala);
            ocorrencias.add(o);
        }

        List<SerieAgendamentoLinha> linhas = agendamentoService.agruparSeriesAtivas(
                ocorrencias,
                Agendamento::isQuinzenal
        );

        assertEquals(1, linhas.size());
        assertEquals(6, linhas.get(0).getProximasOcorrencias().size());
    }

    @Test
    void deveLimitarQuinzenalASeisDatasNaListagem() {
        LocalDateTime primeiro = LocalDateTime.now().plusWeeks(1);
        List<Agendamento> ocorrencias = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Agendamento o = agendamentoSerie("quinzenal-rolagem", primeiro.plusWeeks(2L * i));
            o.setId(200L + i);
            o.setNomeCliente("Cliente quinzenal");
            o.setSala(sala);
            ocorrencias.add(o);
        }

        List<SerieAgendamentoLinha> linhas = agendamentoService.agruparSeriesAtivas(
                ocorrencias,
                Agendamento::isQuinzenal
        );

        assertEquals(1, linhas.size());
        assertEquals(6, linhas.get(0).getProximasOcorrencias().size());
    }

    @Test
    void deveAdicionarProximaDataQuinzenalQuandoFicaComMenosDeSeisFuturas() {
        String serieId = "quinzenal-renovacao";
        LocalDateTime ultimoHorario = LocalDateTime.now().plusWeeks(8);
        Agendamento ultimo = agendamentoSerie(serieId, ultimoHorario);
        ultimo.setId(60L);
        ultimo.setNomeCliente("Cliente");
        ultimo.setSala(sala);
        ultimo.setProfissional(profissional);
        List<Agendamento> cincoFuturas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Agendamento o = agendamentoSerie(serieId, LocalDateTime.now().plusWeeks(2L * (i + 1)));
            o.setId(600L + i);
            o.setNomeCliente("Cliente");
            o.setSala(sala);
            o.setProfissional(profissional);
            cincoFuturas.add(o);
        }

        when(agendamentoRepository.findSerieFixaIdsComOcorrenciasFuturas(any(LocalDateTime.class)))
                .thenReturn(List.of(serieId));
        when(agendamentoRepository.findBySerieFixaIdOrderByDataHoraInicioAsc(serieId))
                .thenReturn(cincoFuturas);
        when(agendamentoRepository.findFirstBySerieFixaIdOrderByDataHoraInicioDesc(serieId))
                .thenReturn(Optional.of(ultimo));
        when(agendamentoRepository.existsBySerieFixaIdAndDataHoraInicio(eq(serieId), any(LocalDateTime.class)))
                .thenReturn(false);
        mockSalaLivre(sala.getId());
        lenient().when(agendamentoRepository.findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class))
        ).thenReturn(Optional.empty());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        agendamentoService.renovarSeriesRecorrentesAtivas();

        verify(agendamentoRepository).saveAll(any());
    }

    @Test
    void naoDeveAdicionarQuinzenalQuandoJaTemSeisFuturas() {
        String serieId = "quinzenal-cheio";
        LocalDateTime ultimoHorario = LocalDateTime.now().plusWeeks(10);
        Agendamento ultimo = agendamentoSerie(serieId, ultimoHorario);
        ultimo.setSala(sala);
        ultimo.setProfissional(profissional);
        List<Agendamento> seisFuturas = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Agendamento o = agendamentoSerie(serieId, LocalDateTime.now().plusWeeks(2L * (i + 1)));
            o.setId(700L + i);
            o.setSala(sala);
            o.setProfissional(profissional);
            seisFuturas.add(o);
        }

        when(agendamentoRepository.findSerieFixaIdsComOcorrenciasFuturas(any(LocalDateTime.class)))
                .thenReturn(List.of(serieId));
        when(agendamentoRepository.findBySerieFixaIdOrderByDataHoraInicioAsc(serieId))
                .thenReturn(seisFuturas);
        when(agendamentoRepository.findFirstBySerieFixaIdOrderByDataHoraInicioDesc(serieId))
                .thenReturn(Optional.of(ultimo));

        agendamentoService.renovarSeriesRecorrentesAtivas();

        verify(agendamentoRepository, never()).saveAll(any());
    }

    @Test
    void polyanaDeveVerValoresDeQualquerProfissional() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);

        Usuario julia = new Usuario();
        julia.setId(20L);

        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(julia);
        agendamento.setValorProfissionalRecebe(new BigDecimal("150.00"));
        agendamento.setValorClinicaCobra(new BigDecimal("35.00"));

        when(authService.isAdmin(polyana)).thenReturn(false);
        when(authService.isDonaClinica(polyana)).thenReturn(true);
        when(authService.profissionalIgnoraValoresEPagamento(julia)).thenReturn(false);

        assertTrue(agendamentoService.podeVerValoresConsulta(agendamento, polyana));
    }

    @Test
    void polyanaNaoVeValoresDosPropriosAgendamentos() {
        Usuario polyana = new Usuario();
        polyana.setId(99L);
        polyana.setDonaClinica(true);

        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(polyana);
        agendamento.setValorProfissionalRecebe(new BigDecimal("150.00"));
        agendamento.setValorClinicaCobra(new BigDecimal("35.00"));

        when(authService.profissionalIgnoraValoresEPagamento(polyana)).thenReturn(true);

        assertFalse(agendamentoService.podeVerValoresConsulta(agendamento, polyana));
    }

    @Test
    void juliaSoVeValoresDosPropriosAgendamentos() {
        Usuario julia = new Usuario();
        julia.setId(20L);

        Usuario carol = new Usuario();
        carol.setId(21L);

        Agendamento proprio = new Agendamento();
        proprio.setProfissional(julia);
        proprio.setValorProfissionalRecebe(new BigDecimal("150.00"));

        Agendamento deOutra = new Agendamento();
        deOutra.setProfissional(carol);
        deOutra.setValorProfissionalRecebe(new BigDecimal("200.00"));

        when(authService.isAdmin(julia)).thenReturn(false);
        when(authService.isDonaClinica(julia)).thenReturn(false);

        assertTrue(agendamentoService.podeVerValoresConsulta(proprio, julia));
        assertFalse(agendamentoService.podeVerValoresConsulta(deOutra, julia));
    }

    private AgendamentoForm novoForm(LocalDateTime dataHoraInicio) {
        AgendamentoForm form = new AgendamentoForm();
        form.setProfissionalId(profissional.getId());
        form.setSalaId(sala.getId());
        form.setNomeCliente("Joao da Silva");
        form.setDataAtendimento(dataHoraInicio.toLocalDate());
        form.setHorarioAtendimento(dataHoraInicio.toLocalTime());
        form.setValorProfissionalRecebe(new BigDecimal("150.00"));
        form.setValorClinicaCobra(new BigDecimal("35.00"));
        return form;
    }

    private void mockSalaLivre(Long salaId) {
        when(agendamentoRepository.findCandidatosConflitoSalaNoHorario(
                eq(salaId),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(-1L),
                any(LocalDateTime.class)
        )).thenReturn(List.of());
    }

    @Test
    void serieSemanalDeveIgnorarHorarioLiberadoNaSegundaSemana() {
        LocalDateTime inicioSerie = proximaDataUtil(LocalTime.of(9, 0));
        AgendamentoForm form = novoForm(inicioSerie);
        form.setRecorrencia("SEMANAL");

        Agendamento liberado = new Agendamento();
        liberado.setId(99L);
        liberado.setSala(sala);
        liberado.setProfissional(profissional);
        liberado.setStatusPagamento(PagamentoStatus.LIBERADO_FALTA_PAGAMENTO);
        liberado.setDataHoraInicio(inicioSerie.plusWeeks(1));
        liberado.setDataHoraFim(inicioSerie.plusWeeks(1).plusHours(1));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        mockSalaLivre(sala.getId());
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> agendamentoService.salvar(form, profissional));
        verify(agendamentoRepository, times(12)).findCandidatosConflitoProfissionalNoHorario(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class), eq(-1L), any(LocalDateTime.class)
        );
    }

    @Test
    void serieSemanalNaoDeveConflitarComQrExpiradoNaSegundaSemana() {
        LocalDateTime inicioSerie = proximaDataUtil(LocalTime.of(9, 0));
        AgendamentoForm form = novoForm(inicioSerie);
        form.setRecorrencia("SEMANAL");

        Agendamento qrExpirado = new Agendamento();
        qrExpirado.setId(88L);
        qrExpirado.setSala(sala);
        qrExpirado.setProfissional(profissional);
        qrExpirado.setNomeCliente("Cliente antigo");
        qrExpirado.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        qrExpirado.setDataHoraInicio(inicioSerie.plusWeeks(1));
        qrExpirado.setDataHoraFim(inicioSerie.plusWeeks(1).plusHours(1));
        qrExpirado.setPagamentoExpiraEm(LocalDateTime.now().minusMinutes(30));

        when(authService.isAdmin(profissional)).thenReturn(false);
        when(usuarioRepository.findById(profissional.getId())).thenReturn(Optional.of(profissional));
        when(salaRepository.findById(sala.getId())).thenReturn(Optional.of(sala));
        mockSalaLivre(sala.getId());
        when(agendamentoRepository.findCandidatosConflitoProfissionalNoHorario(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class), eq(-1L), any(LocalDateTime.class)
        )).thenReturn(List.of(qrExpirado));
        when(pagamentoConsultaService.agendamentoOcupaHorarioParaNovaReserva(qrExpirado)).thenReturn(false);
        when(agendamentoRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> agendamentoService.salvar(form, profissional));
    }

    private void mockSalaOcupada(Long salaId, LocalDateTime inicioOcupacao) {
        Agendamento ocupante = new Agendamento();
        ocupante.setNomeCliente("Cliente existente");
        ocupante.setDataHoraInicio(inicioOcupacao);
        ocupante.setDataHoraFim(inicioOcupacao.plusHours(1));
        when(agendamentoRepository.findCandidatosConflitoSalaNoHorario(
                eq(salaId),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(-1L),
                any(LocalDateTime.class)
        )).thenReturn(List.of(ocupante));
        when(pagamentoConsultaService.agendamentoOcupaHorarioParaNovaReserva(ocupante)).thenReturn(true);
    }

    @Test
    void gradeDeveMostrarProfissionalOcupadoEmOutraSala() {
        LocalDate terca = LocalDate.of(2026, 6, 16);
        LocalDate inicioSemana = terca.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        Sala sala1 = new Sala();
        sala1.setId(1L);
        sala1.setNome("Sala 1");

        Agendamento naSala1 = new Agendamento();
        naSala1.setId(50L);
        naSala1.setProfissional(profissional);
        naSala1.setSala(sala1);
        naSala1.setNomeCliente("Cliente Sala 1");
        naSala1.setDataHoraInicio(LocalDateTime.of(terca, LocalTime.of(9, 0)));
        naSala1.setDataHoraFim(LocalDateTime.of(terca, LocalTime.of(10, 0)));
        naSala1.setStatusPagamento(PagamentoStatus.PAGO);

        when(salaRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(sala, sala1));
        when(agendamentoRepository.findBySalaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                eq(sala.getId()), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of());
        when(agendamentoRepository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                eq(profissional.getId()), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(naSala1));
        when(pagamentoConsultaService.agendamentoOcupaHorarioParaNovaReserva(naSala1)).thenReturn(true);

        AgendaSalaView view = agendamentoService.montarAgendaSala(sala.getId(), terca, profissional.getId());

        int indiceTerca = view.getDiasSemana().indexOf(terca);
        AgendaSalaLinha linha9 = view.getLinhas().stream()
                .filter(l -> l.getHorario().equals(LocalTime.of(9, 0)))
                .findFirst()
                .orElseThrow();
        AgendaGradeCelula celula = linha9.getCelulas().get(indiceTerca);

        assertEquals(inicioSemana, view.getInicioSemana());
        assertNotNull(celula);
        assertFalse(celula.isOcupada());
        assertFalse(celula.isDisponivelParaNovaReserva());
        assertTrue(celula.getAvisoProfissionalOcupadoOutraSala().contains("Sala 1"));
    }

    @Test
    void listarAgendamentosAcompanhamento_filtraPorPeriodoEFormaPagamento() {
        Usuario dona = new Usuario();
        dona.setId(1L);
        dona.setDonaClinica(true);

        Usuario maria = new Usuario();
        maria.setId(2L);
        maria.setPeriodicidadePagamento(PeriodicidadePagamento.SEMANAL);

        LocalDate hoje = LocalDate.now();
        Agendamento deMaria = new Agendamento();
        deMaria.setId(10L);
        deMaria.setProfissional(maria);
        deMaria.setNomeCliente("Cliente A");
        deMaria.setDataHoraInicio(hoje.atTime(10, 0));
        deMaria.setDataHoraFim(hoje.atTime(11, 0));

        when(agendamentoRepository.findByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(deMaria));

        var intervalo = AcompanhamentoAgendaFiltros.resolverIntervalo(
                AcompanhamentoAgendaFiltros.Periodo.HOJE,
                hoje
        );
        List<Agendamento> todos = agendamentoService.listarAgendamentosAcompanhamento(
                new AcompanhamentoAgendaFiltros.FiltroProfissional(null),
                intervalo,
                AcompanhamentoAgendaFiltros.RecorrenciaConsulta.TODOS
        );

        assertEquals(1, todos.size());
    }

    private LocalDateTime proximaDataUtil(LocalTime horario) {
        LocalDate data = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return LocalDateTime.of(data, horario);
    }

    private LocalDateTime proximoDomingo(LocalTime horario) {
        LocalDate data = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        return LocalDateTime.of(data, horario);
    }
}
