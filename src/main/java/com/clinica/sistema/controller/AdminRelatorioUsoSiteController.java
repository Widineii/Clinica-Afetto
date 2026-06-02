package com.clinica.sistema.controller;

import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.RelatorioUsoSitePdfService;
import com.clinica.sistema.service.RelatorioUsoSiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;

@Profile("local")
@Controller
@RequestMapping("/agendamentos/admin")
public class AdminRelatorioUsoSiteController {

    private static final Logger log = LoggerFactory.getLogger(AdminRelatorioUsoSiteController.class);

    private final AuthService authService;
    private final RelatorioUsoSiteService relatorioUsoSiteService;
    private final RelatorioUsoSitePdfService relatorioUsoSitePdfService;

    public AdminRelatorioUsoSiteController(
            AuthService authService,
            RelatorioUsoSiteService relatorioUsoSiteService,
            RelatorioUsoSitePdfService relatorioUsoSitePdfService
    ) {
        this.authService = authService;
        this.relatorioUsoSiteService = relatorioUsoSiteService;
        this.relatorioUsoSitePdfService = relatorioUsoSitePdfService;
    }

    @GetMapping("/relatorio-uso-site")
    public String relatorioUsoSite(Model model, RedirectAttributes redirectAttributes) {
        var usuario = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.isAdmin(usuario)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Somente o usuario administrador pode ver o relatorio de uso do site."
            );
            redirectAttributes.addFlashAttribute("erroContexto", "uso-site");
            return "redirect:/agendamentos/dashboard";
        }

        try {
            model.addAttribute("usuarioLogado", usuario);
            model.addAttribute("isAdmin", true);
            model.addAttribute("perfilLocal", true);
            model.addAttribute("versaoDownload", System.currentTimeMillis());
            model.addAttribute("relatorio", relatorioUsoSiteService.montarRelatorio());
            return "admin-relatorio-uso-site";
        } catch (RuntimeException e) {
            log.error("Falha ao carregar relatorio de uso do site", e);
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Nao foi possivel abrir o relatorio de uso do site. Tente novamente em alguns minutos."
            );
            redirectAttributes.addFlashAttribute("erroContexto", "uso-site");
            return "redirect:/agendamentos/dashboard";
        }
    }

    @GetMapping(value = "/relatorio-uso-site/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> baixarPdfRelatorioUsoSite() {
        var usuario = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.isAdmin(usuario)) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Acesso negado. Somente administrador.".getBytes(StandardCharsets.UTF_8));
        }

        try {
            var relatorio = relatorioUsoSiteService.montarRelatorio();
            byte[] pdf = relatorioUsoSitePdfService.gerarPdf(relatorio);
            if (pdf.length < 4 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F') {
                log.error("PDF do relatorio de uso do site invalido (tamanho={})", pdf.length);
                return ResponseEntity.internalServerError()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Erro ao gerar PDF. Reinicie o servidor local e tente de novo."
                                .getBytes(StandardCharsets.UTF_8));
            }
            String nomeArquivo = relatorioUsoSitePdfService.nomeArquivoPdf();
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(nomeArquivo, StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (RuntimeException e) {
            log.error("Falha ao gerar PDF do relatorio de uso do site", e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Erro ao gerar PDF: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
