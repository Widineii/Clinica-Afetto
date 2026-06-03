package com.clinica.sistema.controller;

import com.clinica.sistema.service.AuthService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/agendamentos/admin")
public class AdminRelatorioUsoSiteController {

    private final AuthService authService;

    public AdminRelatorioUsoSiteController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/relatorio-uso-site")
    public String relatorioUsoSite(RedirectAttributes redirectAttributes) {
        var usuario = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeVerRelatorioUsoSite(usuario)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Voce nao tem permissao para ver o relatorio de uso do site."
            );
            return "redirect:/agendamentos/dashboard";
        }
        return "redirect:/agendamentos/central-profissionais?aba=uso-site";
    }

    @GetMapping("/relatorio-uso-site/download")
    public String baixarPdfRelatorioUsoSiteLegado() {
        return "redirect:/agendamentos/central-profissionais?aba=uso-site";
    }
}
