package com.clinica.sistema.security;

import com.clinica.sistema.config.SegurancaProperties;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
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

    private final UsuarioRepository usuarioRepository;
    private final SegurancaProperties segurancaProperties;

    public ClinicaAuthenticationSuccessHandler(
            UsuarioRepository usuarioRepository,
            SegurancaProperties segurancaProperties
    ) {
        this.usuarioRepository = usuarioRepository;
        this.segurancaProperties = segurancaProperties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        marcarTrocaSenhaPendenteNoLogin(request, authentication);
        response.sendRedirect(request.getContextPath() + "/agendamentos/dashboard");
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
}
