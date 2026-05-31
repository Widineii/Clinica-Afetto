package com.clinica.sistema.controller;

import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.RelatorioProfissionalDemoSeedService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;

@Profile("local")
@Controller
@RequestMapping("/agendamentos/meu-relatorio/dev")
public class RelatorioProfissionalDevController {

    private final RelatorioProfissionalDemoSeedService demoSeedService;
    private final AuthService authService;

    public RelatorioProfissionalDevController(
            RelatorioProfissionalDemoSeedService demoSeedService,
            AuthService authService
    ) {
        this.demoSeedService = demoSeedService;
        this.authService = authService;
    }

    @PostMapping("/semear-demo")
    public String semearDemo(
            RedirectAttributes redirectAttributes,
            @RequestParam(required = false) String mesAno,
            @RequestParam(required = false) String sala
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeVerRelatorioProprio(usuarioLogado)) {
            redirectAttributes.addFlashAttribute("erro", "Relatorio individual disponivel apenas para profissionais.");
            return "redirect:/agendamentos/dashboard";
        }

        int criados = demoSeedService.semearDemonstracaoProfissional(usuarioLogado);
        redirectAttributes.addFlashAttribute(
                "sucesso",
                "Criados " + criados + " atendimentos de demonstracao no mes atual (Salas 1, 2 e 3)."
        );
        return redirectComFiltros(mesAno, sala);
    }

    @PostMapping("/limpar-demo")
    public String limparDemo(
            RedirectAttributes redirectAttributes,
            @RequestParam(required = false) String mesAno,
            @RequestParam(required = false) String sala
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeVerRelatorioProprio(usuarioLogado)) {
            redirectAttributes.addFlashAttribute("erro", "Relatorio individual disponivel apenas para profissionais.");
            return "redirect:/agendamentos/dashboard";
        }

        int removidos = demoSeedService.limparDemonstracaoProfissional(usuarioLogado);
        redirectAttributes.addFlashAttribute(
                "sucesso",
                "Removidos " + removidos + " atendimentos de demonstracao do seu relatorio."
        );
        return redirectComFiltros(mesAno, sala);
    }

    private String redirectComFiltros(String mesAno, String sala) {
        String mes = mesAno != null && !mesAno.isBlank() ? mesAno : YearMonth.now().toString();
        StringBuilder destino = new StringBuilder("redirect:/agendamentos/meu-relatorio?mesAno=").append(mes);
        if (sala != null && !sala.isBlank()) {
            destino.append("&sala=").append(sala);
        }
        return destino.toString();
    }
}
