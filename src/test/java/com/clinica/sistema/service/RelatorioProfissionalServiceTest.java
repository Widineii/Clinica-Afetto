package com.clinica.sistema.service;

import com.clinica.sistema.dto.RelatorioProfissionalMesView;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioProfissionalServiceTest {

    @Mock
    private AgendamentoService agendamentoService;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private InfinitePayService infinitePayService;

    private RelatorioProfissionalService service;

    @BeforeEach
    void setUp() {
        service = new RelatorioProfissionalService(
                agendamentoService,
                agendamentoRepository,
                infinitePayService
        );
    }

    @Test
    void montarRelatorio_agregaAtendimentosTaxasEMelhorMes() {
        Usuario carol = new Usuario();
        carol.setId(10L);
        carol.setNome("Carol");
        carol.setCargo("ROLE_PROFISSIONAL");

        YearMonth maio = YearMonth.of(2026, 5);
        Agendamento atendimento = criarAtendimento(carol, maio.atDay(10).atTime(19, 0), PagamentoStatus.PAGO);
        atendimento.setDataPagamento(maio.atDay(9).atTime(14, 30));

        when(agendamentoService.listarAtendimentosProfissionalNoMes(10L, maio)).thenReturn(List.of(atendimento));
        when(agendamentoRepository.findPagosPorProfissionalEDataPagamentoNoPeriodo(
                eq(10L), any(), any()
        )).thenReturn(List.of(atendimento));
        when(agendamentoRepository.findPagosPorProfissionalComDataPagamento(10L)).thenReturn(List.of(atendimento));
        when(infinitePayService.resolverValorTaxaClinica(atendimento)).thenReturn(new BigDecimal("35.00"));

        RelatorioProfissionalMesView relatorio = service.montarRelatorio(carol, maio);

        assertEquals(1, relatorio.getTotalAtendimentos());
        assertEquals(1, relatorio.getTotalPixPagos());
        assertEquals(new BigDecimal("35.00"), relatorio.getTotalTaxasPagas());
        assertEquals("Maio de 2026", relatorio.getMelhorMesLabel());
        assertEquals(1, relatorio.getAtendimentosMelhorMes());
    }

    @Test
    void montarRelatorio_filtraPorSala() {
        Usuario carol = new Usuario();
        carol.setId(10L);
        carol.setNome("Carol");
        carol.setCargo("ROLE_PROFISSIONAL");

        YearMonth maio = YearMonth.of(2026, 5);
        Agendamento sala1 = criarAtendimento(carol, maio.atDay(10).atTime(19, 0), PagamentoStatus.PAGO, "Sala 1");
        sala1.setDataPagamento(maio.atDay(9).atTime(14, 30));
        Agendamento sala2 = criarAtendimento(carol, maio.atDay(12).atTime(10, 0), PagamentoStatus.PAGO, "Sala 2");
        sala2.setId(2L);
        sala2.setDataPagamento(maio.atDay(11).atTime(9, 0));

        when(agendamentoService.listarAtendimentosProfissionalNoMes(10L, maio))
                .thenReturn(List.of(sala1, sala2));
        when(agendamentoRepository.findPagosPorProfissionalEDataPagamentoNoPeriodo(
                eq(10L), any(), any()
        )).thenReturn(List.of(sala1, sala2));
        when(agendamentoRepository.findPagosPorProfissionalComDataPagamento(10L)).thenReturn(List.of(sala1, sala2));
        when(infinitePayService.resolverValorTaxaClinica(sala1)).thenReturn(new BigDecimal("35.00"));
        when(infinitePayService.resolverValorTaxaClinica(sala2)).thenReturn(new BigDecimal("32.00"));

        RelatorioProfissionalMesView relatorio = service.montarRelatorio(carol, maio, "Sala 2");

        assertEquals(1, relatorio.getTotalAtendimentos());
        assertEquals(1, relatorio.getTotalPixPagos());
        assertEquals(new BigDecimal("32.00"), relatorio.getTotalTaxasPagas());
        assertEquals("Sala 2", relatorio.getSalaFiltro());
    }

    private Agendamento criarAtendimento(
            Usuario profissional,
            LocalDateTime inicio,
            PagamentoStatus status,
            String nomeSala
    ) {
        Sala sala = new Sala();
        sala.setId(1L);
        sala.setNome(nomeSala);

        Agendamento agendamento = new Agendamento();
        agendamento.setId(1L);
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setNomeCliente("Cliente teste");
        agendamento.setDataHoraInicio(inicio);
        agendamento.setDataHoraFim(inicio.plusHours(1));
        agendamento.setFixo(false);
        agendamento.setTipoRecorrencia("AVULSO");
        agendamento.setStatusPagamento(status);
        return agendamento;
    }

    private Agendamento criarAtendimento(Usuario profissional, LocalDateTime inicio, PagamentoStatus status) {
        return criarAtendimento(profissional, inicio, status, "Sala 1");
    }
}
