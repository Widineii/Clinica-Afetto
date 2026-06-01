package com.clinica.sistema.config;

import com.clinica.sistema.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "app.seed-test-user.enabled=true",
        "app.seed-test-user.login=teste",
        "app.seed-test-user.password=297b",
        "app.seed-test-user.name=Perfil Teste"
})
class StartupTestUserSeedTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void deveCriarUsuarioTesteNoStartup() {
        var usuario = usuarioRepository.findByLogin("teste").orElseThrow();
        assertEquals("Perfil Teste", usuario.getNome());
        assertEquals("ROLE_PROFISSIONAL", usuario.getCargo());
        assertNotNull(usuario.getSenha());
        assertTrue(passwordEncoder.matches("297b", usuario.getSenha()));
    }
}
