package com.clinica.sistema.config;

import com.clinica.sistema.service.AuthService;
import com.clinica.sistema.service.PresencaOnlineService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PresencaOnlineInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final PresencaOnlineService presencaOnlineService;

    public PresencaOnlineInterceptor(AuthService authService, PresencaOnlineService presencaOnlineService) {
        this.authService = authService;
        this.presencaOnlineService = presencaOnlineService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        authService.buscarUsuarioLogado()
                .ifPresent(presencaOnlineService::registrarAtividade);
        return true;
    }
}
