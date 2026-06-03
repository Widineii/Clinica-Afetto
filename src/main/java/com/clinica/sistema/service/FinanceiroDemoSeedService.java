package com.clinica.sistema.service;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class FinanceiroDemoSeedService {

    public static final String PREFIXO_CLIENTE = "DEMO-FIN-";
    public static final String PREFIXO_PENDENTE = "DEMO-FIN-PEND-";
    public static final int PAGAMENTOS_POR_PROFISSIONAL = 1;
    public static final int PENDENTES_POR_PROFISSIONAL = 1;

    private static final BigDecimal[] TAXAS = {
            new BigDecimal("35.00"),
            new BigDecimal("32.00"),
            new BigDecimal("25.00")
    };

    private static final String[] TIPOS_RECORRENCIA = {"AVULSO", "SEMANAL", "QUINZENAL", "AVULSO"};

    private final AgendamentoRepository agendamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SalaRepository salaRepository;
    private final ValorConsultaService valorConsultaService;

    public FinanceiroDemoSeedService(
            AgendamentoRepository agendamentoRepository,
            UsuarioRepository usuarioRepository,
            SalaRepository salaRepository,
            ValorConsultaService valorConsultaService
    ) {
        this.agendamentoRepository = agendamentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.salaRepository = salaRepository;
        this.valorConsultaService = valorConsultaService;
    }

    @Transactional
    public int semearPixDemonstracaoMesAtual() {
        agendamentoRepository.deleteByNomeClienteLike(PREFIXO_CLIENTE + "%");

        List<Usuario> profissionais = usuarioRepository.findByCargoOrderByNomeAsc("ROLE_PROFISSIONAL").stream()
                .filter(prof -> !Boolean.TRUE.equals(prof.getDonaClinica()))
                .toList();
        List<Sala> salas = salaRepository.findAllByOrderByNomeAsc();
        if (profissionais.isEmpty() || salas.isEmpty()) {
            return 0;
        }

        YearMonth mes = YearMonth.now();
        List<Agendamento> criados = new ArrayList<>();
        int indice = 1;
        for (int indiceProfissional = 0; indiceProfissional < profissionais.size(); indiceProfissional++) {
            Usuario profissional = profissionais.get(indiceProfissional);
            for (int pagamento = 0; pagamento < PAGAMENTOS_POR_PROFISSIONAL; pagamento++) {
                Sala sala = salas.get((indiceProfissional + pagamento) % salas.size());
                String tipoRecorrencia = TIPOS_RECORRENCIA[pagamento % TIPOS_RECORRENCIA.length];
                BigDecimal taxa = TAXAS[(indiceProfissional + pagamento) % TAXAS.length];
                int diaBase = 1 + ((indiceProfissional * PAGAMENTOS_POR_PROFISSIONAL + pagamento) * 2) % mes.lengthOfMonth();
                int horaConsulta = 8 + ((pagamento * 2 + indiceProfissional) % 10);
                int minutoConsulta = (pagamento * 15 + indiceProfissional * 7) % 60;
                int horaPagamento = Math.min(horaConsulta + 1, 20);
                int minutoPagamento = (minutoConsulta + 25) % 60;

                LocalDateTime consulta = mes.atDay(diaBase).atTime(horaConsulta, minutoConsulta);
                int diaPagamento = Math.min(diaBase + (pagamento % 2), mes.lengthOfMonth());
                LocalDateTime dataPagamento = mes.atDay(diaPagamento).atTime(horaPagamento, minutoPagamento);

                Agendamento agendamento = new Agendamento();
                agendamento.setProfissional(profissional);
                agendamento.setSala(sala);
                agendamento.setNomeCliente(PREFIXO_CLIENTE + indice + " " + profissional.getNome());
                agendamento.setDataHoraInicio(consulta);
                agendamento.setDataHoraFim(consulta.plusHours(1));
                agendamento.setFixo(!"AVULSO".equalsIgnoreCase(tipoRecorrencia));
                agendamento.setTipoRecorrencia(tipoRecorrencia);
                BigDecimal valorConsultaCliente = new BigDecimal("150.00");
                agendamento.setValorProfissionalRecebe(valorConsultaCliente);
                agendamento.setValorClinicaCobra(taxa);
                agendamento.setValorLiquidoProfissional(valorConsultaCliente.subtract(taxa));
                agendamento.setValorPagamento(taxa);
                agendamento.setStatusPagamento(PagamentoStatus.PAGO);
                agendamento.setDataPagamento(dataPagamento);
                agendamento.setDataReferenciaMesPagamento(mes.atDay(1));
                agendamento.setPagamentoOrderNsu("demo-fin-" + mes + "-" + indice);
                valorConsultaService.aplicarValoresPadraoProfissionalNoAgendamento(agendamento, profissional);
                agendamento.setValorPagamento(taxa);
                agendamento.setStatusPagamento(PagamentoStatus.PAGO);
                criados.add(agendamento);
                indice++;
            }
        }

        if (!criados.isEmpty()) {
            agendamentoRepository.saveAll(criados);
        }
        return criados.size();
    }

    @Transactional
    public int limparPixDemonstracao() {
        return agendamentoRepository.deleteByNomeClienteLike(PREFIXO_CLIENTE + "%");
    }
}
