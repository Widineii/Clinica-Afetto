package com.clinica.sistema.controller;

import com.clinica.sistema.config.ArquivoSistemaGitHubProperties;
import com.clinica.sistema.exception.ArquivoSistemaIndisponivelException;
import com.clinica.sistema.service.ArquivoSistemaGitHubService;
import com.clinica.sistema.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/agendamentos/admin")
public class AdminArquivoSistemaController {

    private static final Logger log = LoggerFactory.getLogger(AdminArquivoSistemaController.class);

    private final AuthService authService;
    private final ArquivoSistemaGitHubService arquivoSistemaGitHubService;
    private final ArquivoSistemaGitHubProperties arquivoSistemaGitHubProperties;

    public AdminArquivoSistemaController(
            AuthService authService,
            ArquivoSistemaGitHubService arquivoSistemaGitHubService,
            ArquivoSistemaGitHubProperties arquivoSistemaGitHubProperties
    ) {
        this.authService = authService;
        this.arquivoSistemaGitHubService = arquivoSistemaGitHubService;
        this.arquivoSistemaGitHubProperties = arquivoSistemaGitHubProperties;
    }

    @GetMapping("/arquivo-sistema")
    public String arquivoSistema(
            @RequestParam(value = "caminho", required = false) String caminho,
            @RequestParam(value = "atualizar", defaultValue = "false") boolean atualizar,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        var usuario = authService.buscarUsuarioLogadoObrigatorio();
        if (!authService.podeAcessarArquivoSistema(usuario)) {
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Voce nao tem permissao para ver o arquivo do sistema."
            );
            redirectAttributes.addFlashAttribute("erroContexto", "arquivo-sistema");
            return "redirect:/agendamentos/dashboard";
        }

        try {
            model.addAttribute("usuarioLogado", usuario);
            model.addAttribute("isAdmin", authService.isAdmin(usuario));
            model.addAttribute("resumo", arquivoSistemaGitHubService.montarResumo(atualizar));
            model.addAttribute("atualizadoAgora", atualizar);

            if (caminho != null && !caminho.isBlank()) {
                String caminhoNormalizado = caminho.trim();
                arquivoSistemaGitHubService.validarCaminhoRelativo(caminhoNormalizado);
                model.addAttribute("caminhoSelecionado", caminhoNormalizado);
                model.addAttribute(
                        "urlGitHubArquivoSelecionado",
                        arquivoSistemaGitHubProperties.resolverUrlArquivoNoGitHub(caminhoNormalizado)
                );
                try {
                    model.addAttribute(
                            "conteudoArquivo",
                            arquivoSistemaGitHubService.buscarConteudoArquivo(caminhoNormalizado)
                    );
                } catch (ArquivoSistemaIndisponivelException e) {
                    model.addAttribute("erroArquivo", e.getMessage());
                }
            }

            return "admin-arquivo-sistema";
        } catch (ArquivoSistemaIndisponivelException e) {
            log.warn("Falha ao carregar arquivo do sistema: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContexto", "arquivo-sistema");
            return "redirect:/agendamentos/dashboard";
        } catch (RuntimeException e) {
            log.error("Falha ao carregar arquivo do sistema", e);
            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Nao foi possivel abrir o arquivo do sistema. Tente novamente em alguns minutos."
            );
            redirectAttributes.addFlashAttribute("erroContexto", "arquivo-sistema");
            return "redirect:/agendamentos/dashboard";
        }
    }
}
