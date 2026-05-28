package com.clinica.sistema;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.service.PagamentoConsultaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();
        Sala sala = salaRepository.findAll().stream()
                .filter(s -> "Sala 1".equals(s.getNome()))
                .findFirst()
                .orElseGet(() -> salaRepository.findAll().get(0));

        LocalDate hoje = LocalDate.now();
        LocalDate amanha = hoje.plusDays(1);
        LocalTime horarioHoje = LocalTime.now().isBefore(LocalTime.of(19, 0))
                ? LocalTime.of(19, 0)
                : LocalTime.of(21, 0);

        salvar(julia, sala, amanha, LocalTime.of(10, 0), PREFIXO + "Amanha (pagar hoje)",
                PagamentoStatus.PAGAMENTO_FUTURO);
        salvar(julia, sala, hoje, horarioHoje, PREFIXO + "Hoje (esqueceu de pagar)",
                PagamentoStatus.LIBERADO_FALTA_PAGAMENTO);

        LocalDate inicioSemana = hoje.getDayOfWeek() == DayOfWeek.SUNDAY
                ? hoje.plusDays(1)
                : hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sabadoSemana = inicioSemana.plusDays(5);
        LocalDate diaSemana = sabadoSemana;
        if (diaSemana.equals(hoje) || diaSemana.equals(amanha) || !diaSemana.isAfter(hoje)) {
            diaSemana = amanha.plusDays(2);
            if (diaSemana.getDayOfWeek() == DayOfWeek.SUNDAY) {
                diaSemana = diaSemana.plusDays(1);
            }
        }
        if (diaSemana.getDayOfWeek() == DayOfWeek.SUNDAY) {
            diaSemana = diaSemana.minusDays(1);
        }

        salvar(julia, sala, diaSemana, LocalTime.of(14, 0), PREFIXO + "Semana (adiantar)",
                PagamentoStatus.PAGAMENTO_FUTURO);
        LocalDate diaSemanaB = diaSemana.minusDays(1);
        if (!diaSemanaB.equals(hoje)
                && !diaSemanaB.equals(amanha)
                && !diaSemanaB.isBefore(hoje)
                && diaSemanaB.getDayOfWeek() != DayOfWeek.SUNDAY) {
            salvar(julia, sala, diaSemanaB, LocalTime.of(15, 0), PREFIXO + "Semana B (adiantar)",
                    PagamentoStatus.PAGAMENTO_FUTURO);
        }

        List<Agendamento> pendentes = pagamentoConsultaService.listarPagamentosPendentesProximoDia(julia);
        List<Agendamento> semana = pagamentoConsultaService.listarConsultasAdiantamentoSemanaAtual(julia);

        assertFalse(pendentes.isEmpty(), "Deveria haver pendencias (amanha e/ou hoje)");
        assertFalse(semana.isEmpty(), "Deveria haver consultas na aba da semana");

        imprimirRoteiro(pendentes.size(), semana.size(),
                pagamentoConsultaService.formatarTotalTaxaPix(pendentes),
                pagamentoConsultaService.formatarTotalTaxaPix(semana),
                amanha, hoje, horarioHoje, diaSemana);
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
        System.out.println("Login: julia / senha: 297b");
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
