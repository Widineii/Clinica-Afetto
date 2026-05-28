package com.clinica.sistema.controller;

import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.UsoBancoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/agendamentos/admin")
public class AdminUsoBancoController {

    private static final Logger log = LoggerFactory.getLogger(AdminUsoBancoController.class);

    private final AuthService authService;
    private final UsoBancoService usoBancoService;

    public AdminUsoBancoController(AuthService authService, UsoBancoService usoBancoService) {
        this.authService = authService;
        this.usoBancoService = usoBancoService;
    }

    @GetMapping("/uso-banco")
    public String usoBanco(Model model, RedirectAttributes redirectAttributes) {
        var usuario = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.isAdmin(usuario)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Somente o usuario administrador pode ver o uso do banco."
            );
            redirectAttributes.addFlashAttribute("erroContexto", "uso-banco");
            return "redirect:/agendamentos/dashboard";
        }

        try {
            model.addAttribute("usuarioLogado", usuario);
            model.addAttribute("isAdmin", true);
            model.addAttribute("resumo", usoBancoService.montarResumo());
            return "admin-uso-banco";
        } catch (RuntimeException e) {
            log.error("Falha ao carregar painel de uso do banco", e);
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Nao foi possivel abrir o painel de uso do banco. Tente novamente em alguns minutos."
            );
            redirectAttributes.addFlashAttribute("erroContexto", "uso-banco");
            return "redirect:/agendamentos/dashboard";
        }
    }
}
