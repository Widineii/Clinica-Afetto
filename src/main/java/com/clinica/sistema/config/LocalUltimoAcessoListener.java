package com.clinica.sistema.config;

import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.security.ClinicaUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@Profile("local")
public class LocalUltimoAcessoListener {

    private static final Logger log = LoggerFactory.getLogger(LocalUltimoAcessoListener.class);

    private final UsuarioRepository usuarioRepository;

    public LocalUltimoAcessoListener(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @EventListener
    @Transactional
    public void registrarUltimoAcesso(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication().getPrincipal() instanceof ClinicaUserPrincipal principal)) {
            return;
        }
        Usuario usuario = principal.getUsuario();
        if (usuario == null || usuario.getId() == null) {
            return;
        }
        usuarioRepository.findById(usuario.getId()).ifPresent(registro -> {
            registro.setUltimoAcessoEm(LocalDateTime.now());
            usuarioRepository.save(registro);
            log.debug("Ultimo acesso registrado para login={}", registro.getLogin());
        });
    }
}
