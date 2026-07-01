package com.clinica.sistema.security;



import com.clinica.sistema.config.SegurancaProperties;

import com.clinica.sistema.model.Usuario;

import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.service.BoasVindasLoginService;
import com.clinica.sistema.service.LgpdConsentimentoService;
import com.clinica.sistema.service.PagamentoConsultaService;
import com.clinica.sistema.service.PendenciasDonaLoginService;
import com.clinica.sistema.service.UsuarioService;

import jakarta.servlet.http.HttpServletRequest;

import jakarta.servlet.http.HttpServletResponse;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;

import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;



@Component

public class ClinicaAuthenticationSuccessHandler implements AuthenticationSuccessHandler {



    public static final String SESSION_LOGIN_COM_TROCA_SENHA = "loginComTrocaSenhaPendente";
    public static final String SESSION_LOGIN_COM_CONTATO_PENDENTE = "loginComContatoPendente";
    public static final String SESSION_LOGIN_COM_BOAS_VINDAS = "loginComBoasVindasPendente";
    public static final String SESSION_LOGIN_COM_PENDENCIAS_PAGAMENTO = "loginComPendenciasPagamentoPendente";
    public static final String SESSION_LOGIN_COM_PENDENCIAS_DONA = "loginComPendenciasDonaPendente";



    private final UsuarioRepository usuarioRepository;

    private final SegurancaProperties segurancaProperties;

    private final UsuarioService usuarioService;

    private final PagamentoConsultaService pagamentoConsultaService;

    private final LgpdConsentimentoService lgpdConsentimentoService;

    private final BoasVindasLoginService boasVindasLoginService;

    private final PendenciasDonaLoginService pendenciasDonaLoginService;

    private final RequestCache requestCache = new HttpSessionRequestCache();



    public ClinicaAuthenticationSuccessHandler(

            UsuarioRepository usuarioRepository,

            SegurancaProperties segurancaProperties,

            UsuarioService usuarioService,

            PagamentoConsultaService pagamentoConsultaService,

            LgpdConsentimentoService lgpdConsentimentoService,

            BoasVindasLoginService boasVindasLoginService,

            PendenciasDonaLoginService pendenciasDonaLoginService

    ) {

        this.usuarioRepository = usuarioRepository;

        this.segurancaProperties = segurancaProperties;

        this.usuarioService = usuarioService;

        this.pagamentoConsultaService = pagamentoConsultaService;

        this.lgpdConsentimentoService = lgpdConsentimentoService;

        this.boasVindasLoginService = boasVindasLoginService;

        this.pendenciasDonaLoginService = pendenciasDonaLoginService;

    }



    @Override

    public void onAuthenticationSuccess(

            HttpServletRequest request,

            HttpServletResponse response,

            Authentication authentication

    ) throws IOException {

        marcarTrocaSenhaPendenteNoLogin(request, authentication);
        marcarContatoPendenteNoLogin(request, authentication);
        marcarBoasVindasPendenteNoLogin(request, authentication);
        marcarPendenciasPagamentoNoLogin(request, authentication);
        marcarPendenciasDonaNoLogin(request, authentication);

        salvarAcessoNoNavegador(request, response, authentication);

        if (lgpdConsentimentoService.usuarioLogadoPrecisaConsentir()) {
            response.sendRedirect(request.getContextPath() + "/conta/consentimento-lgpd");
            return;
        }

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            String targetUrl = savedRequest.getRedirectUrl();
            if (destinoPosLoginSeguro(request, targetUrl)) {
                requestCache.removeRequest(request, response);
                response.sendRedirect(targetUrl);
                return;
            }
        }

