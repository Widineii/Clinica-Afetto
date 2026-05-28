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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Prepara consultas da semana atual para demonstrar PIX unico em Meus pagamentos.
 * Rode com a aplicacao local no ar: mvn test -Dtest=DemonstracaoPagamentoSemanaIntegracaoTest
 */
@SpringBootTest
@ActiveProfiles("local")
class DemonstracaoPagamentoSemanaIntegracaoTest {

    private static final String PREFIXO = "DEMO_SEMANA_";

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
    void limparDemonstracaoAnterior() {
        removerChecksStatusPagamentoH2();
        agendamentoRepository.findAll().stream()
                .filter(a -> a.getNomeCliente() != null && a.getNomeCliente().startsWith(PREFIXO))
                .forEach(a -> agendamentoRepository.deleteById(a.getId()));
    }

    @Test
    void montarCenarioPagamentoSemanaUnico() {
        Usuario carol = usuarioRepository.findByLogin("julia").orElseThrow();
        Sala sala1 = salaRepository.findAll().stream()
                .filter(s -> "Sala 1".equals(s.getNome()))
                .findFirst()
                .orElseThrow();

        LocalDate inicioSemana = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fimSemana = inicioSemana.plusDays(6);

        Sala sala2 = salaRepository.findAll().stream()
                .filter(s -> "Sala 2".equals(s.getNome()))
                .findFirst()
                .orElse(sala1);

        LocalDate dia1 = inicioSemana.plusDays(4);
        LocalDate dia2 = inicioSemana.plusDays(5);
        if (dia2.isAfter(fimSemana)) {
            dia1 = fimSemana.minusDays(2);
            dia2 = fimSemana.minusDays(1);
        }

        salvarConsultaFutura(carol, sala2, dia1, LocalTime.of(15, 0), PREFIXO + "Consulta A");
        salvarConsultaFutura(carol, sala2, dia2, LocalTime.of(16, 0), PREFIXO + "Consulta B");

        List<Agendamento> semana = pagamentoConsultaService.listarConsultasAdiantamentoSemanaAtual(carol);
        assertFalse(semana.isEmpty(), "Deveria haver consultas da semana para adiantar");

        if (semana.size() < 2) {
            LocalDate diaExtra = fimSemana.minusDays(1);
            salvarConsultaFutura(carol, sala2, diaExtra, LocalTime.of(18, 0), PREFIXO + "Consulta C");
            semana = pagamentoConsultaService.listarConsultasAdiantamentoSemanaAtual(carol);
        }

        String total = pagamentoConsultaService.formatarTotalTaxaPix(semana);

        imprimirRoteiro(total, dia1, dia2, semana.size());
    }

    private Agendamento salvarConsultaFutura(
            Usuario profissional,
            Sala sala,
            LocalDate data,
            LocalTime hora,
            String cliente
    ) {
        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(profissional);
        agendamento.setSala(sala);
        agendamento.setNomeCliente(cliente);
        agendamento.setDataHoraInicio(LocalDateTime.of(data, hora));
        agendamento.setDataHoraFim(LocalDateTime.of(data, hora.plusHours(1)));
        agendamento.setFixo(true);
        agendamento.setTipoRecorrencia("SEMANAL");
        agendamento.setSerieFixaId("demo-semana-" + cliente.hashCode());
        agendamento.setValorProfissionalRecebe(new BigDecimal("100.00"));
        agendamento.setValorClinicaCobra(new BigDecimal("35.00"));
        agendamento.setValorLiquidoProfissional(new BigDecimal("65.00"));
        agendamento.setValorPagamento(new BigDecimal("35.00"));
        agendamento.setIndicacaoDona(false);
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
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

    private void imprimirRoteiro(String total, LocalDate dia1, LocalDate dia2, int quantidade) {
        System.out.println();
        System.out.println("========== DEMO PAGAMENTO SEMANA (PIX UNICO) ==========");
        System.out.println("App: http://localhost:8081");
        System.out.println("Login: julia / 297b");
        System.out.println();
        System.out.println("1) Abra: http://localhost:8081/agendamentos/meus-pagamentos");
        System.out.println("2) Clique na aba: Pagar a semana toda (badge " + quantidade + ")");
        System.out.println("3) Total esperado: " + total + " (2 x R$ 35,00)");
        System.out.println("4) Clique: Pagar semana inteira (PIX unico)");
        System.out.println("5) Na tela do PIX, confirme no checkout teste");
        System.out.println();
        System.out.println("Consultas criadas: " + PREFIXO + "Consulta A (" + dia1 + " 10:00)");
        System.out.println("                   " + PREFIXO + "Consulta B (" + dia2 + " 11:00)");
        System.out.println("=======================================================");
        System.out.println();
    }
}
