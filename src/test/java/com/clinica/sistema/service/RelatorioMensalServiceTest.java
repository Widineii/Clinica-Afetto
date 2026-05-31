package com.clinica.sistema.service;

import com.clinica.sistema.dto.RelatorioMensalNotificacaoView;
import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.dto.RelatorioUsoSalaItem;
import com.clinica.sistema.dto.RelatorioUsoSalaProfissional;
import com.clinica.sistema.model.RelatorioMensalArquivado;
import com.clinica.sistema.repository.RelatorioMensalArquivadoRepository;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioMensalServiceTest {

    @Mock
    private AgendamentoService agendamentoService;

    @Mock
    private RelatorioMensalPdfService relatorioMensalPdfService;

    @Mock
    private RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository;

    private RelatorioMensalService relatorioMensalService;

    @BeforeEach
    void setUp() {
        relatorioMensalService = new RelatorioMensalService(
                agendamentoService,
                relatorioMensalPdfService,
                relatorioMensalArquivadoRepository,
                JsonMapper.builder().build()
        );
        ReflectionTestUtils.setField(relatorioMensalService, "diaFechamento", 3);
        ReflectionTestUtils.setField(relatorioMensalService, "diaRemocaoPdf", 15);
    }

    @Test
    void deveArquivarMesSemPdfNoBancoELimparAgendamentos() {
        YearMonth maio = YearMonth.of(2026, 5);
        RelatorioMensalUsoSalasView relatorio = relatorioExemplo(maio);

        when(relatorioMensalArquivadoRepository.existsByAnoAndMes(2026, 5)).thenReturn(false);
        when(agendamentoService.montarRelatorioMensalUsoSalas(maio)).thenReturn(relatorio);
        when(agendamentoService.limparAgendamentosNoPeriodo(
                maio.atDay(1).atStartOfDay(),
                maio.plusMonths(1).atDay(1).atStartOfDay()
        )).thenReturn(15L);
        when(relatorioMensalArquivadoRepository.save(any(RelatorioMensalArquivado.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<RelatorioMensalArquivado> resultado = relatorioMensalService.arquivarMesSeNecessario(maio);

        assertTrue(resultado.isPresent());
        assertEquals(7L, resultado.get().getTotalGeral());
        assertEquals(15L, resultado.get().getAgendamentosRemovidos());
        assertTrue(resultado.get().getPdf() == null || resultado.get().getPdf().length == 0);

        ArgumentCaptor<RelatorioMensalArquivado> captor = ArgumentCaptor.forClass(RelatorioMensalArquivado.class);
        verify(relatorioMensalArquivadoRepository).save(captor.capture());
        assertEquals(2026, captor.getValue().getAno());
        assertEquals(5, captor.getValue().getMes());
        assertTrue(captor.getValue().getDadosJson().contains("Julia"));
        verify(relatorioMensalPdfService, never()).gerarPdf(any());
    }

    @Test
    void notificacaoOcultaAntesDoDiaDeFechamento() {
        assertTrue(relatorioMensalService.avaliarNotificacaoMensal(YearMonth.now().atDay(2)).isEmpty());
    }

    @Test
    void notificacaoPendenteAPartirDoDiaDeFechamento() {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);
        when(relatorioMensalArquivadoRepository.existsByAnoAndMes(
                mesPassado.getYear(),
                mesPassado.getMonthValue()
        )).thenReturn(false);
        when(agendamentoService.contarAgendamentosNoMes(mesPassado)).thenReturn(5L);

        Optional<RelatorioMensalNotificacaoView> notificacao =
                relatorioMensalService.avaliarNotificacaoMensal(YearMonth.now().atDay(3));

        assertTrue(notificacao.isPresent());
        assertTrue(notificacao.get().isPendenteArquivamento());
        assertTrue(notificacao.get().getMensagemPainel().contains("sera gerado no dia"));
    }

    @Test
    void notificacaoOcultaSemAgendamentosNoMesPassado() {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);
        when(relatorioMensalArquivadoRepository.existsByAnoAndMes(
                mesPassado.getYear(),
                mesPassado.getMonthValue()
        )).thenReturn(false);
        when(agendamentoService.contarAgendamentosNoMes(mesPassado)).thenReturn(0L);

        assertTrue(relatorioMensalService.avaliarNotificacaoMensal(YearMonth.now().atDay(5)).isEmpty());
    }

    @Test
    void notificacaoProntaParaBaixarQuandoArquivado() {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);

        when(relatorioMensalArquivadoRepository.existsByAnoAndMes(
                mesPassado.getYear(),
                mesPassado.getMonthValue()
        )).thenReturn(true);
        when(relatorioMensalArquivadoRepository.existsComDadosJson(
                mesPassado.getYear(),
                mesPassado.getMonthValue()
        )).thenReturn(true);

        LocalDate diaNaJanela = mesPassado.plusMonths(1).atDay(5);
        Optional<RelatorioMensalNotificacaoView> notificacao =
                relatorioMensalService.avaliarNotificacaoMensal(diaNaJanela);

        assertTrue(notificacao.isPresent());
        assertFalse(notificacao.get().isPendenteArquivamento());
        assertTrue(notificacao.get().getMensagemPainel().contains("foi gerado no dia"));
    }

    @Test
    void bolinhaSomeDepoisDeBaixarPdfMensalNaSessao() {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);
        LocalDate diaNaJanela = mesPassado.plusMonths(1).atDay(5);
        when(relatorioMensalArquivadoRepository.existsByAnoAndMes(
                mesPassado.getYear(),
                mesPassado.getMonthValue()
        )).thenReturn(true);
        when(relatorioMensalArquivadoRepository.existsComDadosJson(
                mesPassado.getYear(),
                mesPassado.getMonthValue()
        )).thenReturn(true);

        jakarta.servlet.http.HttpSession session = org.mockito.Mockito.mock(jakarta.servlet.http.HttpSession.class);
        assertTrue(relatorioMensalService.avaliarNotificacaoMensal(diaNaJanela).isPresent());
        assertFalse(relatorioMensalService.notificacaoMensalJaFoiAtendida(session, mesPassado));

        relatorioMensalService.marcarPdfMensalBaixadoNaNotificacao(session, mesPassado);
        org.mockito.Mockito.verify(session).setAttribute(
                RelatorioMensalService.SESSAO_NOTIFICACAO_PDF_MENSAL_BAIXADO,
                RelatorioMensalService.chaveMesReferencia(mesPassado)
        );

        when(session.getAttribute(RelatorioMensalService.SESSAO_NOTIFICACAO_PDF_MENSAL_BAIXADO))
                .thenReturn(RelatorioMensalService.chaveMesReferencia(mesPassado));
        assertTrue(relatorioMensalService.notificacaoMensalJaFoiAtendida(session, mesPassado));

        when(relatorioMensalArquivadoRepository.findPdfNotificacaoBaixadoEmByAnoAndMes(
                mesPassado.getYear(),
                mesPassado.getMonthValue()
        )).thenReturn(Optional.of(java.time.LocalDateTime.now()));

        jakarta.servlet.http.HttpSession sessaoNova = org.mockito.Mockito.mock(jakarta.servlet.http.HttpSession.class);
        assertTrue(relatorioMensalService.notificacaoMensalJaFoiAtendida(sessaoNova, mesPassado));
    }

    @Test
    void notificacaoAtivaComJsonArquivadoSemPdfNoBanco() {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);
        LocalDate diaNaJanela = mesPassado.plusMonths(1).atDay(5);
        when(relatorioMensalArquivadoRepository.existsByAnoAndMes(
                mesPassado.getYear(),
                mesPassado.getMonthValue()
        )).thenReturn(true);
        when(relatorioMensalArquivadoRepository.existsComDadosJson(
                mesPassado.getYear(),
                mesPassado.getMonthValue()
        )).thenReturn(true);

        assertTrue(relatorioMensalService.avaliarNotificacaoMensal(diaNaJanela).isPresent());
    }

    @Test
    void deveGerarPdfNoDownloadSemSalvarNoBanco() {
        YearMonth abril = YearMonth.of(2026, 4);
        RelatorioMensalArquivado arquivado = new RelatorioMensalArquivado();
        arquivado.setAno(2026);
        arquivado.setMes(4);
        arquivado.setDadosJson(
                "{\"anoReferencia\":2026,\"mesReferencia\":4,\"mesReferenciaLabel\":\"Abril de 2026\","
                        + "\"totalGeral\":7,\"profissionais\":[]}"
        );
        arquivado.setPdf(new byte[] {10, 20, 30});

        when(relatorioMensalPdfService.gerarPdf(any())).thenReturn(new byte[] {9, 9, 9});

        byte[] pdf = relatorioMensalService.gerarPdfDoArquivado(arquivado);

        assertEquals(3, pdf.length);
        assertEquals(9, pdf[0]);
        verify(relatorioMensalPdfService).gerarPdf(any());
        verify(relatorioMensalArquivadoRepository, never()).save(any());
    }

    @Test
    void naoDeveArquivarMesDuasVezesMasDeveLimparAvulsosPendentes() {
        YearMonth maio = YearMonth.of(2026, 5);
        RelatorioMensalArquivado existente = new RelatorioMensalArquivado();
        existente.setAno(2026);
        existente.setMes(5);
        existente.setAgendamentosRemovidos(10L);
        existente.setPdf(new byte[] {1, 2});
        existente.setDadosJson(
                "{\"anoReferencia\":2026,\"mesReferencia\":5,\"mesReferenciaLabel\":\"Maio de 2026\","
                        + "\"totalGeral\":7,\"profissionais\":[{\"profissionalNome\":\"Julia\","
                        + "\"totalHorarios\":7,\"salas\":[{\"salaNome\":\"Sala 1\",\"quantidade\":7}]}]}"
        );

        when(relatorioMensalArquivadoRepository.existsByAnoAndMes(2026, 5)).thenReturn(true);
        when(relatorioMensalArquivadoRepository.findByAnoAndMes(2026, 5)).thenReturn(Optional.of(existente));
        when(agendamentoService.limparAgendamentosNoPeriodo(
                maio.atDay(1).atStartOfDay(),
                maio.plusMonths(1).atDay(1).atStartOfDay()
        )).thenReturn(3L);
        when(relatorioMensalArquivadoRepository.save(any(RelatorioMensalArquivado.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        relatorioMensalService.arquivarMesSeNecessario(maio);

        verify(agendamentoService, never()).montarRelatorioMensalUsoSalas(any());
        verify(agendamentoService).limparAgendamentosNoPeriodo(
                maio.atDay(1).atStartOfDay(),
                maio.plusMonths(1).atDay(1).atStartOfDay()
        );
        verify(relatorioMensalPdfService, never()).gerarPdf(any());
        verify(relatorioMensalArquivadoRepository).save(existente);
        assertEquals(13L, existente.getAgendamentosRemovidos());
    }

    private RelatorioMensalUsoSalasView relatorioExemplo(YearMonth mes) {
        RelatorioUsoSalaItem sala1 = new RelatorioUsoSalaItem();
        sala1.setSalaNome("Sala 1");
        sala1.setQuantidade(7);

        RelatorioUsoSalaProfissional julia = new RelatorioUsoSalaProfissional();
        julia.setProfissionalNome("Julia");
        julia.getSalas().add(sala1);
        julia.setTotalHorarios(7);

        RelatorioMensalUsoSalasView relatorio = new RelatorioMensalUsoSalasView();
        relatorio.setAnoReferencia(mes.getYear());
        relatorio.setMesReferencia(mes.getMonthValue());
        relatorio.setMesReferenciaLabel("Maio de 2026");
        relatorio.getProfissionais().add(julia);
        relatorio.setTotalGeral(7);
        return relatorio;
    }
}
