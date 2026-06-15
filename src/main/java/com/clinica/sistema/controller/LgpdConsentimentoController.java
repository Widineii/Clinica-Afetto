package com.clinica.sistema.controller;

import com.clinica.sistema.dto.ConsentimentoLgpdForm;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.LgpdConsentimentoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class LgpdConsentimentoController {

    private final AuthService authService;
    private final LgpdConsentimentoService lgpdConsentimentoService;

    public LgpdConsentimentoController(
            AuthService authService,
            LgpdConsentimentoService lgpdConsentimentoService
    ) {
        this.authService = authService;
        this.lgpdConsentimentoService = lgpdConsentimentoService;
    }

    @GetMapping("/conta/consentimento-lgpd")
    public String exibirConsentimento(Model model) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!lgpdConsentimentoService.usuarioPrecisaConsentir(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }
        if (!model.containsAttribute("consentimentoLgpdForm")) {
            model.addAttribute("consentimentoLgpdForm", new ConsentimentoLgpdForm());
        }
        model.addAttribute("usuarioLogado", usuarioLogado);
        return "consentimento-lgpd";
    }

    @PostMapping("/conta/consentimento-lgpd")
    public String confirmarConsentimento(
            @ModelAttribute ConsentimentoLgpdForm consentimentoLgpdForm,
            Model model
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!lgpdConsentimentoService.usuarioPrecisaConsentir(usuarioLogado)) {
            return "redirect:/agendamentos/dashboard";
        }
        if (!consentimentoLgpdForm.isAutorizado()) {
            model.addAttribute("erro", "Marque a caixa de autorizacao para continuar.");
            model.addAttribute("usuarioLogado", usuarioLogado);
            return "consentimento-lgpd";
        }
        lgpdConsentimentoService.registrarConsentimento(usuarioLogado);
        return "redirect:/agendamentos/dashboard";
    }
}
