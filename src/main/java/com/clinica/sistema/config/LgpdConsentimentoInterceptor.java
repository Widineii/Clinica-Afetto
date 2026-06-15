package com.clinica.sistema.config;

import com.clinica.sistema.service.LgpdConsentimentoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LgpdConsentimentoInterceptor implements HandlerInterceptor {

    private final LgpdConsentimentoService lgpdConsentimentoService;

    public LgpdConsentimentoInterceptor(LgpdConsentimentoService lgpdConsentimentoService) {
        this.lgpdConsentimentoService = lgpdConsentimentoService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = request.getRequestURI();
        if (uri == null || podeAcessarSemConsentimento(uri)) {
            return true;
        }
        if (!lgpdConsentimentoService.usuarioLogadoPrecisaConsentir()) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/conta/consentimento-lgpd");
        return false;
    }

    private boolean podeAcessarSemConsentimento(String uri) {
        return uri.equals("/")
                || uri.startsWith("/login")
                || uri.startsWith("/logout")
                || uri.startsWith("/error")
                || uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/images/")
                || uri.startsWith("/actuator/health")
                || uri.startsWith("/conta/consentimento-lgpd");
    }
}
