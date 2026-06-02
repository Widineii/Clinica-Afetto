package com.clinica.sistema.security;

import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ClinicaUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public ClinicaUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String login = normalizarLogin(username);
        return usuarioRepository.findByLogin(login)
                .map(ClinicaUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado: " + login));
    }

    static String normalizarLogin(String login) {
        return login != null ? login.trim().toLowerCase(Locale.ROOT) : "";
    }
}
