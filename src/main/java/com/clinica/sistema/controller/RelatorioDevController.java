package com.clinica.sistema.controller;

import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.RelatorioMensalArquivadoRepository;
import com.clinica.sistema.service.AgendamentoService;
import com.clinica.sistema.service.RelatorioMensalService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Endpoints de apoio para testes locais do relatorio (nao disponivel em producao).
 */
@Profile("local")
@Controller
@RequestMapping("/agendamentos/relatorio/dev")
public class RelatorioDevController {

    private final RelatorioMensalService relatorioMensalService;
    private final AgendamentoService agendamentoService;
    private final AgendamentoRepository agendamentoRepository;
    private final RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository;

    public RelatorioDevController(
            RelatorioMensalService relatorioMensalService,
            AgendamentoService agendamentoService,
            AgendamentoRepository agendamentoRepository,
            RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository
    ) {
        this.relatorioMensalService = relatorioMensalService;
        this.agendamentoService = agendamentoService;
        this.agendamentoRepository = agendamentoRepository;
        this.relatorioMensalArquivadoRepository = relatorioMensalArquivadoRepository;
    }

    /** Remove todos os agendamentos e relatorios arquivados (somente local). */
    @PostMapping("/limpar-agenda")
    @Transactional
    @ResponseBody
    public String limparAgendaERelatorios() {
        long agendamentos = agendamentoRepository.count();
        long relatorios = relatorioMensalArquivadoRepository.count();
        agendamentoRepository.deleteAllInBatch();
        relatorioMensalArquivadoRepository.deleteAllInBatch();
        return "Agenda limpa: " + agendamentos + " agendamento(s) e " + relatorios + " relatorio(s) removidos.";
    }

    @PostMapping("/resetar-arquivo-mes-passado")
    @ResponseBody
    public String resetarArquivoMesPassado() {
        boolean removido = relatorioMensalService.removerArquivoMesPassadoSeExistir();
        if (removido) {
            return "Arquivo do mes passado removido. Crie agendamentos avulsos e abra o relatorio.";
        }
        return "Nao havia arquivo do mes passado. Pode criar agendamentos e abrir o relatorio.";
    }

    @PostMapping("/regenerar-pdf-mes-passado")
    @ResponseBody
    public String regenerarPdfMesPassado() {
        var mes = relatorioMensalService.mesPassadoReferencia();
        var arquivado = relatorioMensalService.buscarArquivado(mes);
        if (arquivado.isEmpty()) {
            return "Nao ha relatorio arquivado para " + mes + ". Abra a pagina do relatorio primeiro.";
        }
        byte[] pdf = relatorioMensalService.gerarPdfDoArquivado(arquivado.get());
        return "PDF gerado na hora para " + mes + " (" + pdf.length + " bytes, nao gravado no banco). Baixe com Ctrl+F5.";
    }

    @PostMapping("/semear-semana-atual")
    public String semearSemanaAtual(RedirectAttributes redirectAttributes) {
        int criados = agendamentoService.semearAvulsosSemanaAtualParaTesteRelatorio();
        redirectAttributes.addFlashAttribute(
                "sucesso",
                "Criados " + criados + " agendamentos de teste na semana atual. "
                        + "O item 'recente-menos-24h' nao deve aparecer no relatorio (regra 24h)."
        );
        return "redirect:/agendamentos/relatorio/semanal";
    }
}

