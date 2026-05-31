package com.clinica.sistema.controller;

import com.clinica.sistema.dto.GraficoJsonUtil;
import com.clinica.sistema.dto.RelatorioLinhaView;
import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.dto.RelatorioUsoSalaItem;
import com.clinica.sistema.dto.RelatorioUsoSalaProfissional;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        return carregarRelatorios(model, redirectAttributes, session);
    }

    @GetMapping("/mensal")
    public String relatorioMensal() {
        return "redirect:/agendamentos/relatorio/semanal";
    }

    private String carregarRelatorios(
            Model model,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!podeVerRelatorio(usuarioLogado)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Somente administracao ou dona da clinica pode ver o relatorio."
            );
            return "redirect:/agendamentos/dashboard";
        }

        try {
            relatorioMensalService.executarFechamentoAutomaticoSeDevido();

            RelatorioMensalUsoSalasView relatorioSemanal = relatorioSemanalService.montarRelatorioSemanalAtual();
            relatorioSemanalService.armazenarNaSessao(session, relatorioSemanal);

            model.addAttribute("usuarioLogado", usuarioLogado);
            model.addAttribute("isAdmin", authService.isAdmin(usuarioLogado));
            model.addAttribute("perfilLocal", isPerfilLocal());
            model.addAttribute("relatorioSemanal", relatorioSemanal);
            List<RelatorioLinhaView> linhasSemanal = montarLinhasRelatorio(relatorioSemanal);
            model.addAttribute("linhasSemanal", linhasSemanal);
            model.addAttribute("totalProfissionaisSemanal", relatorioSemanal.getProfissionais().size());
            model.addAttribute("graficoSalasSemanalJson", GraficoJsonUtil.serializarUsoSalasRelatorio(linhasSemanal));
            model.addAttribute("graficoProfissionaisSemanalJson", GraficoJsonUtil.serializarProfissionaisRelatorio(linhasSemanal));
            model.addAttribute("periodoLabel", relatorioSemanal.getMesReferenciaLabel());
            model.addAttribute("geradoEmSemanal", java.time.LocalDateTime.now());
            model.addAttribute("versaoDownload", System.currentTimeMillis());
            return "relatorio";
        } catch (RuntimeException e) {
            log.error("Falha ao carregar relatorio semanal", e);
            redirectAttributes.addFlashAttribute("erroContexto", "relatorio");
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Nao foi possivel abrir os relatorios. Tente novamente."
            );
            return "redirect:/agendamentos/dashboard";
        }
    }

    @GetMapping("/mensal/visualizar")
    public ResponseEntity<byte[]> visualizarPdfMensal() {
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/mensal/download")
    public ResponseEntity<byte[]> baixarPdfMensal() {
        return ResponseEntity.notFound().build();
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
