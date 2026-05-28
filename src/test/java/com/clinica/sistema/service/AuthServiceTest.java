package com.clinica.sistema.service;

import com.clinica.sistema.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService();
    }

    @Test
    void profissionalDeveUsarMeusAgendamentosResumido() {
        Usuario julia = new Usuario();
        julia.setCargo("ROLE_PROFISSIONAL");

        assertTrue(authService.deveUsarMeusAgendamentosResumido(julia));
    }

    @Test
    void donaClinicaDeveUsarMeusAgendamentosResumido() {
        Usuario polyana = new Usuario();
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(true);

        assertTrue(authService.deveUsarMeusAgendamentosResumido(polyana));
    }

    @Test
    void adminNaoDeveUsarMeusAgendamentosResumido() {
        Usuario admin = new Usuario();
        admin.setCargo("ROLE_ADMIN");

        assertFalse(authService.deveUsarMeusAgendamentosResumido(admin));
    }
}
