package com.clinica.sistema;

import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.RelocacaoAgendamentoForm;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.service.AgendamentoService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Monta cenario de demonstracao das regras novas no banco local.
 * Apos rodar: abra http://localhost:8081 com a aplicacao no ar e siga o README no console do teste.
 */
@SpringBootTest
@ActiveProfiles("local")
class DemonstracaoRegrasIntegracaoTest {

    private static final String PREFIXO = "DEMO_REGRA_";

    @Autowired
    private AgendamentoService agendamentoService;

    @Autowired
    private PagamentoConsultaService pagamentoConsultaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepararBanco() {
        removerChecksStatusPagamentoH2();
        agendamentoRepository.findAll().stream()
                .filter(a -> a.getNomeCliente() != null && a.getNomeCliente().startsWith(PREFIXO))
                .forEach(a -> agendamentoRepository.deleteById(a.getId()));
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

    @Test
    void montarCenarioDemonstracaoRegras() {
        Usuario admin = usuarioRepository.findByLogin("admin").orElseThrow();
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario carol = usuarioRepository.findByLogin("carol").orElseThrow();
        carol.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);
        usuarioRepository.save(carol);
        Sala sala1 = agendamentoService.listarSalas().stream()
                .filter(s -> "Sala 1".equals(s.getNome()))
                .findFirst()
                .orElseThrow();
        Sala sala2 = agendamentoService.listarSalas().stream()
                .filter(s -> "Sala 2".equals(s.getNome()))
                .findFirst()
                .orElseThrow();

        LocalDate hoje = LocalDate.now();
        LocalDate amanha = proximoDiaUtil(hoje.plusDays(1));
        LocalDate depoisDeAmanha = proximoDiaUtil(hoje.plusDays(2));

        // 1) Realocacao: avulso pago (Carol, depois de amanha) — admin ja marca 1a consulta como PAGO
        Agendamento pago = salvarAvulso(carol, admin, sala1, depoisDeAmanha, LocalTime.of(10, 0), PREFIXO + "REALOCAR");
        if (!PagamentoStatus.PAGO.equals(pago.getStatusPagamento())) {
            pagamentoConsultaService.simularPagamento(pago.getId(), admin);
            pago = agendamentoRepository.findById(pago.getId()).orElseThrow();
        }

        assertTrue(agendamentoService.podeRealocar(pago, polyana));
        RelocacaoAgendamentoForm reloc = new RelocacaoAgendamentoForm();
        reloc.setSalaId(sala2.getId());
        reloc.setDataAtendimento(proximoDiaUtil(depoisDeAmanha.plusDays(1)));
        reloc.setHorarioAtendimento(LocalTime.of(14, 0));
        Agendamento realocado = agendamentoService.realocar(pago.getId(), reloc, polyana);

        assertEquals(PagamentoStatus.PAGO, realocado.getStatusPagamento());
        assertEquals(sala2.getId(), realocado.getSala().getId());
        LocalDate dataRealocada = proximoDiaUtil(depoisDeAmanha.plusDays(1));
        assertEquals(LocalDateTime.of(dataRealocada, LocalTime.of(14, 0)), realocado.getDataHoraInicio());

        // 2) D-1 liberacao: consulta HOJE sem pagamento (salvo direto no banco para evitar QR automatico)
        LocalTime horarioFuturoHoje = LocalTime.now().getHour() < 20
                ? LocalTime.of(20, 0)
                : LocalTime.of(21, 0);
        Agendamento semPagamento = salvarDiretoNoBanco(
                carol, sala1, hoje, horarioFuturoHoje, PREFIXO + "LIBERAR", PagamentoStatus.AGUARDANDO_PAGAMENTO
        );
        assertTrue(pagamentoConsultaService.passouPrazoPagamentoVespera(semPagamento));

        int liberados = pagamentoConsultaService.liberarVagasPorFaltaPagamento();
        assertTrue(liberados >= 1, "Deveria liberar ao menos 1 vaga");

        semPagamento = agendamentoRepository.findById(semPagamento.getId()).orElseThrow();
        assertEquals(PagamentoStatus.LIBERADO_FALTA_PAGAMENTO, semPagamento.getStatusPagamento());
        assertFalse(pagamentoConsultaService.ocupaVagaNaGrade(semPagamento));

        imprimirRoteiro(realocado.getId(), semPagamento.getId());
    }

    private Agendamento salvarAvulso(
            Usuario profissional,
            Usuario gestor,
            Sala sala,
            LocalDate data,
            LocalTime hora,
            String cliente
    ) {
        AgendamentoForm form = new AgendamentoForm();
        form.setProfissionalId(profissional.getId());
        form.setSalaId(sala.getId());
        form.setNomeCliente(cliente);
        form.setDataAtendimento(data);
        form.setHorarioAtendimento(hora);
        form.setRecorrencia("AVULSO");
        form.setValorProfissionalRecebe(new BigDecimal("200.00"));
        form.setValorClinicaCobra(new BigDecimal("35.00"));
        return agendamentoService.salvar(form, gestor);
    }

    private Agendamento salvarDiretoNoBanco(
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
        agendamento.setValorProfissionalRecebe(new BigDecimal("200.00"));
        agendamento.setValorClinicaCobra(new BigDecimal("35.00"));
        agendamento.setValorLiquidoProfissional(new BigDecimal("165.00"));
        agendamento.setValorPagamento(new BigDecimal("35.00"));
        agendamento.setIndicacaoDona(false);
        agendamento.setStatusPagamento(status);
        return agendamentoRepository.save(agendamento);
    }

    private static LocalDate proximoDiaUtil(LocalDate data) {
        LocalDate cursor = data;
        while (cursor.getDayOfWeek() == DayOfWeek.SUNDAY) {
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }

    private void imprimirRoteiro(Long idRealocado, Long idLiberado) {
        System.out.println();
        System.out.println("========== DEMONSTRACAO PRONTA NO BANCO LOCAL ==========");
        System.out.println("App: http://localhost:8081");
        System.out.println("Login Polyana: polyana / 297b  (ou Carol: carol / 297b)");
        System.out.println();
        System.out.println("REALOCACAO (pago):");
        System.out.println("  - Cliente: " + PREFIXO + "REALOCAR");
        System.out.println("  - URL: http://localhost:8081/agendamentos/" + idRealocado + "/realocar");
        System.out.println("  - Carol > Avulso > botao Realocar data");
        System.out.println();
        System.out.println("LIBERACAO D-1 (vaga livre na grade):");
        System.out.println("  - Cliente: " + PREFIXO + "LIBERAR");
        System.out.println("  - Status: LIBERADO_FALTA_PAGAMENTO (nao ocupa celula)");
        System.out.println("  - Grade Sala 1: http://localhost:8081/agendamentos/dashboard");
        System.out.println("  - Gestao financeira: http://localhost:8081/agendamentos/financeiro");
        System.out.println("==========================================================");
        System.out.println();
    }
}
