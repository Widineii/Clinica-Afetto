package com.clinica.sistema;

import com.clinica.sistema.dto.ResumoPendenciasPagamentoView;
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
 * Cria consulta de amanhã para Julia no banco local (./data/clinica-local).
 * mvn test -Dtest=DemonstracaoJuliaPendenciasIntegracaoTest
 */
@SpringBootTest
@ActiveProfiles("local")
class DemonstracaoJuliaPendenciasIntegracaoTest {

    private static final String PREFIXO = "TESTE_JULIA_PEND_";

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
    void montarPendenciaJuliaParaFaixaVermelha() {
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();
        julia.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);
        usuarioRepository.save(julia);

        Sala sala = salaRepository.findAll().stream()
                .filter(s -> "Sala 1".equals(s.getNome()))
                .findFirst()
                .orElseGet(() -> salaRepository.findAll().get(0));

        LocalDate amanha = LocalDate.now().plusDays(1);
        salvar(julia, sala, amanha, LocalTime.of(10, 0), PREFIXO + "Consulta amanha (teste faixa vermelha)",
                PagamentoStatus.PAGAMENTO_FUTURO);

        List<Agendamento> pendentes = pagamentoConsultaService.listarPagamentosPendentesProximoDia(julia);
        assertFalse(pendentes.isEmpty(), "Julia deveria ter pendencia para amanha");

        ResumoPendenciasPagamentoView resumo = pagamentoConsultaService.montarResumoPendenciasPagamento(julia);
        assertTrue(resumo.quantidade() > 0, "Resumo de pendencias deveria ser > 0");

        System.out.println();
        System.out.println("========== TESTE FAIXA VERMELHA — JULIA ==========");
        System.out.println("App: http://localhost:8081/agendamentos/dashboard");
        System.out.println("Login: julia / senha: 297b");
        System.out.println("Consulta: " + amanha + " 10:00 | cliente: " + PREFIXO + "Consulta amanha (teste faixa vermelha)");
        System.out.println("Pendencias: " + resumo.quantidade() + " | total: " + resumo.valorTotalFormatado());
        System.out.println("==================================================");
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
