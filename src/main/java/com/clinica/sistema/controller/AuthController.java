package com.clinica.sistema.controller;

import com.clinica.sistema.dto.LoginForm;
import com.clinica.sistema.dto.AtualizarTelefoneWhatsappForm;
import com.clinica.sistema.dto.TrocarSenhaForm;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.security.AcessoSalvoCookies;
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

    @ModelAttribute
    public void atributosPadraoLogin(Model model) {
        if (!model.containsAttribute("loginPreenchido")) {
            model.addAttribute("loginPreenchido", false);
        }
        if (!model.containsAttribute("senhaPreenchida")) {
            model.addAttribute("senhaPreenchida", false);
        }
        if (!model.containsAttribute("acessoProntoParaEntrar")) {
            model.addAttribute("acessoProntoParaEntrar", false);
        }
        if (!model.containsAttribute("lembrarAcessoMarcado")) {
            model.addAttribute("lembrarAcessoMarcado", false);
        }
        if (!model.containsAttribute("loginForm")) {
            model.addAttribute("loginForm", new LoginForm());
        }
    }

    @GetMapping({"/", "/login"})
    public String login(
            Model model,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(required = false) String erro,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String senhaAlterada,
            @org.springframework.web.bind.annotation.CookieValue(name = AcessoSalvoCookies.COOKIE_LOGIN, required = false) String loginSalvo,
            @org.springframework.web.bind.annotation.CookieValue(name = AcessoSalvoCookies.COOKIE_SENHA, required = false) String senhaSalvaCodificada
    ) {
        if (senhaAlterada != null) {
            AcessoSalvoCookies.removerTudo(response, request);
            model.addAttribute("sucesso", "Senha alterada com sucesso. Entre com a nova senha.");
            model.addAttribute("loginForm", new LoginForm());
            model.addAttribute("loginPreenchido", false);
            model.addAttribute("senhaPreenchida", false);
            model.addAttribute("acessoProntoParaEntrar", false);
            model.addAttribute("lembrarAcessoMarcado", false);
            return "login";
        }

        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/agendamentos/dashboard";
        }

        LoginForm form = new LoginForm();
        if (loginSalvo != null && !loginSalvo.isBlank()) {
            form.setLogin(loginSalvo.trim());
        }
        String senhaSalva = AcessoSalvoCookies.decodificarValor(senhaSalvaCodificada);
        if (!senhaSalva.isBlank()) {
            form.setSenha(senhaSalva);
        }

        model.addAttribute("loginForm", form);

        boolean loginPreenchido = form.getLogin() != null && !form.getLogin().isBlank();
        boolean senhaPreenchida = form.getSenha() != null && !form.getSenha().isBlank();
        boolean acessoProntoParaEntrar = loginPreenchido && senhaPreenchida;

        model.addAttribute("loginPreenchido", loginPreenchido);
        model.addAttribute("senhaPreenchida", senhaPreenchida);
        model.addAttribute("acessoProntoParaEntrar", acessoProntoParaEntrar);
        model.addAttribute("lembrarAcessoMarcado", false);

        if (erro != null) {
            model.addAttribute("erro", "Login ou senha inválidos.");
        }
        if (logout != null) {
            model.addAttribute("sucesso", "Você saiu do sistema. Seus dados continuam neste aparelho — toque em Entrar.");
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
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            if (!authService.podeTrocarPropriaSenha(usuarioLogado)) {
                throw new RuntimeException("Troca de senha nao disponivel para este usuario.");
            }
            usuarioService.trocarSenha(trocarSenhaForm, usuarioLogado);
            HttpSession sessao = request.getSession(false);
            if (sessao != null) {
                sessao.invalidate();
            }
            SecurityContextHolder.clearContext();
            return "redirect:/login?senhaAlterada=1";
        } catch (RuntimeException e) {
            boolean senhaAtualIncorreta = "Senha atual incorreta.".equals(e.getMessage());
            redirectAttributes.addFlashAttribute("erroSenha", e.getMessage());
            redirectAttributes.addFlashAttribute("erroSenhaAtual", senhaAtualIncorreta);
            redirectAttributes.addFlashAttribute("trocarSenhaForm", trocarSenhaForm);
            redirectAttributes.addFlashAttribute("reabrirModalTrocarSenha", true);
            return "redirect:/agendamentos/dashboard";
        }
    }

    @PostMapping("/conta/telefone-whatsapp")
    public String cadastrarTelefoneWhatsapp(
            @ModelAttribute AtualizarTelefoneWhatsappForm atualizarTelefoneWhatsappForm,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            usuarioService.atualizarTelefoneWhatsapp(atualizarTelefoneWhatsappForm, usuarioLogado);
            usuarioService.dispensarCadastroTelefoneWhatsapp(session);
            redirectAttributes.addFlashAttribute("sucesso", "WhatsApp cadastrado com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erroWhatsapp", e.getMessage());
            redirectAttributes.addFlashAttribute("atualizarTelefoneWhatsappForm", atualizarTelefoneWhatsappForm);
            redirectAttributes.addFlashAttribute("reabrirModalTelefoneWhatsapp", true);
        }
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/conta/telefone-whatsapp/pular")
    public String pularCadastroTelefoneWhatsapp(HttpSession session) {
        authService.buscarUsuarioLogadoObrigatorio();
        usuarioService.dispensarCadastroTelefoneWhatsapp(session);
        return "redirect:/agendamentos/dashboard";
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
