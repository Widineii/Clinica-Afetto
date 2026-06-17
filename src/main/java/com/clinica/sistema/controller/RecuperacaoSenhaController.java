package com.clinica.sistema.controller;

import com.clinica.sistema.dto.RedefinirSenhaCodigoForm;
import com.clinica.sistema.dto.SolicitarCodigoSenhaForm;
import com.clinica.sistema.service.RecuperacaoSenhaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RecuperacaoSenhaController {

    private final RecuperacaoSenhaService recuperacaoSenhaService;

    public RecuperacaoSenhaController(RecuperacaoSenhaService recuperacaoSenhaService) {
        this.recuperacaoSenhaService = recuperacaoSenhaService;
    }

    @GetMapping("/senha/esqueci")
    public String esqueciSenha(Model model) {
        if (!recuperacaoSenhaService.recuperacaoHabilitada()) {
            return "redirect:/login";
        }
        if (!model.containsAttribute("solicitarCodigoSenhaForm")) {
            model.addAttribute("solicitarCodigoSenhaForm", new SolicitarCodigoSenhaForm());
        }
        return "senha-esqueci";
    }

    @PostMapping("/senha/esqueci")
    public String solicitarCodigo(
            @ModelAttribute SolicitarCodigoSenhaForm solicitarCodigoSenhaForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            recuperacaoSenhaService.solicitarCodigo(solicitarCodigoSenhaForm);
            redirectAttributes.addFlashAttribute("sucesso", RecuperacaoSenhaService.MSG_SOLICITACAO_GENERICO);
            redirectAttributes.addFlashAttribute("loginRecuperacao", solicitarCodigoSenhaForm.getLogin());
            return "redirect:/senha/codigo";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("solicitarCodigoSenhaForm", solicitarCodigoSenhaForm);
            return "redirect:/senha/esqueci";
        }
    }

    @GetMapping("/senha/codigo")
    public String informarCodigo(
            @ModelAttribute RedefinirSenhaCodigoForm redefinirSenhaCodigoForm,
            Model model
    ) {
        if (!recuperacaoSenhaService.recuperacaoHabilitada()) {
            return "redirect:/login";
        }
        if ((redefinirSenhaCodigoForm.getLogin() == null || redefinirSenhaCodigoForm.getLogin().isBlank())
                && model.containsAttribute("loginRecuperacao")) {
            Object loginFlash = model.getAttribute("loginRecuperacao");
            if (loginFlash instanceof String loginSalvo) {
                redefinirSenhaCodigoForm.setLogin(loginSalvo.trim());
            }
        }
        return "senha-codigo";
    }

    @PostMapping("/senha/codigo")
    public String redefinirSenha(
            @ModelAttribute RedefinirSenhaCodigoForm redefinirSenhaCodigoForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            recuperacaoSenhaService.redefinirComCodigo(redefinirSenhaCodigoForm);
            return "redirect:/login?senhaAlterada=1";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("redefinirSenhaCodigoForm", redefinirSenhaCodigoForm);
            return "redirect:/senha/codigo";
        }
    }
}
