package com.clinica.sistema.service;

import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class LgpdConsentimentoService {

    private final AuthService authService;
    private final UsuarioRepository usuarioRepository;

    public LgpdConsentimentoService(AuthService authService, UsuarioRepository usuarioRepository) {
        this.authService = authService;
        this.usuarioRepository = usuarioRepository;
    }

    public boolean usuarioLogadoPrecisaConsentir() {
        return authService.buscarUsuarioLogado()
                .map(this::usuarioPrecisaConsentir)
                .orElse(false);
    }

    public boolean usuarioPrecisaConsentir(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            return false;
        }
        Usuario atualizado = usuarioRepository.findById(usuario.getId()).orElse(usuario);
        return atualizado.getLgpdConsentimentoEm() == null;
    }

    @Transactional
    public void registrarConsentimento(Usuario usuarioLogado) {
        Usuario usuario = usuarioRepository.findById(usuarioLogado.getId())
                .orElseThrow(() -> new RuntimeException("Usuario nao encontrado."));
        if (usuario.getLgpdConsentimentoEm() != null) {
            return;
        }
        usuario.setLgpdConsentimentoEm(LocalDateTime.now());
        usuarioRepository.save(usuario);
    }
}
