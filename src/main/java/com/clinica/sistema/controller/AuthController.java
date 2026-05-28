package com.clinica.sistema.controller;

import com.clinica.sistema.dto.LoginForm;
import com.clinica.sistema.dto.TrocarSenhaForm;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final AuthService authService;
    private final UsuarioService usuarioService;

    public AuthController(AuthService authService, UsuarioService usuarioService) {
        this.authService = authService;
        this.usuarioService = usuarioService;
    }

    @GetMapping({"/", "/login"})
    public String login(
            Model model,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(required = false) String erro,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String senhaAlterada
    ) {
        if (senhaAlterada != null) {
            encerrarSessaoDoUsuario(request, response);
            model.addAttribute("sucesso", "Senha alterada com sucesso. Entre com a nova senha.");
            model.addAttribute("loginForm", new LoginForm());
            return "login";
        }

        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/agendamentos/dashboard";
        }

        if (!model.containsAttribute("loginForm")) {
            model.addAttribute("loginForm", new LoginForm());
        }
        if (erro != null) {
            model.addAttribute("erro", "Login ou senha invalidos.");
        }
        if (logout != null) {
            model.addAttribute("sucesso", "Voce saiu do sistema com sucesso.");
        }
        return "login";
    }

    @PostMapping("/conta/trocar-senha")
    public String trocarSenha(
            @ModelAttribute TrocarSenhaForm trocarSenhaForm,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes
    ) {
        try {
            usuarioService.trocarSenha(trocarSenhaForm, authService.buscarUsuarioLogadoObrigatorio());
            HttpSession sessao = request.getSession(false);
            if (sessao != null) {
                sessao.invalidate();
            }
            SecurityContextHolder.clearContext();
            return "redirect:/login?senhaAlterada=1";
        } catch (RuntimeException e) {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            boolean senhaAtualIncorreta = "Senha atual incorreta.".equals(e.getMessage());
            redirectAttributes.addFlashAttribute("erroSenha", e.getMessage());
            redirectAttributes.addFlashAttribute("erroSenhaAtual", senhaAtualIncorreta);
            redirectAttributes.addFlashAttribute("trocarSenhaForm", trocarSenhaForm);
            if (authService.podeGerenciarEquipe(usuarioLogado)) {
                return "redirect:/agendamentos/central-profissionais";
            }
            return "redirect:/agendamentos/dashboard";
        }
    }

    private void encerrarSessaoDoUsuario(HttpServletRequest request, HttpServletResponse response) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
        logoutHandler.setInvalidateHttpSession(true);
        logoutHandler.setClearAuthentication(true);
        logoutHandler.logout(request, response, autenticacao);
        SecurityContextHolder.clearContext();

        HttpSession sessao = request.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("JSESSIONID".equals(cookie.getName())) {
                    Cookie invalida = new Cookie("JSESSIONID", "");
                    invalida.setPath(cookie.getPath() != null && !cookie.getPath().isBlank() ? cookie.getPath() : "/");
                    invalida.setMaxAge(0);
                    invalida.setHttpOnly(true);
                    response.addCookie(invalida);
                }
            }
        }
    }
}
