package com.clinica.sistema;

import com.clinica.sistema.controller.AgendamentoController;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.security.ClinicaUserPrincipal;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("local")
class CentralProfissionaisIntegracaoTest {

    @Autowired
    private AgendamentoController agendamentoController;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void autenticarAdmin() {
        Usuario admin = usuarioRepository.findByLogin("admin").orElseThrow();
        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void limparSessao() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminDeveMontarModeloDaCentralSemErro() {
        ExtendedModelMap model = new ExtendedModelMap();
        HttpSession session = new MockHttpSession();

        String view = agendamentoController.abrirCentralProfissionais("equipe", null, false, model, session);

        assertEquals("central-profissionais", view);
        assertNotNull(model.get("secoesAvisoWhatsapp"));
        assertNotNull(model.get("msgWhatsappGeral"));
        assertNotNull(model.get("profissionais"));
    }
}
