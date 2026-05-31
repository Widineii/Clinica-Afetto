package com.clinica.sistema.service;

import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository);
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

    @Test
    void somenteDonaClinicaAcessaCentralProfissionais() {
        Usuario polyana = new Usuario();
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(true);

        Usuario admin = new Usuario();
        admin.setCargo("ROLE_ADMIN");

        Usuario julia = new Usuario();
        julia.setCargo("ROLE_PROFISSIONAL");

        assertTrue(authService.podeAcessarCentralProfissionais(polyana));
        assertTrue(authService.podeAcessarCentralProfissionais(admin));
        assertFalse(authService.podeAcessarCentralProfissionais(julia));
    }

    @Test
    void loginPolyanaEConsideradoDonaClinicaMesmoSemFlagNoBanco() {
        Usuario polyana = new Usuario();
        polyana.setLogin("polyana");
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(false);

        assertTrue(authService.isDonaClinica(polyana));
        assertFalse(authService.podeTrocarPropriaSenha(polyana));
    }

    @Test
    void profissionalComumPodeTrocarPropriaSenha() {
        Usuario julia = new Usuario();
        julia.setLogin("julia");
        julia.setCargo("ROLE_PROFISSIONAL");

        assertTrue(authService.podeTrocarPropriaSenha(julia));
    }

    @Test
    void adminNaoPodeTrocarPropriaSenhaNaAgenda() {
        Usuario admin = new Usuario();
        admin.setLogin("admin");
        admin.setCargo("ROLE_ADMIN");

        assertFalse(authService.podeTrocarPropriaSenha(admin));
    }

    @Test
    void somenteProfissionalComumPodeEscolherFormaPagamento() {
        Usuario polyana = new Usuario();
        polyana.setLogin("polyana");
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(true);

        Usuario julia = new Usuario();
        julia.setLogin("julia");
        julia.setCargo("ROLE_PROFISSIONAL");

        Usuario admin = new Usuario();
        admin.setCargo("ROLE_ADMIN");

        assertFalse(authService.podeEscolherFormaPagamento(polyana));
        assertTrue(authService.podeEscolherFormaPagamento(julia));
        assertFalse(authService.podeEscolherFormaPagamento(admin));
    }

    @Test
    void donaClinicaVeRelatorioProprioSemTaxasDePagamento() {
        Usuario polyana = new Usuario();
        polyana.setLogin("polyana");
        polyana.setCargo("ROLE_PROFISSIONAL");
        polyana.setDonaClinica(true);

        Usuario julia = new Usuario();
        julia.setLogin("julia");
        julia.setCargo("ROLE_PROFISSIONAL");

        assertTrue(authService.podeVerRelatorioProprio(polyana));
        assertTrue(authService.podeVerRelatorioProprio(julia));
        assertTrue(authService.profissionalIgnoraValoresEPagamento(polyana));
        assertFalse(authService.profissionalIgnoraValoresEPagamento(julia));
    }
}
