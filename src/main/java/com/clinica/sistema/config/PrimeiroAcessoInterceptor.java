package com.clinica.sistema.config;

import com.clinica.sistema.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PrimeiroAcessoInterceptor implements HandlerInterceptor {

    private final UsuarioService usuarioService;

    public PrimeiroAcessoInterceptor(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri == null || podeAcessarComTrocaSenhaPendente(uri)) {
            return true;
        }
        if (!usuarioService.usuarioLogadoDeveTrocarSenha()) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/agendamentos/dashboard");
        return false;
    }

    private boolean podeAcessarComTrocaSenhaPendente(String uri) {
        return uri.equals("/")
                || uri.startsWith("/login")
                || uri.startsWith("/error")
                || uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/images/")
                || uri.startsWith("/actuator/health")
                || uri.startsWith("/agendamentos/dashboard");
    }
}
