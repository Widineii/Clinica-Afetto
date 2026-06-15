package com.clinica.sistema.security;



import com.clinica.sistema.config.SegurancaProperties;

import com.clinica.sistema.model.Usuario;

import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.service.LgpdConsentimentoService;
import com.clinica.sistema.service.PagamentoConsultaService;
import com.clinica.sistema.service.UsuarioService;

import jakarta.servlet.http.HttpServletRequest;

import jakarta.servlet.http.HttpServletResponse;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;

import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import org.springframework.stereotype.Component;



import java.io.IOException;



@Component

public class ClinicaAuthenticationSuccessHandler implements AuthenticationSuccessHandler {



    public static final String SESSION_LOGIN_COM_TROCA_SENHA = "loginComTrocaSenhaPendente";
    public static final String SESSION_LOGIN_COM_WHATSAPP_PENDENTE = "loginComWhatsappPendente";
    public static final String SESSION_LOGIN_COM_PENDENCIAS_PAGAMENTO = "loginComPendenciasPagamentoPendente";



    private final UsuarioRepository usuarioRepository;

    private final SegurancaProperties segurancaProperties;

    private final UsuarioService usuarioService;

    private final PagamentoConsultaService pagamentoConsultaService;

    private final LgpdConsentimentoService lgpdConsentimentoService;



    public ClinicaAuthenticationSuccessHandler(

            UsuarioRepository usuarioRepository,

            SegurancaProperties segurancaProperties,

            UsuarioService usuarioService,

            PagamentoConsultaService pagamentoConsultaService,

            LgpdConsentimentoService lgpdConsentimentoService

    ) {

        this.usuarioRepository = usuarioRepository;

        this.segurancaProperties = segurancaProperties;

        this.usuarioService = usuarioService;

        this.pagamentoConsultaService = pagamentoConsultaService;

        this.lgpdConsentimentoService = lgpdConsentimentoService;

    }



    @Override

    public void onAuthenticationSuccess(

            HttpServletRequest request,

            HttpServletResponse response,

            Authentication authentication

    ) throws IOException {

        marcarTrocaSenhaPendenteNoLogin(request, authentication);
        marcarWhatsappPendenteNoLogin(request, authentication);
        marcarPendenciasPagamentoNoLogin(request, authentication);

        salvarAcessoNoNavegador(request, response, authentication);

        String destino = lgpdConsentimentoService.usuarioLogadoPrecisaConsentir()
                ? "/conta/consentimento-lgpd"
                : "/agendamentos/dashboard";
        response.sendRedirect(request.getContextPath() + destino);

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

        if (usuario == null || usuario.getId() == null || "ROLE_ADMIN".equals(usuario.getCargo())) {

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

    private void marcarWhatsappPendenteNoLogin(HttpServletRequest request, Authentication authentication) {

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

        usuarioService.marcarCadastroTelefoneWhatsappPendenteNoLogin(session, usuario);

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

    }

}

