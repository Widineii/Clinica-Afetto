package com.clinica.sistema.service;

import com.clinica.sistema.dto.ReceitaPixMesView;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceiroReceitaPixServiceTest {

    @Mock
    private AgendamentoRepository repository;

    @Mock
    private InfinitePayService infinitePayService;

    private FinanceiroReceitaPixService service;

    @BeforeEach
    void setUp() {
        service = new FinanceiroReceitaPixService(repository, infinitePayService);
    }

    @Test
    void deveSomarPagamentosConfirmadosNoMes() {
        Agendamento pago1 = agendamentoPago(LocalDateTime.of(2026, 5, 10, 14, 0));
        Agendamento pago2 = agendamentoPago(LocalDateTime.of(2026, 5, 28, 9, 30));
        when(repository.findPagosPorDataPagamentoNoPeriodo(any(), any()))
                .thenReturn(List.of(pago1, pago2));
        when(infinitePayService.resolverValorTaxaClinica(pago1)).thenReturn(new BigDecimal("35.00"));
        when(infinitePayService.resolverValorTaxaClinica(pago2)).thenReturn(new BigDecimal("32.00"));

        ReceitaPixMesView resumo = service.montarResumoMes(YearMonth.of(2026, 5));

        assertEquals(2, resumo.getQuantidadePagamentos());
        assertEquals(0, new BigDecimal("67.00").compareTo(resumo.getTotalRecebido()));
        assertEquals(true, resumo.getTotalRecebidoFormatado().contains("67"));
    }

    @Test
    void mesSemPagamentosDeveRetornarZero() {
        when(repository.findPagosPorDataPagamentoNoPeriodo(any(), any())).thenReturn(List.of());

        ReceitaPixMesView resumo = service.montarResumoMes(YearMonth.of(2026, 6));

        assertEquals(0, resumo.getQuantidadePagamentos());
        assertEquals(0, BigDecimal.ZERO.compareTo(resumo.getTotalRecebido()));
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
}
