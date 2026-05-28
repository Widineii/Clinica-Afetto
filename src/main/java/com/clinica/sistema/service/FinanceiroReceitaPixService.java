package com.clinica.sistema.service;

import com.clinica.sistema.dto.ReceitaPixLinhaView;
import com.clinica.sistema.dto.ReceitaPixMesView;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.repository.AgendamentoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
public class FinanceiroReceitaPixService {

    private final AgendamentoRepository agendamentoRepository;
    private final InfinitePayService infinitePayService;

    public FinanceiroReceitaPixService(
            AgendamentoRepository agendamentoRepository,
            InfinitePayService infinitePayService
    ) {
        this.agendamentoRepository = agendamentoRepository;
        this.infinitePayService = infinitePayService;
    }

    public ReceitaPixMesView montarResumoMes(YearMonth mesSelecionado) {
        LocalDateTime inicio = mesSelecionado.atDay(1).atStartOfDay();
        LocalDateTime fim = mesSelecionado.plusMonths(1).atDay(1).atStartOfDay();

        List<ReceitaPixLinhaView> linhas = agendamentoRepository
                .findPagosPorDataPagamentoNoPeriodo(inicio, fim)
                .stream()
                .filter(this::contaParaReceitaClinica)
                .map(this::paraLinha)
                .toList();

        BigDecimal total = linhas.stream()
                .map(ReceitaPixLinhaView::getValorTaxa)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ReceitaPixMesView(mesSelecionado, linhas, total);
    }

    private boolean contaParaReceitaClinica(Agendamento agendamento) {
        if (agendamento.getProfissional() == null) {
            return true;
        }
        return !Boolean.TRUE.equals(agendamento.getProfissional().getDonaClinica());
    }

    private ReceitaPixLinhaView paraLinha(Agendamento agendamento) {
        BigDecimal valorTaxa = infinitePayService.resolverValorTaxaClinica(agendamento);
        return new ReceitaPixLinhaView(agendamento, valorTaxa);
    }
}
