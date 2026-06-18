package com.clinica.sistema.service;

import com.clinica.sistema.config.FinanceiroProperties;
import com.clinica.sistema.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceiroPolyanaAcessoServiceTest {

    private FinanceiroProperties financeiroProperties;
    private AuthService authService;
    private FinanceiroPolyanaAcessoService service;

    @BeforeEach
    void setUp() {
        financeiroProperties = new FinanceiroProperties();
        authService = mock(AuthService.class);
        service = new FinanceiroPolyanaAcessoService(financeiroProperties, authService);
    }

    @Test
    void donaClinicaComFlagHabilitadaDeveAcessar() {
        financeiroProperties.getPolyana().setEnabled(true);
        Usuario polyana = new Usuario();
        when(authService.isDonaClinica(polyana)).thenReturn(true);

        assertTrue(service.podeAcessarGestaoFinanceira(polyana));
    }

    @Test
    void adminComFlagHabilitadaDeveAcessar() {
        financeiroProperties.getPolyana().setEnabled(true);
        Usuario admin = new Usuario();
        admin.setCargo("ROLE_ADMIN");
        when(authService.isDonaClinica(admin)).thenReturn(false);
        when(authService.isAdmin(admin)).thenReturn(true);

        assertTrue(service.podeAcessarGestaoFinanceira(admin));
    }

    @Test
    void profissionalComumNaoDeveAcessar() {
        financeiroProperties.getPolyana().setEnabled(true);
        Usuario julia = new Usuario();
        when(authService.isDonaClinica(julia)).thenReturn(false);
        when(authService.isAdmin(julia)).thenReturn(false);

        assertFalse(service.podeAcessarGestaoFinanceira(julia));
    }

    @Test
    void flagDesligadaBloqueiaMesmoDonaClinica() {
        financeiroProperties.getPolyana().setEnabled(false);
        Usuario polyana = new Usuario();
        when(authService.isDonaClinica(polyana)).thenReturn(true);

        assertFalse(service.podeAcessarGestaoFinanceira(polyana));
    }
}
