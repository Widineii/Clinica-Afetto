package com.clinica.sistema;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.PeriodicidadePagamento;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prepara Carol com pendencia que bloqueia novo agendamento (banco local H2).
 * Rode com o app parado: mvn test -Dtest=DemonstracaoBloqueioAgendaIntegracaoTest
 */
@SpringBootTest
@ActiveProfiles("local")
class DemonstracaoBloqueioAgendaIntegracaoTest {

    private static final String PREFIXO = "TESTE_BLOQUEIO_";

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
    void limparAnteriores() {
        removerChecksStatusPagamentoH2();
        agendamentoRepository.findAll().stream()
                .filter(a -> a.getNomeCliente() != null && a.getNomeCliente().startsWith(PREFIXO))
                .forEach(a -> agendamentoRepository.deleteById(a.getId()));
    }

    @Test
    void montarCarolComPendenciaBloqueandoAgenda() {
        Usuario carol = usuarioRepository.findByLogin("carol").orElseThrow();
        carol.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);
        usuarioRepository.save(carol);

        Sala sala = salaRepository.findAll().stream()
                .filter(s -> "Sala 1".equals(s.getNome()))
                .findFirst()
                .orElseGet(() -> salaRepository.findAll().get(0));

        LocalDate hoje = LocalDate.now();
        LocalTime horario = LocalTime.now().getHour() < 20 ? LocalTime.of(20, 0) : LocalTime.of(21, 0);

        salvar(carol, sala, hoje, horario, PREFIXO + "Consulta hoje sem pagar",
                PagamentoStatus.AGUARDANDO_PAGAMENTO);

        assertTrue(
                pagamentoConsultaService.profissionalBloqueadoPorPendenciaPagamento(carol),
                "Carol deveria estar bloqueada para novo agendamento"
        );
        assertFalse(
                pagamentoConsultaService.listarPendenciasObrigatoriasParaBloqueio(carol).isEmpty(),
                "Deveria haver pendencia obrigatoria"
        );

        var resumo = pagamentoConsultaService.montarResumoBloqueioAgendamento(carol);
        System.out.println();
        System.out.println("=== Cenario pronto para teste da Carol ===");
        System.out.println("Login: carol / 297b");
        System.out.println("URL: http://localhost:8081/login");
        System.out.println("Pendencias bloqueio: " + resumo.quantidade());
        System.out.println("Total em aberto: " + resumo.valorTotalFormatado());
        System.out.println("Tente salvar um agendamento — deve abrir o modal de bloqueio.");
        System.out.println();
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
        agendamento.setRecorrencia("AVULSO");
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
}
