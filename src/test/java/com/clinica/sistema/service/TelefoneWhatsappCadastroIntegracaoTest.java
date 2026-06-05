package com.clinica.sistema.service;

import com.clinica.sistema.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class TelefoneWhatsappCadastroIntegracaoTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioService usuarioService;

    @Test
    void carolDevePrecisarCadastrarWhatsapp() {
        var carol = usuarioRepository.findByLogin("carol").orElseThrow();
        assertTrue(authService.podeCadastrarProprioTelefoneWhatsapp(carol));
        assertTrue(usuarioService.precisaCadastrarTelefoneWhatsapp(carol));
    }

    @Test
    void juliaNaoDevePrecisarQuandoJaTemNumeroSalvo() {
        var julia = usuarioRepository.findByLogin("julia").orElseThrow();
        assertTrue(authService.podeCadastrarProprioTelefoneWhatsapp(julia));
        assertFalse(usuarioService.precisaCadastrarTelefoneWhatsapp(julia));
    }

    @Test
    void polyanaNaoCadastraProprioWhatsappNaAgenda() {
        var polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        assertFalse(authService.podeCadastrarProprioTelefoneWhatsapp(polyana));
        assertFalse(usuarioService.precisaCadastrarTelefoneWhatsapp(polyana));
    }
}
