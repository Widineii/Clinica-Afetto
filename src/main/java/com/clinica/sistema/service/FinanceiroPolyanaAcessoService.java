package com.clinica.sistema.service;

import com.clinica.sistema.config.FinanceiroProperties;
import com.clinica.sistema.model.Usuario;
import org.springframework.stereotype.Service;

@Service
public class FinanceiroPolyanaAcessoService {

    private final FinanceiroProperties financeiroProperties;
    private final AuthService authService;

    public FinanceiroPolyanaAcessoService(
            FinanceiroProperties financeiroProperties,
            AuthService authService
    ) {
        this.financeiroProperties = financeiroProperties;
        this.authService = authService;
    }

    public boolean podeAcessarGestaoFinanceira(Usuario usuario) {
        if (!financeiroProperties.getPolyana().isEnabled()) {
            return false;
        }
        return authService.isDonaClinica(usuario) || authService.isAdmin(usuario);
    }
}
