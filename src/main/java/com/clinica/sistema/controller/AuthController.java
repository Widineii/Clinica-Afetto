package com.clinica.sistema.controller;

import com.clinica.sistema.dto.LoginForm;
import com.clinica.sistema.dto.CadastroContaPublicaForm;
import com.clinica.sistema.dto.AtualizarEmailProfissionalForm;
import com.clinica.sistema.dto.AtualizarTelefoneWhatsappForm;
import com.clinica.sistema.dto.TrocarSenhaForm;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.security.AcessoSalvoCookies;
import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.BoasVindasLoginService;
import com.clinica.sistema.service.LgpdConsentimentoService;
import com.clinica.sistema.service.PagamentoConsultaService;
import com.clinica.sistema.service.PendenciasDonaLoginService;
import com.clinica.sistema.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final UsuarioService usuarioService;
    private final PagamentoConsultaService pagamentoConsultaService;
    private final LgpdConsentimentoService lgpdConsentimentoService;
    private final BoasVindasLoginService boasVindasLoginService;
    private final PendenciasDonaLoginService pendenciasDonaLoginService;

    public AuthController(
            AuthService authService,
            UsuarioService usuarioService,
            PagamentoConsultaService pagamentoConsultaService,
            LgpdConsentimentoService lgpdConsentimentoService,
            BoasVindasLoginService boasVindasLoginService,
            PendenciasDonaLoginService pendenciasDonaLoginService
    ) {
        this.authService = authService;
        this.usuarioService = usuarioService;
        this.pagamentoConsultaService = pagamentoConsultaService;
        this.lgpdConsentimentoService = lgpdConsentimentoService;
        this.boasVindasLoginService = boasVindasLoginService;
        this.pendenciasDonaLoginService = pendenciasDonaLoginService;
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
        if (!model.containsAttribute("cadastroContaPublicaForm")) {
            model.addAttribute("cadastroContaPublicaForm", new CadastroContaPublicaForm());
        }
    }

    @GetMapping({"/", "/login"})
    public String login(
            Model model,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(required = false) String erro,
            @RequestParam(required = false) String pendente,
            @RequestParam(required = false) String contaCriada,
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
            garantirCadastroContaPublicaForm(model);
            return "login";
        }

        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            if (lgpdConsentimentoService.usuarioLogadoPrecisaConsentir()) {
                return "redirect:/conta/consentimento-lgpd";
            }
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
        if (pendente != null || contaCriada != null) {
            model.addAttribute("mostrarContaPendente", true);
        }
        if (logout != null) {
            model.addAttribute("sucesso", "Você saiu do sistema. Seus dados continuam neste aparelho — toque em Entrar.");
        }
        garantirCadastroContaPublicaForm(model);
        return "login";
    }

    private void garantirCadastroContaPublicaForm(Model model) {
        if (!model.containsAttribute("cadastroContaPublicaForm")) {
            model.addAttribute("cadastroContaPublicaForm", new CadastroContaPublicaForm());
        }
    }

    @PostMapping("/primeiro-acesso")
    public String solicitarContaPublica(
            @ModelAttribute CadastroContaPublicaForm cadastroContaPublicaForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            usuarioService.solicitarContaPublica(cadastroContaPublicaForm);
            return "redirect:/login?contaCriada=1";
        } catch (Exception e) {
            log.warn("Falha ao solicitar conta publica: {}", e.getMessage(), e);
            if (cadastroContaPublicaForm != null) {
                cadastroContaPublicaForm.setSenha(null);
                cadastroContaPublicaForm.setConfirmarSenha(null);
            }
            redirectAttributes.addFlashAttribute("erroCadastroConta", mensagemErroCadastroConta(e));
            redirectAttributes.addFlashAttribute("cadastroContaPublicaForm", cadastroContaPublicaForm);
            redirectAttributes.addFlashAttribute("abrirCadastroConta", true);
        }
        return "redirect:/login";
    }

    private String mensagemErroCadastroConta(Exception e) {
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            String mensagem = e.getMessage();
            if (!mensagem.toLowerCase().contains("exception")
                    && !mensagem.toLowerCase().contains("sql")
                    && !mensagem.toLowerCase().contains("jdbc")) {
                return mensagem;
            }
        }
        return "Nao foi possivel criar sua conta agora. Tente novamente em instantes.";
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

    @PostMapping(value = "/conta/perfil", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String atualizarPerfil(
            @ModelAttribute AtualizarTelefoneWhatsappForm atualizarTelefoneWhatsappForm,
            @RequestParam(name = "fotoPerfil", required = false) MultipartFile fotoPerfil,
            @RequestParam(name = "removerFoto", defaultValue = "false") boolean removerFoto,
            @RequestParam(name = "retorno", defaultValue = "/agendamentos/dashboard") String retorno,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            usuarioService.atualizarPerfilProfissional(
                    atualizarTelefoneWhatsappForm,
                    fotoPerfil,
                    removerFoto,
                    usuarioLogado
            );
            Usuario usuarioAtualizado = usuarioService.recarregarUsuario(usuarioLogado);
            usuarioService.sincronizarCadastroContatoPendenteNaSessao(session, usuarioAtualizado);
            redirectAttributes.addFlashAttribute("sucesso", "Perfil atualizado com sucesso.");
            if (usuarioService.precisaCadastrarContatoProfissional(usuarioAtualizado)) {
                redirectAttributes.addFlashAttribute("reabrirModalCadastroContato", true);
            }
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erroPerfil", e.getMessage());
            redirectAttributes.addFlashAttribute("erroContato", e.getMessage());
            redirectAttributes.addFlashAttribute("atualizarTelefoneWhatsappForm", atualizarTelefoneWhatsappForm);
            redirectAttributes.addFlashAttribute("reabrirModalEditarPerfil", true);
            redirectAttributes.addFlashAttribute("reabrirModalCadastroContato", true);
        }
        return "redirect:" + normalizarRetornoPerfil(retorno);
    }

    @PostMapping("/conta/contato-profissional")
    public String cadastrarContatoProfissional(
            @ModelAttribute AtualizarTelefoneWhatsappForm atualizarTelefoneWhatsappForm,
            @org.springframework.web.bind.annotation.RequestParam(name = "retorno", defaultValue = "/agendamentos/dashboard")
            String retorno,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            usuarioService.atualizarContatoProfissional(atualizarTelefoneWhatsappForm, usuarioLogado);
            usuarioService.dispensarCadastroContato(session);
            redirectAttributes.addFlashAttribute("sucesso", "Dados de contato salvos com sucesso.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erroContato", e.getMessage());
            redirectAttributes.addFlashAttribute("atualizarTelefoneWhatsappForm", atualizarTelefoneWhatsappForm);
            redirectAttributes.addFlashAttribute("reabrirModalCadastroContato", true);
        }
        return "redirect:" + normalizarRetornoPerfil(retorno);
    }

    @PostMapping("/conta/contato-profissional/pular")
    public String pularCadastroContatoProfissional(HttpSession session) {
        authService.buscarUsuarioLogadoObrigatorio();
        usuarioService.dispensarCadastroContato(session);
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/conta/boas-vindas-login/pular")
    public String pularBoasVindasLogin(
            @RequestParam(defaultValue = "false") boolean naoMostrarMaisHoje,
            @RequestParam(required = false) String periodicidade,
            HttpSession session
    ) {
        Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
        Usuario registro = usuarioService.recarregarUsuario(usuarioLogado);
        if (boasVindasLoginService.isPrimeiroLoginPendente(registro)) {
            if (boasVindasLoginService.exigeFormaPagamentoPrimeiroAcesso(registro)) {
                if (periodicidade == null || periodicidade.isBlank()) {
                    throw new RuntimeException("Selecione a forma de pagamento para continuar.");
                }
                usuarioService.confirmarPeriodicidadePrimeiroAcesso(
                        PeriodicidadePagamento.valueOf(periodicidade.trim()),
                        usuarioLogado
                );
            }
            boasVindasLoginService.registrarFechamentoBoasVindas(usuarioLogado, false);
        } else {
            boasVindasLoginService.registrarFechamentoBoasVindas(usuarioLogado, naoMostrarMaisHoje);
        }
        boasVindasLoginService.dispensarBoasVindasLogin(session);
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/conta/email-profissional")
    public String cadastrarEmailProfissional(
            @ModelAttribute AtualizarEmailProfissionalForm atualizarEmailProfissionalForm,
            @org.springframework.web.bind.annotation.RequestParam(name = "retorno", defaultValue = "/agendamentos/dashboard")
            String retorno,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            usuarioService.atualizarEmailNotificacao(atualizarEmailProfissionalForm, usuarioLogado);
            Usuario usuarioAtualizado = usuarioService.recarregarUsuario(usuarioLogado);
            usuarioService.sincronizarCadastroContatoPendenteNaSessao(session, usuarioAtualizado);
            redirectAttributes.addFlashAttribute("sucesso", "E-mail cadastrado com sucesso.");
            if (usuarioService.precisaCadastrarContatoProfissional(usuarioAtualizado)) {
                redirectAttributes.addFlashAttribute("reabrirModalCadastroContato", true);
            }
        } catch (RuntimeException e) {
            AtualizarTelefoneWhatsappForm form = new AtualizarTelefoneWhatsappForm();
            form.setEmail(atualizarEmailProfissionalForm != null ? atualizarEmailProfissionalForm.getEmail() : null);
            redirectAttributes.addFlashAttribute("erroContato", e.getMessage());
            redirectAttributes.addFlashAttribute("atualizarTelefoneWhatsappForm", form);
            redirectAttributes.addFlashAttribute("reabrirModalCadastroContato", true);
        }
        return "redirect:" + normalizarRetornoPerfil(retorno);
    }

    @PostMapping("/conta/email-profissional/pular")
    public String pularCadastroEmailProfissional(HttpSession session) {
        authService.buscarUsuarioLogadoObrigatorio();
        usuarioService.dispensarCadastroContato(session);
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/conta/telefone-whatsapp")
    public String cadastrarTelefoneWhatsapp(
            @ModelAttribute AtualizarTelefoneWhatsappForm atualizarTelefoneWhatsappForm,
            @org.springframework.web.bind.annotation.RequestParam(name = "retorno", defaultValue = "/agendamentos/dashboard")
            String retorno,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Usuario usuarioLogado = authService.buscarUsuarioLogadoObrigatorio();
            usuarioService.atualizarTelefoneWhatsapp(atualizarTelefoneWhatsappForm, usuarioLogado);
            Usuario usuarioAtualizado = usuarioService.recarregarUsuario(usuarioLogado);
            usuarioService.sincronizarCadastroContatoPendenteNaSessao(session, usuarioAtualizado);
            redirectAttributes.addFlashAttribute("sucesso", "Perfil atualizado com sucesso.");
            if (usuarioService.precisaCadastrarContatoProfissional(usuarioAtualizado)) {
                redirectAttributes.addFlashAttribute("reabrirModalCadastroContato", true);
            }
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erroContato", e.getMessage());
            redirectAttributes.addFlashAttribute("atualizarTelefoneWhatsappForm", atualizarTelefoneWhatsappForm);
            redirectAttributes.addFlashAttribute("reabrirModalCadastroContato", true);
        }
        return "redirect:" + normalizarRetornoPerfil(retorno);
    }

    private String normalizarRetornoPerfil(String retorno) {
        if (retorno == null || retorno.isBlank()) {
            return "/agendamentos/dashboard";
        }
        if (!retorno.startsWith("/agendamentos/")) {
            return "/agendamentos/dashboard";
        }
        return retorno;
    }

    @PostMapping("/conta/telefone-whatsapp/pular")
    public String pularCadastroTelefoneWhatsapp(HttpSession session) {
        authService.buscarUsuarioLogadoObrigatorio();
        usuarioService.dispensarCadastroContato(session);
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/conta/pendencias-pagamento/pular")
    public String pularLembretePendenciasPagamento(HttpSession session) {
        authService.buscarUsuarioLogadoObrigatorio();
        pagamentoConsultaService.dispensarLembretePendenciasPagamento(session);
        return "redirect:/agendamentos/dashboard";
    }

    @PostMapping("/conta/pendencias-dona/pular")
    public String pularLembretePendenciasDona(HttpSession session) {
        authService.buscarUsuarioLogadoObrigatorio();
        pendenciasDonaLoginService.dispensarLembretePendenciasDona(session);
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
