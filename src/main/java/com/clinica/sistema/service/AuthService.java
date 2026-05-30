package com.clinica.sistema.service;

import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.security.ClinicaUserPrincipal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;

    public AuthService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Optional<Usuario> buscarUsuarioLogado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof ClinicaUserPrincipal clinicaUserPrincipal) {
            return Optional.of(clinicaUserPrincipal.getUsuario());
        }

        return Optional.empty();
    }

    public Usuario buscarUsuarioLogadoObrigatorio() {
        Usuario usuarioSessao = buscarUsuarioLogado()
                .orElseThrow(() -> new RuntimeException("Sessao expirada. Faca login novamente."));
        return usuarioRepository.findById(usuarioSessao.getId())
                .orElseThrow(() -> new RuntimeException("Usuario nao encontrado."));
    }

    public boolean isAdmin(Usuario usuario) {
        return "ROLE_ADMIN".equals(usuario.getCargo());
    }

    public boolean isDonaClinica(Usuario usuario) {
        return usuario != null && Boolean.TRUE.equals(usuario.getDonaClinica());
    }

    public boolean profissionalIgnoraValoresEPagamento(Usuario profissional) {
        return isDonaClinica(profissional);
    }

    public boolean podeGerenciarEquipe(Usuario usuario) {
        return isAdmin(usuario) || isDonaClinica(usuario);
    }

    /** Cadastro, senhas e periodicidade de pagamento — dona da clinica e administracao. */
    public boolean podeAcessarCentralProfissionais(Usuario usuario) {
        return podeGerenciarEquipe(usuario);
    }

    /** Layout caderno (Avulso / Fixo / Quinzenal) para profissionais e dona da clinica; admin mantem a grade completa. */
    public boolean deveUsarMeusAgendamentosResumido(Usuario usuario) {
        return usuario != null && !isAdmin(usuario);
    }
}
