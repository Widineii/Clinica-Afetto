package com.clinica.sistema.controller;

import com.clinica.sistema.service.FinanceiroDemoSeedService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Profile("local")
@Controller
@RequestMapping("/agendamentos/financeiro/dev")
public class FinanceiroDevController {

    private final FinanceiroDemoSeedService financeiroDemoSeedService;

    public FinanceiroDevController(FinanceiroDemoSeedService financeiroDemoSeedService) {
        this.financeiroDemoSeedService = financeiroDemoSeedService;
    }

    @PostMapping("/semear-pix-demo")
    public String semearPixDemo(RedirectAttributes redirectAttributes) {
        int criados = financeiroDemoSeedService.semearPixDemonstracaoMesAtual();
        redirectAttributes.addFlashAttribute(
                "sucesso",
                "Criados " + criados + " PIX de demonstracao (1 por profissional). Os graficos foram atualizados."
        );
        return "redirect:/agendamentos/financeiro";
    }

    @PostMapping("/limpar-pix-demo")
    public String limparPixDemo(RedirectAttributes redirectAttributes) {
        int removidos = financeiroDemoSeedService.limparPixDemonstracao();
        redirectAttributes.addFlashAttribute(
                "sucesso",
                "Removidos " + removidos + " PIX de demonstracao."
        );
        return "redirect:/agendamentos/financeiro";
    }
}
