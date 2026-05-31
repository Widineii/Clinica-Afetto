package com.clinica.sistema.service;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RelatorioProfissionalDemoSeedService {

    public static final String PREFIXO_CLIENTE = "DEMO-REL-";

    private static final List<DemoAtendimento> ATENDIMENTOS_DEMO = List.of(
            new DemoAtendimento("Sala 1", "AVULSO", new BigDecimal("35.00"), 5, 10, 0),
            new DemoAtendimento("Sala 1", "SEMANAL", new BigDecimal("32.00"), 12, 14, 30),
            new DemoAtendimento("Sala 2", "AVULSO", new BigDecimal("35.00"), 8, 16, 0),
            new DemoAtendimento("Sala 2", "QUINZENAL", new BigDecimal("32.00"), 18, 11, 15),
            new DemoAtendimento("Sala 3", "AVULSO", new BigDecimal("25.00"), 22, 9, 45)
    );

    private final AgendamentoRepository agendamentoRepository;
    private final SalaRepository salaRepository;

    public RelatorioProfissionalDemoSeedService(
            AgendamentoRepository agendamentoRepository,
            SalaRepository salaRepository
    ) {
        this.agendamentoRepository = agendamentoRepository;
        this.salaRepository = salaRepository;
    }

    @Transactional
    public int semearDemonstracaoProfissional(Usuario profissional) {
        if (profissional == null || profissional.getId() == null) {
            return 0;
        }

        limparDemonstracaoProfissional(profissional);

        Map<String, Sala> salasPorNome = salaRepository.findAllByOrderByNomeAsc().stream()
                .filter(sala -> sala.getNome() != null)
                .collect(Collectors.toMap(Sala::getNome, Function.identity(), (a, b) -> a));

        YearMonth mes = YearMonth.now();
        List<Agendamento> criados = new ArrayList<>();
        int indice = 1;
        for (DemoAtendimento demo : ATENDIMENTOS_DEMO) {
            Sala sala = salasPorNome.get(demo.salaNome());
            if (sala == null) {
                continue;
            }

            int dia = Math.min(demo.dia(), mes.lengthOfMonth());
            LocalDateTime consulta = mes.atDay(dia).atTime(demo.hora(), demo.minuto());
            LocalDateTime pagamento = consulta.minusHours(2);
            if (pagamento.getMonth() != mes.getMonth()) {
                pagamento = mes.atDay(dia).atTime(8, 30);
            }

            Agendamento agendamento = new Agendamento();
            agendamento.setProfissional(profissional);
            agendamento.setSala(sala);
            agendamento.setNomeCliente(PREFIXO_CLIENTE + indice + " " + profissional.getNome());
            agendamento.setDataHoraInicio(consulta);
            agendamento.setDataHoraFim(consulta.plusHours(1));
            agendamento.setFixo(!"AVULSO".equalsIgnoreCase(demo.tipoRecorrencia()));
            agendamento.setTipoRecorrencia(demo.tipoRecorrencia());
            agendamento.setValorClinicaCobra(demo.taxa());
            agendamento.setValorPagamento(demo.taxa());
            agendamento.setValorProfissionalRecebe(new BigDecimal("100.00"));
            agendamento.setStatusPagamento(PagamentoStatus.PAGO);
            agendamento.setDataPagamento(pagamento);
            agendamento.setDataReferenciaMesPagamento(mes.atDay(1));
            agendamento.setPagamentoOrderNsu("demo-rel-" + profissional.getId() + "-" + mes + "-" + indice);
            criados.add(agendamento);
            indice++;
        }

        if (!criados.isEmpty()) {
            agendamentoRepository.saveAll(criados);
        }
        return criados.size();
    }

    @Transactional
    public int limparDemonstracaoProfissional(Usuario profissional) {
        if (profissional == null || profissional.getId() == null) {
            return 0;
        }
        return agendamentoRepository.deleteByProfissionalIdAndNomeClienteLike(
                profissional.getId(),
                PREFIXO_CLIENTE + "%"
        );
    }

    private record DemoAtendimento(
            String salaNome,
            String tipoRecorrencia,
            BigDecimal taxa,
            int dia,
            int hora,
            int minuto
    ) {
    }
}
