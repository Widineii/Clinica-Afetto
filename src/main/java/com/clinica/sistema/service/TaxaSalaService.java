package com.clinica.sistema.service;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.util.MoedaBrasilUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class TaxaSalaService {

    public static final BigDecimal TAXA_SALA_4_PADRAO = ValorConsultaService.CLINICA_SALA_4;

    private final SalaRepository salaRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ValorConsultaService valorConsultaService;

    public TaxaSalaService(
            SalaRepository salaRepository,
            AgendamentoRepository agendamentoRepository,
            ValorConsultaService valorConsultaService
    ) {
        this.salaRepository = salaRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.valorConsultaService = valorConsultaService;
    }

    public BigDecimal taxaClinicaSala4() {
        return valorConsultaService.taxaClinicaSala4Atual();
    }

    public String taxaClinicaSala4Input() {
        return MoedaBrasilUtil.formatarDecimal(taxaClinicaSala4());
    }

    public Long idSala4() {
        return salaRepository.findByNomeIgnoreCase("Sala 4").map(Sala::getId).orElse(null);
    }

    @Transactional
    public int atualizarTaxaSala4(BigDecimal novaTaxa) {
        if (novaTaxa == null || novaTaxa.signum() <= 0) {
            throw new RuntimeException("Informe a taxa da Sala 4.");
        }
        BigDecimal taxaNormalizada = novaTaxa.setScale(2, RoundingMode.HALF_UP);
        Sala sala4 = salaRepository.findByNomeIgnoreCase("Sala 4")
                .orElseThrow(() -> new RuntimeException("Sala 4 nao encontrada."));
        sala4.setTaxaClinica(taxaNormalizada);
        salaRepository.save(sala4);
        return propagarTaxaSala4AgendamentosPendentes();
    }

    private int propagarTaxaSala4AgendamentosPendentes() {
        List<Agendamento> agendamentos = agendamentoRepository.listarSala4ParaPropagacaoTaxa();
        int atualizados = 0;
        for (Agendamento agendamento : agendamentos) {
            if (valorConsultaService.aplicarValoresPadraoProfissionalNoAgendamento(
                    agendamento,
                    agendamento.getProfissional()
            )) {
                atualizados++;
            }
        }
        if (atualizados > 0) {
            agendamentoRepository.saveAll(agendamentos);
        }
        return atualizados;
    }
}
