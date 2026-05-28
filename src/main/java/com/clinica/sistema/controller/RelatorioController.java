package com.clinica.sistema.controller;

import com.clinica.sistema.dto.RelatorioHistoricoResumo;
import com.clinica.sistema.dto.RelatorioLinhaView;
import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.dto.RelatorioUsoSalaItem;
import com.clinica.sistema.dto.RelatorioUsoSalaProfissional;
import com.clinica.sistema.model.RelatorioMensalArquivado;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.RelatorioMensalService;
import com.clinica.sistema.service.RelatorioSemanalService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/agendamentos/relatorio")
public class RelatorioController {

    private static final Logger log = LoggerFactory.getLogger(RelatorioController.class);

    private final RelatorioMensalService relatorioMensalService;
    private final RelatorioSemanalService relatorioSemanalService;
    private final AuthService authService;
    private final Environment environment;

    public RelatorioController(
            RelatorioMensalService relatorioMensalService,
            RelatorioSemanalService relatorioSemanalService,
            AuthService authService,
            Environment environment
    ) {
        this.relatorioMensalService = relatorioMensalService;
        this.relatorioSemanalService = relatorioSemanalService;
        this.authService = authService;
        this.environment = environment;
    }

    private boolean isPerfilLocal() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }

    @GetMapping
    public String relatorioPrincipal() {
        return "redirect:/agendamentos/relatorio/semanal";
    }

    @GetMapping("/semanal")
    public String relatorioSemanal(
            Model model,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        return carregarRelatorios(model, redirectAttributes, session, "semanal", false);
    }

    @GetMapping("/mensal")
    public String relatorioMensal(
            Model model,
            RedirectAttributes redirectAttributes,
            HttpSession session,
            @RequestParam(required = false, defaultValue = "false") boolean viaNotificacao
    ) {
        return carregarRelatorios(model, redirectAttributes, session, "mensal", viaNotificacao);
    }

    private String carregarRelatorios(
            Model model,
            RedirectAttributes redirectAttributes,
            HttpSession session,
            String aba,
            boolean viaNotificacao
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!podeVerRelatorio(usuarioLogado)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Somente administracao ou dona da clinica pode ver o relatorio."
            );
            return "redirect:/agendamentos/dashboard";
        }

        String abaAtiva = "mensal".equalsIgnoreCase(aba) ? "mensal" : "semanal";

        try {
            RelatorioMensalUsoSalasView relatorioSemanal = relatorioSemanalService.montarRelatorioSemanalAtual();
            relatorioSemanalService.armazenarNaSessao(session, relatorioSemanal);

            model.addAttribute("usuarioLogado", usuarioLogado);
            model.addAttribute("isAdmin", authService.isAdmin(usuarioLogado));
            model.addAttribute("perfilLocal", isPerfilLocal());
            model.addAttribute("abaAtiva", abaAtiva);
            model.addAttribute("relatorioSemanal", relatorioSemanal);
            model.addAttribute("linhasSemanal", montarLinhasRelatorio(relatorioSemanal));
            model.addAttribute("totalProfissionaisSemanal", relatorioSemanal.getProfissionais().size());
            model.addAttribute("periodoLabel", relatorioSemanal.getMesReferenciaLabel());
            model.addAttribute("geradoEmSemanal", java.time.LocalDateTime.now());
            model.addAttribute("versaoDownload", System.currentTimeMillis());

            popularDadosMensal(model, session, viaNotificacao);
            return "relatorio";
        } catch (RuntimeException e) {
            log.error("Falha ao carregar relatorios (aba={})", abaAtiva, e);
            redirectAttributes.addFlashAttribute("erroContexto", "relatorio");
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Nao foi possivel abrir os relatorios. Tente novamente."
            );
            return "redirect:/agendamentos/dashboard";
        }
    }

    private void popularDadosMensal(Model model, HttpSession session, boolean viaNotificacao) {
        YearMonth mesPassado = relatorioMensalService.mesPassadoReferencia();
        boolean fechamentoNestaVisita = false;
        int diaFechamento = 3;

        model.addAttribute("mensalCarregado", false);
        model.addAttribute("podeBaixarPdf", false);
        model.addAttribute("exibirPdfInline", false);
        model.addAttribute("relatorioGeradoAguardando", false);
        model.addAttribute("aguardandoDia3", !relatorioMensalService.podeExecutarFechamentoAutomatico());
        model.addAttribute("diaFechamento", diaFechamento);
        model.addAttribute("mesAtualLabel", relatorioMensalService.formatarMesReferencia(YearMonth.now()));
        model.addAttribute("mesPassadoLabel", relatorioMensalService.formatarMesReferencia(mesPassado));
        model.addAttribute("historico", Collections.emptyList());

        try {
            fechamentoNestaVisita = relatorioMensalService.executarFechamentoAutomaticoSeDevido();

            RelatorioMensalUsoSalasView relatorioMensal =
                    relatorioMensalService.carregarRelatorioParaExibicao(mesPassado);
            List<RelatorioHistoricoResumo> historico;
            try {
                historico = relatorioMensalService.listarHistoricoResumo();
            } catch (RuntimeException e) {
                log.warn("Historico de relatorios indisponivel; continuando sem lista.", e);
                historico = Collections.emptyList();
            }

            boolean aguardandoDia3 = !relatorioMensalService.podeExecutarFechamentoAutomatico();
            boolean relatorioArquivado = relatorioMensalService.temDadosArquivados(mesPassado);
            boolean relatorioVisivel = relatorioMensalService.relatorioMensalVisivelNaTela(mesPassado);
            boolean relatorioSaiuDaTela = relatorioMensalService.relatorioSaiuDaTela(mesPassado);
            var cabecalhoArquivado = relatorioMensalService.buscarCabecalhoArquivado(mesPassado);

            boolean podeBaixarPdf = relatorioVisivel;
            boolean relatorioGeradoAguardando = relatorioVisivel;
            boolean pdfJaBaixado = relatorioMensalService.notificacaoMensalJaFoiAtendida(session, mesPassado);

            model.addAttribute("mensalCarregado", true);
            model.addAttribute("relatorioMensal", relatorioMensal);
            model.addAttribute("mesPassadoLabel", relatorioMensal.getMesReferenciaLabel());
            model.addAttribute("relatorioArquivado", relatorioArquivado);
            model.addAttribute("podeBaixarPdf", podeBaixarPdf);
            model.addAttribute("exibirPdfInline", relatorioVisivel);
            model.addAttribute("relatorioGeradoAguardando", relatorioGeradoAguardando);
            model.addAttribute("pdfJaBaixado", pdfJaBaixado);
            model.addAttribute("relatorioSaiuDaTela", relatorioSaiuDaTela);
            model.addAttribute("diaRemocaoPdf", relatorioMensalService.getDiaRemocaoPdf());
            model.addAttribute("aguardandoDia3", aguardandoDia3);
            model.addAttribute("aguardandoProcessamentoAutomatico",
                    relatorioMensalService.podeExecutarFechamentoAutomatico() && !relatorioArquivado);
            model.addAttribute("historico", historico);

            cabecalhoArquivado.ifPresent(cabecalho -> {
                model.addAttribute("geradoEmMensal", cabecalho.getGeradoEm());
                model.addAttribute("agendamentosRemovidos", cabecalho.getAgendamentosRemovidos());
            });

            if (viaNotificacao && relatorioGeradoAguardando) {
                model.addAttribute(
                        "sucessoNotificacao",
                        "Relatorio do mes passado (" + relatorioMensal.getMesReferenciaLabel() + ") gerado no dia "
                                + diaFechamento + ". Veja o PDF na aba Mensal."
                );
            } else if (viaNotificacao && !aguardandoDia3 && fechamentoNestaVisita) {
                model.addAttribute(
                        "sucessoNotificacao",
                        "Relatorio de " + relatorioMensal.getMesReferenciaLabel()
                                + " gerado agora. Veja o PDF na aba Mensal."
                );
            } else if (viaNotificacao) {
                model.addAttribute(
                        "sucessoNotificacao",
                        "Abrindo o relatorio do mes passado (" + relatorioMensal.getMesReferenciaLabel() + ")."
                );
            }
        } catch (RuntimeException e) {
            log.error("Falha ao carregar relatorio mensal de {}", mesPassado, e);
            model.addAttribute(
                    "mensalErro",
                    !relatorioMensalService.podeExecutarFechamentoAutomatico()
                            ? relatorioMensalService.mensagemRelatorioDisponivelAposDia3()
                            : "Nao foi possivel carregar o relatorio de "
                                    + relatorioMensalService.formatarMesReferencia(mesPassado)
                                    + ". Tente novamente em alguns minutos."
            );
        }
    }

    @GetMapping("/mensal/visualizar")
    public ResponseEntity<byte[]> visualizarPdfMensal(
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!podeVerRelatorio(usuarioLogado)) {
            return ResponseEntity.status(403).build();
        }

        YearMonth mesReferencia = ano != null && mes != null
                ? YearMonth.of(ano, mes)
                : relatorioMensalService.mesPassadoReferencia();

        if (!relatorioMensalService.podeExportarPdf(mesReferencia)) {
            return ResponseEntity.notFound().build();
        }

        byte[] pdf;
        try {
            pdf = relatorioMensalService.obterPdfParaDownload(mesReferencia);
        } catch (RuntimeException e) {
            log.error("Falha ao gerar PDF para visualizacao {}", mesReferencia, e);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/mensal/download")
    public ResponseEntity<byte[]> baixarPdf(
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes,
            HttpSession session
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!podeVerRelatorio(usuarioLogado)) {
            return ResponseEntity.status(403).build();
        }

        YearMonth mesReferencia = ano != null && mes != null
                ? YearMonth.of(ano, mes)
                : relatorioMensalService.mesPassadoReferencia();

        if (!relatorioMensalService.podeExportarPdf(mesReferencia)) {
            return ResponseEntity.notFound().build();
        }

        byte[] pdf;
        try {
            pdf = relatorioMensalService.obterPdfParaDownload(mesReferencia);
        } catch (RuntimeException e) {
            log.error("Falha ao gerar PDF do relatorio {}", mesReferencia, e);
            return ResponseEntity.notFound().build();
        }
        String nomeArquivo = relatorioMensalService.nomeArquivoPdf(mesReferencia);

        if (mesReferencia.equals(relatorioMensalService.mesPassadoReferencia())) {
            relatorioMensalService.executarFechamentoAutomaticoSeDevido();
            relatorioMensalService.garantirRemocaoAvulsosDoMesArquivado(mesReferencia);
            relatorioMensalService.marcarPdfMensalBaixadoNaNotificacao(session, mesReferencia);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/semanal/download")
    public ResponseEntity<byte[]> baixarPdfSemanal(HttpSession session) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!podeVerRelatorio(usuarioLogado)) {
            return ResponseEntity.status(403).build();
        }

        if (relatorioSemanalService.obterDaSessao(session).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            RelatorioMensalUsoSalasView relatorio = relatorioSemanalService.obterDaSessao(session).get();
            byte[] pdf = relatorioSemanalService.gerarPdfDaSessao(session);
            String nomeArquivo = relatorioSemanalService.nomeArquivoPdf(relatorio);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (RuntimeException e) {
            log.error("Falha ao gerar PDF do relatorio semanal", e);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/semanal/limpar")
    public ResponseEntity<Void> limparRelatorioSemanal(HttpSession session) {
        relatorioSemanalService.limparSessao(session);
        return ResponseEntity.noContent().build();
    }

    private boolean podeVerRelatorio(Usuario usuario) {
        return authService.isAdmin(usuario) || authService.isDonaClinica(usuario);
    }

    private List<RelatorioLinhaView> montarLinhasRelatorio(RelatorioMensalUsoSalasView relatorio) {
        return relatorio.getProfissionais().stream()
                .map(this::paraLinhaView)
                .toList();
    }

    private RelatorioLinhaView paraLinhaView(RelatorioUsoSalaProfissional profissional) {
        Map<String, Long> porSala = new HashMap<>();
        for (RelatorioUsoSalaItem item : profissional.getSalas()) {
            porSala.put(item.getSalaNome(), item.getQuantidade());
        }

        RelatorioLinhaView linha = new RelatorioLinhaView();
        linha.setProfissionalNome(profissional.getProfissionalNome());
        linha.setTotalHorarios(profissional.getTotalHorarios());
        linha.setSala1(porSala.getOrDefault("Sala 1", 0L));
        linha.setSala2(porSala.getOrDefault("Sala 2", 0L));
        linha.setSala3(porSala.getOrDefault("Sala 3", 0L));
        linha.setSala4(porSala.getOrDefault("Sala 4", 0L));
        return linha;
    }
}
