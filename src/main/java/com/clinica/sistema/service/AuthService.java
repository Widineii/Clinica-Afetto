package com.clinica.sistema.service;

import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.security.ClinicaUserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;

    @Value("${app.seed-test-user.login:teste}")
    private String testUserLogin;

    @Value("${app.seed-test-user.isento-pagamento:false}")
    private boolean testUserIsentoPagamento;

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

    private static final String LOGIN_DONA_CLINICA = "polyana";

    public boolean isDonaClinica(Usuario usuario) {
        if (usuario == null) {
            return false;
        }
        if (Boolean.TRUE.equals(usuario.getDonaClinica())) {
            return true;
        }
        String login = usuario.getLogin();
        return login != null && LOGIN_DONA_CLINICA.equalsIgnoreCase(login.trim());
    }

    /** Profissionais comuns trocam a propria senha na agenda; admin e dona da clinica nao. */
    public boolean podeTrocarPropriaSenha(Usuario usuario) {
        return usuario != null
                && !isAdmin(usuario)
                && !isDonaClinica(usuario);
    }

    public boolean profissionalIgnoraValoresEPagamento(Usuario profissional) {
        if (isDonaClinica(profissional)) {
            return true;
        }
        if (!testUserIsentoPagamento || profissional == null) {
            return false;
        }
        String login = profissional.getLogin();
        return login != null && testUserLogin.equalsIgnoreCase(login.trim());
    }

    /** Profissionais comuns escolhem Diario/Semanal/Mensal na agenda; dona e admin nao. */
    public boolean podeEscolherFormaPagamento(Usuario usuario) {
        if (usuario == null || isAdmin(usuario) || isDonaClinica(usuario)) {
            return false;
        }
        return "ROLE_PROFISSIONAL".equals(usuario.getCargo());
    }

    public boolean podeGerenciarEquipe(Usuario usuario) {
        return isAdmin(usuario) || isDonaClinica(usuario);
    }

    /**
     * Ve status de pagamento (pago / nao pago) de todos os profissionais na grade.
     * Demais usuarios veem apenas os proprios agendamentos.
     */
    public boolean podeVerPagamentoDeTodos(Usuario usuario) {
        if (usuario == null) {
            return false;
        }
        if (isAdmin(usuario) || isDonaClinica(usuario)) {
            return true;
        }
        String login = usuario.getLogin();
        return login != null && testUserLogin.equalsIgnoreCase(login.trim());
    }

    /** Cadastro, senhas e periodicidade de pagamento — dona da clinica e administracao. */
    public boolean podeAcessarCentralProfissionais(Usuario usuario) {
        return podeGerenciarEquipe(usuario);
    }

    /** Relatorio de uso do site (quem entrou / quem agendou) — administracao e Polyana. */
    public boolean podeVerRelatorioUsoSite(Usuario usuario) {
        return isAdmin(usuario) || isDonaClinica(usuario);
    }

    /** Valores padrao de consulta por profissional — somente Polyana (dona da clinica). */
    public boolean podeGerenciarValoresConsultaProfissionais(Usuario usuario) {
        return isDonaClinica(usuario) && !isAdmin(usuario);
    }

    /** Lista da aba Valores: profissionais comuns, sem admin, Polyana nem perfil de teste. */
    public boolean elegivelParaGestaoValoresConsulta(Usuario profissional) {
        if (profissional == null || isAdmin(profissional)) {
            return false;
        }
        if (!"ROLE_PROFISSIONAL".equals(profissional.getCargo())) {
            return false;
        }
        return !isDonaClinica(profissional) && !profissionalIgnoraValoresEPagamento(profissional);
    }

    /** Layout caderno (Avulso / Fixo / Quinzenal) para profissionais e dona da clinica; admin mantem a grade completa. */
    public boolean deveUsarMeusAgendamentosResumido(Usuario usuario) {
        return usuario != null && !isAdmin(usuario);
    }

    /** Relatorio individual: profissionais e dona da clinica (taxas ocultas para a dona). */
    public boolean podeVerRelatorioProprio(Usuario usuario) {
        return usuario != null
                && !isAdmin(usuario)
                && "ROLE_PROFISSIONAL".equals(usuario.getCargo());
    }

    /** Dona da clinica acompanha somente o valor que ela recebe nas proprias consultas (formulario da agenda). */
    public boolean podeAcompanharGanhosConsultaPropria(Usuario usuario) {
        return isDonaClinica(usuario) && !isAdmin(usuario);
    }

    /** Card "Voce ganhou no mes" no relatorio individual de cada profissional com valores na consulta. */
    public boolean podeVerGanhosConsultaRelatorio(Usuario usuario) {
        if (!podeVerRelatorioProprio(usuario)) {
            return false;
        }
        return isDonaClinica(usuario) || !profissionalIgnoraValoresEPagamento(usuario);
    }
}
