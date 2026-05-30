package com.clinica.sistema.service;

import com.clinica.sistema.dto.AtualizarPeriodicidadeForm;
import com.clinica.sistema.dto.CadastroProfissionalForm;
import com.clinica.sistema.dto.TrocarSenhaAdminForm;
import com.clinica.sistema.dto.TrocarSenhaForm;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final PagamentoConsultaService pagamentoConsultaService;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            AgendamentoRepository agendamentoRepository,
            AuthService authService,
            PasswordEncoder passwordEncoder,
            PagamentoConsultaService pagamentoConsultaService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.pagamentoConsultaService = pagamentoConsultaService;
    }

    public List<Usuario> listarProfissionaisDaEquipe() {
        return usuarioRepository.findByCargoOrderByNomeAsc("ROLE_PROFISSIONAL");
    }

    public List<Usuario> listarUsuariosParaTrocaSenha() {
        return usuarioRepository.findAll().stream()
                .sorted((a, b) -> a.getNome().compareToIgnoreCase(b.getNome()))
                .toList();
    }

    public Usuario cadastrarProfissional(CadastroProfissionalForm form, Usuario usuarioLogado) {
        validarGerenciamentoEquipe(usuarioLogado);

        String nome = form.getNome() != null ? form.getNome().trim() : "";
        String login = form.getLogin() != null ? form.getLogin().trim().toLowerCase() : "";
        String senha = form.getSenha() != null ? form.getSenha().trim() : "";

        if (nome.isBlank()) {
            throw new RuntimeException("Informe o nome do profissional.");
        }
        if (login.isBlank()) {
            throw new RuntimeException("Informe o login do profissional.");
        }
        if (senha.isBlank()) {
            throw new RuntimeException("Informe a senha do profissional.");
        }
        if (senha.length() < 4) {
            throw new RuntimeException("A senha do profissional precisa ter pelo menos 4 caracteres.");
        }
        if (usuarioRepository.findByLogin(login).isPresent()) {
            throw new RuntimeException("Ja existe um usuario com esse login.");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setLogin(login);
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.setCargo("ROLE_PROFISSIONAL");
        usuario.setDonaClinica(false);
        usuario.setPeriodicidadePagamento(
                form.getPeriodicidade() != null ? form.getPeriodicidade() : PeriodicidadePagamento.DIARIO
        );
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public int atualizarPeriodicidadePagamento(AtualizarPeriodicidadeForm form, Usuario usuarioLogado) {
        validarGerenciamentoEquipe(usuarioLogado);

        if (form.getUsuarioId() == null) {
            throw new RuntimeException("Selecione o profissional.");
        }
        if (form.getPeriodicidade() == null) {
            throw new RuntimeException("Selecione a periodicidade de pagamento.");
        }

        Usuario alvo = usuarioRepository.findById(form.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario nao encontrado."));

        if (!"ROLE_PROFISSIONAL".equals(alvo.getCargo())) {
            throw new RuntimeException("Somente profissionais podem ter periodicidade de pagamento.");
        }
        if (Boolean.TRUE.equals(alvo.getDonaClinica())) {
            throw new RuntimeException("A dona da clínica não usa cobrança por periodicidade.");
        }

        PeriodicidadePagamento anterior = pagamentoConsultaService.resolverPeriodicidade(alvo);
        PeriodicidadePagamento nova = form.getPeriodicidade();
        alvo.setPeriodicidadePagamento(nova);
        usuarioRepository.save(alvo);

        return pagamentoConsultaService.migrarAgendamentosAoAlterarPeriodicidade(alvo, anterior, nova);
    }

    @Transactional
    public void trocarSenha(TrocarSenhaForm form, Usuario usuarioLogado) {
        Usuario usuario = usuarioRepository.findById(usuarioLogado.getId())
                .orElseThrow(() -> new RuntimeException("Usuario nao encontrado."));

        String senhaAtual = normalizarSenha(form.getSenhaAtual());
        String novaSenha = normalizarSenha(form.getNovaSenha());
        String confirmarSenha = normalizarSenha(form.getConfirmarSenha());

        if (senhaAtual.isBlank()) {
            throw new RuntimeException("Informe sua senha atual.");
        }
        if (!verificarSenhaAtual(usuario, senhaAtual)) {
            throw new RuntimeException("Senha atual incorreta.");
        }
        aplicarNovaSenha(usuario, novaSenha, confirmarSenha, true, senhaAtual);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void trocarSenhaComoGestor(TrocarSenhaAdminForm form, Usuario usuarioLogado) {
        validarGerenciamentoEquipe(usuarioLogado);

        if (form.getUsuarioId() == null) {
            throw new RuntimeException("Selecione o usuario para alterar a senha.");
        }

        Usuario alvo = usuarioRepository.findById(form.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario nao encontrado."));

        String novaSenha = normalizarSenha(form.getNovaSenha());
        String confirmarSenha = normalizarSenha(form.getConfirmarSenha());
        aplicarNovaSenha(alvo, novaSenha, confirmarSenha, false, null);
        usuarioRepository.save(alvo);
    }

    @Transactional
    public void excluirUsuario(Long usuarioId, Usuario usuarioLogado) {
        validarGerenciamentoEquipe(usuarioLogado);

        if (usuarioId == null) {
            throw new RuntimeException("Selecione o usuario para excluir.");
        }
        if (usuarioId.equals(usuarioLogado.getId())) {
            throw new RuntimeException("Você não pode excluir o seu próprio usuário.");
        }

        Usuario alvo = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario nao encontrado."));

        if (authService.isAdmin(alvo)) {
            throw new RuntimeException("Não é permitido excluir o usuário administrador.");
        }
        if (!"ROLE_PROFISSIONAL".equals(alvo.getCargo())) {
            throw new RuntimeException("Somente profissionais podem ser excluidos por aqui.");
        }
        if (agendamentoRepository.existsByProfissionalId(usuarioId)) {
            throw new RuntimeException(
                    "Nao e possivel excluir: o profissional ainda possui agendamentos. "
                            + "Encerre ou transfira os horarios antes de excluir."
            );
        }

        agendamentoRepository.deleteByProfissionalIdIn(List.of(usuarioId));
        usuarioRepository.delete(alvo);
    }

    private void aplicarNovaSenha(
            Usuario usuario,
            String novaSenha,
            String confirmarSenha,
            boolean exigirDiferenteDaAtual,
            String senhaAtual
    ) {
        if (novaSenha.isBlank()) {
            throw new RuntimeException("Informe a nova senha.");
        }
        if (novaSenha.length() < 4) {
            throw new RuntimeException("A nova senha precisa ter pelo menos 4 caracteres.");
        }
        if (!novaSenha.equals(confirmarSenha)) {
            throw new RuntimeException("A confirmação da senha não confere.");
        }
        if (exigirDiferenteDaAtual && novaSenha.equals(senhaAtual)) {
            throw new RuntimeException("A nova senha precisa ser diferente da senha atual.");
        }

        usuario.setSenha(passwordEncoder.encode(novaSenha));
    }

    private void validarGerenciamentoEquipe(Usuario usuarioLogado) {
        if (!authService.podeAcessarCentralProfissionais(usuarioLogado)) {
            throw new RuntimeException("Somente a dona da clínica pode acessar a central dos profissionais.");
        }
    }

    private boolean verificarSenhaAtual(Usuario usuario, String senhaAtual) {
        String armazenada = usuario.getSenha();
        if (armazenada == null) {
            return false;
        }
        if (passwordEncoder.matches(senhaAtual, armazenada)) {
            return true;
        }
        return !armazenada.startsWith("$2a$") && armazenada.equals(senhaAtual);
    }

    private String normalizarSenha(String senha) {
        return senha != null ? senha.trim() : "";
    }
}