        response.sendRedirect(request.getContextPath() + "/agendamentos/dashboard");

    }



    private void salvarAcessoNoNavegador(

            HttpServletRequest request,

            HttpServletResponse response,

            Authentication authentication

    ) {

        if (!(authentication.getPrincipal() instanceof ClinicaUserPrincipal principal)) {

            return;

        }

        String login = principal.getUsername();

        if (login == null || login.isBlank()) {

            return;

        }



        boolean lembrarAcesso = "true".equals(request.getParameter("remember-me"));

        if (!lembrarAcesso) {

            AcessoSalvoCookies.removerTudo(response, request);

            return;

        }



        int validadeSegundos = segurancaProperties.getLoginSalvoValiditySeconds();

        AcessoSalvoCookies.salvarLogin(response, request, login, validadeSegundos);



        String senhaInformada = request.getParameter("senha");

        if (senhaInformada != null && !senhaInformada.isBlank()) {

            AcessoSalvoCookies.salvarSenha(response, request, senhaInformada, validadeSegundos);

        }

    }



    private void marcarTrocaSenhaPendenteNoLogin(HttpServletRequest request, Authentication authentication) {

        if (!segurancaProperties.isExigirTrocaSenhaPrimeiroAcesso()) {

            return;

        }

        if (!(authentication.getPrincipal() instanceof ClinicaUserPrincipal principal)) {

            return;

        }

        Usuario usuario = principal.getUsuario();

        if (usuario == null || usuario.getId() == null) {

            return;

        }

        boolean deveTrocar = usuarioRepository.findById(usuario.getId())

                .map(u -> Boolean.TRUE.equals(u.getDeveTrocarSenha()))

                .orElse(false);

        if (!deveTrocar) {

            return;

        }

        HttpSession session = request.getSession(true);

        session.setAttribute(SESSION_LOGIN_COM_TROCA_SENHA, Boolean.TRUE);

    }

    private void marcarContatoPendenteNoLogin(HttpServletRequest request, Authentication authentication) {

        if (!(authentication.getPrincipal() instanceof ClinicaUserPrincipal principal)) {

            return;

        }

        Usuario usuario = principal.getUsuario();

        if (usuario == null) {

            return;

        }

        HttpSession session = request.getSession(true);

        usuarioService.marcarCadastroContatoPendenteNoLogin(session, usuario);

    }

    private void marcarBoasVindasPendenteNoLogin(HttpServletRequest request, Authentication authentication) {

        if (!(authentication.getPrincipal() instanceof ClinicaUserPrincipal principal)) {

            return;

        }

        Usuario usuario = principal.getUsuario();

        if (usuario == null) {

            return;

        }

        HttpSession session = request.getSession(true);

        boasVindasLoginService.marcarBoasVindasPendenteNoLogin(session, usuario);

    }

    private void marcarPendenciasPagamentoNoLogin(HttpServletRequest request, Authentication authentication) {

        if (!(authentication.getPrincipal() instanceof ClinicaUserPrincipal principal)) {

            return;

        }

        Usuario usuario = principal.getUsuario();

        if (usuario == null) {

            return;

        }

        HttpSession session = request.getSession(false);

        if (session == null) {

            return;

        }

        pagamentoConsultaService.marcarLembretePendenciasPagamentoNoLogin(session, usuario);
        try {
            pagamentoConsultaService.reconciliarPagamentosInfinitePayPendentes(usuario);
        } catch (RuntimeException ignored) {
            // best-effort ao entrar
        }

    }

    private void marcarPendenciasDonaNoLogin(HttpServletRequest request, Authentication authentication) {

        if (!(authentication.getPrincipal() instanceof ClinicaUserPrincipal principal)) {

            return;

        }

        Usuario usuario = principal.getUsuario();

        if (usuario == null) {

            return;

        }

        HttpSession session = request.getSession(false);

        if (session == null) {

            return;

        }

        pendenciasDonaLoginService.marcarPendenciasDonaNoLogin(session, usuario);

    }

    private boolean destinoPosLoginSeguro(HttpServletRequest request, String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            return false;
        }
        String path = extrairPathInterno(request, targetUrl);
        if (path == null || path.isBlank()) {
            return false;
        }
        return path.startsWith("/agendamentos/meus-pagamentos")
                || path.startsWith("/pagamentos/")
                || path.startsWith("/agendamentos/meus-pacientes")
                || path.startsWith("/agendamentos/dashboard");
    }

    private String extrairPathInterno(HttpServletRequest request, String targetUrl) {
        if (targetUrl.startsWith("/") && !targetUrl.startsWith("//")) {
            return targetUrl;
        }
        try {
            URI uri = URI.create(targetUrl);
            if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase(request.getServerName())) {
                return null;
            }
            return uri.getPath();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

}

