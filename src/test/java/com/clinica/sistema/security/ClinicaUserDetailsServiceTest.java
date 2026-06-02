package com.clinica.sistema.security;

import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicaUserDetailsServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ClinicaUserDetailsService service;

    @Test
    void loadUserByUsername_deveRetornarPrincipalDoUsuario() {
        Usuario usuario = new Usuario();
        usuario.setLogin("polyana");
        usuario.setCargo("ROLE_PROFISSIONAL");
        when(usuarioRepository.findByLogin("polyana")).thenReturn(Optional.of(usuario));

        var userDetails = service.loadUserByUsername("Polyana");

        assertEquals("polyana", userDetails.getUsername());
    }

    @Test
    void loadUserByUsername_usuarioInexistente_deveLancarExcecao() {
        when(usuarioRepository.findByLogin("inexistente")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("inexistente"));
    }
}
