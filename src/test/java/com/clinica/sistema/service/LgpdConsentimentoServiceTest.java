package com.clinica.sistema.service;

import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LgpdConsentimentoServiceTest {

    @Mock
    private AuthService authService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private LgpdConsentimentoService service;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(10L);
        usuario.setNome("Carol");
        usuario.setLogin("carol");
    }

    @Test
    void deveExigirConsentimentoQuandoNuncaAceitou() {
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));

        assertTrue(service.usuarioPrecisaConsentir(usuario));
    }

    @Test
    void naoDeveExigirConsentimentoQuandoJaAceitou() {
        usuario.setLgpdConsentimentoEm(LocalDateTime.of(2026, 6, 3, 10, 0));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));

        assertFalse(service.usuarioPrecisaConsentir(usuario));
    }

    @Test
    void deveRegistrarDataDoConsentimento() {
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registrarConsentimento(usuario);

        verify(usuarioRepository).save(any(Usuario.class));
        assertNotNull(usuario.getLgpdConsentimentoEm());
    }
}
