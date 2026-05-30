package com.clinica.sistema;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.service.PagamentoConsultaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cria consultas de teste no banco local (./data/clinica-local) para a tela Meus pagamentos.
 * Rode com o app parado ou ligado (H2 AUTO_SERVER): mvn test -Dtest=DemonstracaoMeusPagamentosIntegracaoTest
 */
@SpringBootTest
@ActiveProfiles("local")
class DemonstracaoMeusPagamentosIntegracaoTest {

    private static final String PREFIXO = "TESTE_PAG_";

    @Autowired
    private PagamentoConsultaService pagamentoConsultaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SalaRepository salaRepository;

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparTestesAnteriores() {
        removerChecksStatusPagamentoH2();
        agendamentoRepository.findAll().stream()
                .filter(a -> a.getNomeCliente() != null && a.getNomeCliente().startsWith(PREFIXO))
                .forEach(a -> agendamentoRepository.deleteById(a.getId()));
    }

    @Test
    void montarCenarioMeusPagamentos() {
        comDataReferencia(LocalDate.of(2026, 5, 28), LocalTime.of(10, 0), () -> {
        Usuario carol = usuarioRepository.findByLogin("carol").orElseThrow();
        carol.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);
        usuarioRepository.save(carol);
        Sala sala = salaRepository.findAll().stream()
                .filter(s -> "Sala 1".equals(s.getNome()))
                .findFirst()
                .orElseGet(() -> salaRepository.findAll().get(0));

        LocalDate hoje = LocalDate.now();
        LocalDate amanha = hoje.plusDays(1);
        LocalTime horarioHoje = LocalTime.now().isBefore(LocalTime.of(19, 0))
                ? LocalTime.of(19, 0)
                : LocalTime.of(21, 0);

        salvar(carol, sala, amanha, LocalTime.of(10, 0), PREFIXO + "Amanha (pagar hoje)",
                PagamentoStatus.PAGAMENTO_FUTURO);
        salvar(carol, sala, hoje, horarioHoje, PREFIXO + "Hoje (esqueceu de pagar)",
                PagamentoStatus.LIBERADO_FALTA_PAGAMENTO);

        LocalDate diaSemana = resolverDiaAdiantamentoSemana(hoje, amanha);

        salvar(carol, sala, diaSemana, LocalTime.of(14, 0), PREFIXO + "Semana (adiantar)",
                PagamentoStatus.PAGAMENTO_FUTURO);
        LocalDate diaSemanaB = diaSemana.minusDays(1);
        if (!diaSemanaB.equals(hoje)
                && !diaSemanaB.equals(amanha)
                && !diaSemanaB.isBefore(hoje)
                && diaSemanaB.getDayOfWeek() != DayOfWeek.SUNDAY
                && !deveAbrirPagamentoAgora(diaSemanaB)) {
            salvar(carol, sala, diaSemanaB, LocalTime.of(15, 0), PREFIXO + "Semana B (adiantar)",
                    PagamentoStatus.PAGAMENTO_FUTURO);
        }

        List<Agendamento> pendentes = pagamentoConsultaService.listarPagamentosPendentesProximoDia(carol);
        List<Agendamento> semana = pagamentoConsultaService.listarConsultasAdiantamentoSemanaAtual(carol);

        assertFalse(pendentes.isEmpty(), "Deveria haver pendencias (amanha e/ou hoje)");
        assertFalse(semana.isEmpty(), "Deveria haver consultas na aba da semana");

        imprimirRoteiro(pendentes.size(), semana.size(),
                pagamentoConsultaService.formatarTotalTaxaPix(pendentes),
                pagamentoConsultaService.formatarTotalTaxaPix(semana),
                amanha, hoje, horarioHoje, diaSemana);
        });
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

    private LocalDate resolverDiaAdiantamentoSemana(LocalDate hoje, LocalDate amanha) {
        LocalDate inicioSemana = hoje.getDayOfWeek() == DayOfWeek.SUNDAY
                ? hoje.plusDays(1)
                : hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fimSemana = inicioSemana.plusDays(6);
        LocalDate candidato = hoje.plusDays(1);
        while (!candidato.isAfter(fimSemana)) {
            if (!candidato.equals(hoje)
                    && !candidato.equals(amanha)
                    && candidato.isAfter(hoje)
                    && !deveAbrirPagamentoAgora(candidato)) {
                return candidato;
            }
            candidato = candidato.plusDays(1);
        }
        for (LocalDate dia = hoje.plusDays(1); !dia.isAfter(fimSemana); dia = dia.plusDays(1)) {
            if (!dia.equals(hoje) && !dia.equals(amanha) && LocalDate.now().isBefore(dia.minusDays(1))) {
                return dia;
            }
        }
        return fimSemana;
    }

    private boolean deveAbrirPagamentoAgora(LocalDate dataConsulta) {
        LocalDate diaLimitePagamento = dataConsulta.minusDays(1);
        return !LocalDate.now().isBefore(diaLimitePagamento);
    }

    private Agendamento salvar(
            Usuario profissional,
            Sala sala,
            LocalDate data,
            LocalTime hora,
            String cliente,
            PagamentoStatus status
    ) {
        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setNomeCliente(cliente);
        agendamento.setDataHoraInicio(LocalDateTime.of(data, hora));
        agendamento.setDataHoraFim(LocalDateTime.of(data, hora.plusHours(1)));
        agendamento.setFixo(false);
        agendamento.setTipoRecorrencia("AVULSO");
        agendamento.setValorProfissionalRecebe(new BigDecimal("100.00"));
        agendamento.setValorClinicaCobra(new BigDecimal("35.00"));
        agendamento.setValorLiquidoProfissional(new BigDecimal("65.00"));
        agendamento.setValorPagamento(new BigDecimal("35.00"));
        agendamento.setIndicacaoDona(false);
        agendamento.setStatusPagamento(status);
        return agendamentoRepository.save(agendamento);
    }

    private void removerChecksStatusPagamentoH2() {
        List<String> checks = jdbcTemplate.queryForList(
                """
                SELECT CONSTRAINT_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE UPPER(TABLE_NAME) = 'AGENDAMENTOS'
                  AND CONSTRAINT_TYPE = 'CHECK'
                """,
                String.class
        );
        for (String constraint : checks) {
            jdbcTemplate.execute("ALTER TABLE agendamentos DROP CONSTRAINT " + constraint);
        }
    }

    private void imprimirRoteiro(
            int qtdPendentes,
            int qtdSemana,
            String totalPendentes,
            String totalSemana,
            LocalDate amanha,
            LocalDate hoje,
            LocalTime horarioHoje,
            LocalDate diaSemana
    ) {
        System.out.println();
        System.out.println("========== TESTE MEUS PAGAMENTOS ==========");
        System.out.println("App: http://localhost:8081/agendamentos/meus-pagamentos");
        System.out.println("Login: carol / senha: 297b");
        System.out.println();
        System.out.println("Aba Pagamentos pendentes: " + qtdPendentes + " consulta(s) | total selecionar todas: " + totalPendentes);
        System.out.println("  - " + PREFIXO + "Amanha (pagar hoje) -> " + amanha + " 10:00");
        System.out.println("  - " + PREFIXO + "Hoje (esqueceu de pagar) -> " + hoje + " " + horarioHoje);
        System.out.println();
        System.out.println("Aba Pagar a semana toda: " + qtdSemana + " consulta(s) | total semana: " + totalSemana);
        System.out.println("  - Consultas em/apos " + diaSemana + " (fora das obrigatorias de hoje/amanha)");
        System.out.println("================================================");
        System.out.println();
    }
}
