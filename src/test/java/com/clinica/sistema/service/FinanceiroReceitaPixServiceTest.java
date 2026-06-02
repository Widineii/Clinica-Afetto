package com.clinica.sistema.service;

import com.clinica.sistema.dto.ProfissionalReceitaPainelView;
import com.clinica.sistema.dto.ReceitaPixMesView;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceiroReceitaPixServiceTest {

    @Mock
    private AgendamentoRepository repository;

    @Mock
    private InfinitePayService infinitePayService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SalaRepository salaRepository;

    @Mock
    private AuthService authService;

    private FinanceiroReceitaPixService service;

    @BeforeEach
    void setUp() {
        service = new FinanceiroReceitaPixService(
                repository,
                usuarioRepository,
                salaRepository,
                infinitePayService,
                authService
        );
        when(salaRepository.findAllByOrderByNomeAsc()).thenReturn(List.of(sala("Sala 1"), sala("Sala 2")));
        when(usuarioRepository.findByCargoOrderByNomeAsc("ROLE_PROFISSIONAL")).thenReturn(List.of());
    }

    @Test
    void naoContaConsultasDaDonaClinicaNaReceita() {
        Agendamento daJulia = agendamentoPago(LocalDateTime.of(2026, 5, 10, 14, 0));
        Agendamento daPolyana = agendamentoPago(LocalDateTime.of(2026, 5, 11, 10, 0));
        Usuario julia = daJulia.getProfissional();
        julia.setId(1L);
        Usuario polyana = daPolyana.getProfissional();
        polyana.setId(99L);
        polyana.setLogin("polyana");
        polyana.setDonaClinica(false);

        when(repository.findPagosPorDataPagamentoNoPeriodo(any(), any()))
                .thenReturn(List.of(daJulia, daPolyana));
        when(repository.findTodosPagosComDataPagamento()).thenReturn(List.of(daJulia, daPolyana));
        when(repository.findPorDataConsultaEStatusPagamentoNoPeriodo(any(), any(), any()))
                .thenReturn(List.of());
        when(usuarioRepository.findByCargoOrderByNomeAsc("ROLE_PROFISSIONAL"))
                .thenReturn(List.of(julia, polyana));
        when(authService.profissionalIgnoraValoresEPagamento(julia)).thenReturn(false);
        when(authService.profissionalIgnoraValoresEPagamento(polyana)).thenReturn(true);
        when(infinitePayService.resolverValorTaxaClinica(daJulia)).thenReturn(new BigDecimal("35.00"));

        ReceitaPixMesView resumo = service.montarResumoMes(YearMonth.of(2026, 5));

        assertEquals(1, resumo.getQuantidadePagamentos());
        assertEquals(0, new BigDecimal("35.00").compareTo(resumo.getTotalRecebido()));
    }

    @Test
    void deveSomarPagamentosConfirmadosNoMes() {
        Agendamento pago1 = agendamentoPago(LocalDateTime.of(2026, 5, 10, 14, 0));
        Agendamento pago2 = agendamentoPago(LocalDateTime.of(2026, 5, 28, 9, 30));
        when(repository.findPagosPorDataPagamentoNoPeriodo(any(), any()))
                .thenReturn(List.of(pago1, pago2));
        when(repository.findTodosPagosComDataPagamento()).thenReturn(List.of(pago1, pago2));
        when(repository.findPorDataConsultaEStatusPagamentoNoPeriodo(any(), any(), any()))
                .thenReturn(List.of());
        when(infinitePayService.resolverValorTaxaClinica(pago1)).thenReturn(new BigDecimal("35.00"));
        when(infinitePayService.resolverValorTaxaClinica(pago2)).thenReturn(new BigDecimal("32.00"));

        ReceitaPixMesView resumo = service.montarResumoMes(YearMonth.of(2026, 5));

        assertEquals(2, resumo.getQuantidadePagamentos());
        assertEquals(0, new BigDecimal("67.00").compareTo(resumo.getTotalRecebido()));
        assertEquals(true, resumo.getTotalRecebidoFormatado().contains("67"));
        assertEquals(0, resumo.getQuantidadePendentes());
        assertEquals(1, resumo.getProfissionaisPainel().size());
        assertEquals(2, resumo.getProfissionaisPainel().get(0).getAtendimentosMesAtual());
    }

    @Test
    void deveSomarTaxasPendentesPorDataDaConsulta() {
        Agendamento pendente = agendamentoPendente(
                LocalDateTime.of(2026, 6, 12, 10, 0),
                PagamentoStatus.AGUARDANDO_PAGAMENTO
        );
        when(repository.findPagosPorDataPagamentoNoPeriodo(any(), any())).thenReturn(List.of());
        when(repository.findTodosPagosComDataPagamento()).thenReturn(List.of());
        when(repository.findPorDataConsultaEStatusPagamentoNoPeriodo(any(), any(), any()))
                .thenReturn(List.of(pendente));
        when(infinitePayService.resolverValorTaxaClinica(pendente)).thenReturn(new BigDecimal("35.00"));

        ReceitaPixMesView resumo = service.montarResumoMes(YearMonth.of(2026, 6));

        assertEquals(1, resumo.getQuantidadePendentes());
        assertEquals(0, new BigDecimal("35.00").compareTo(resumo.getTotalAReceber()));
        assertEquals("Aguardando pagamento", resumo.getPendentes().get(0).getStatusPagamentoRotulo());
    }

    @Test
    void mesSemPagamentosDeveRetornarZero() {
        when(repository.findPagosPorDataPagamentoNoPeriodo(any(), any())).thenReturn(List.of());
        when(repository.findTodosPagosComDataPagamento()).thenReturn(List.of());
        when(repository.findPorDataConsultaEStatusPagamentoNoPeriodo(any(), any(), any()))
                .thenReturn(List.of());

        ReceitaPixMesView resumo = service.montarResumoMes(YearMonth.of(2026, 6));

        assertEquals(0, resumo.getQuantidadePagamentos());
        assertEquals(0, BigDecimal.ZERO.compareTo(resumo.getTotalRecebido()));
        assertEquals(0, resumo.getQuantidadePendentes());
        assertTrue(resumo.getProfissionaisPainel().isEmpty());
    }

    @Test
    void deveIdentificarMelhorMesPorQuantidadeDeAtendimentos() {
        Agendamento maio1 = agendamentoPago(LocalDateTime.of(2026, 5, 10, 14, 0));
        Agendamento maio2 = agendamentoPago(LocalDateTime.of(2026, 5, 12, 10, 0));
        Agendamento abril = agendamentoPago(LocalDateTime.of(2026, 4, 15, 9, 0));
        when(repository.findPagosPorDataPagamentoNoPeriodo(any(), any())).thenReturn(List.of(maio1, maio2));
        when(repository.findTodosPagosComDataPagamento()).thenReturn(List.of(maio1, maio2, abril));
        when(repository.findPorDataConsultaEStatusPagamentoNoPeriodo(any(), any(), any()))
                .thenReturn(List.of());
        when(infinitePayService.resolverValorTaxaClinica(maio1)).thenReturn(new BigDecimal("35.00"));
        when(infinitePayService.resolverValorTaxaClinica(maio2)).thenReturn(new BigDecimal("35.00"));
        when(infinitePayService.resolverValorTaxaClinica(abril)).thenReturn(new BigDecimal("50.00"));

        ProfissionalReceitaPainelView painel = service.montarResumoMes(YearMonth.of(2026, 5))
                .getProfissionaisPainel()
                .get(0);

        assertEquals(2, painel.getAtendimentosMesAtual());
        assertEquals(0, new BigDecimal("70.00").compareTo(painel.getValorMesAtual()));
        assertEquals(2, painel.getAtendimentosMelhorMes());
        assertTrue(painel.getMelhorMesLabel().toLowerCase().contains("maio"));
    }

    private Agendamento agendamentoPago(LocalDateTime dataPagamento) {
        Usuario profissional = new Usuario();
        profissional.setNome("Julia");
        profissional.setDonaClinica(false);

        Sala sala = new Sala();
        sala.setNome("Sala 1");

        Agendamento agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setNomeCliente("Cliente teste");
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        agendamento.setDataPagamento(dataPagamento);
        agendamento.setDataHoraInicio(dataPagamento.plusHours(5));
        agendamento.setValorClinicaCobra(new BigDecimal("35.00"));
        return agendamento;
    }

    private Agendamento agendamentoPendente(LocalDateTime dataConsulta, PagamentoStatus status) {
        Usuario profissional = new Usuario();
        profissional.setNome("Julia");
        profissional.setDonaClinica(false);

        Sala sala = new Sala();
        sala.setNome("Sala 1");

        Agendamento agendamento = new Agendamento();
        agendamento.setId(2L);
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setNomeCliente("Cliente pendente");
        agendamento.setStatusPagamento(status);
        agendamento.setDataHoraInicio(dataConsulta);
        agendamento.setValorClinicaCobra(new BigDecimal("35.00"));
        return agendamento;
    }

    private Sala sala(String nome) {
        Sala sala = new Sala();
        sala.setNome(nome);
        return sala;
    }
}
