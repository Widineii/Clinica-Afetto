package com.clinica.sistema.security;

import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class ClinicaAuthenticationProvider implements AuthenticationProvider {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public ClinicaAuthenticationProvider(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String login = normalizarLogin(authentication.getName());
        String senhaInformada = authentication.getCredentials() != null
                ? authentication.getCredentials().toString()
                : "";

        Usuario usuario = usuarioRepository.findByLogin(ClinicaUserDetailsService.normalizarLogin(login))
                .orElseThrow(() -> new BadCredentialsException("Login ou senha invalidos."));

        if (!verificarSenha(usuario, senhaInformada)) {
            throw new BadCredentialsException("Login ou senha invalidos.");
        }
        if (Boolean.FALSE.equals(usuario.getContaAprovada())) {
            throw new DisabledException("Conta aguardando aprovacao.");
        }

        ClinicaUserPrincipal principal = new ClinicaUserPrincipal(usuario);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private boolean verificarSenha(Usuario usuario, String senhaInformada) {
        String senhaArmazenada = usuario.getSenha();
        if (senhaArmazenada == null || senhaArmazenada.isBlank()) {
            return false;
        }
        if (passwordEncoder.matches(senhaInformada, senhaArmazenada)) {
            return true;
        }
        if (!senhaArmazenada.startsWith("$2a$") && senhaArmazenada.equals(senhaInformada)) {
            usuario.setSenha(passwordEncoder.encode(senhaInformada));
            usuarioRepository.save(usuario);
            return true;
        }
        return false;
    }

    private String normalizarLogin(String login) {
        return login != null ? login.trim().toLowerCase(Locale.ROOT) : "";
    }
}
