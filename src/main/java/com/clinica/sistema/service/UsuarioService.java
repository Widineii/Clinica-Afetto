package com.clinica.sistema.service;

import com.clinica.sistema.config.SegurancaProperties;
import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.AtualizarPeriodicidadeForm;
import com.clinica.sistema.dto.GraficoJsonUtil;
import com.clinica.sistema.dto.AtualizarValoresConsultaProfissionalForm;
import com.clinica.sistema.dto.CadastroProfissionalForm;
import com.clinica.sistema.dto.ProfissionalValoresConsultaLinhaView;
import com.clinica.sistema.dto.TrocarSenhaAdminForm;
import com.clinica.sistema.dto.TrocarSenhaForm;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.security.ClinicaAuthenticationSuccessHandler;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UsuarioService {
    private static final Duration BLOQUEIO_ALTERACAO_PERIODICIDADE = Duration.ofHours(24);
    private static final DateTimeFormatter FORMATO_PROXIMA_ALTERACAO =
            DateTimeFormatter.ofPattern("dd/MM 'as' HH:mm");

    private final UsuarioRepository usuarioRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final PagamentoConsultaService pagamentoConsultaService;
    private final SegurancaProperties segurancaProperties;
    private final ValorConsultaService valorConsultaService;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            AgendamentoRepository agendamentoRepository,
            AuthService authService,
            PasswordEncoder passwordEncoder,
            PagamentoConsultaService pagamentoConsultaService,
            SegurancaProperties segurancaProperties,
            ValorConsultaService valorConsultaService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.pagamentoConsultaService = pagamentoConsultaService;
        this.segurancaProperties = segurancaProperties;
        this.valorConsultaService = valorConsultaService;
    }

    public List<Usuario> listarProfissionaisDaEquipe() {
        return usuarioRepository.findByCargoOrderByNomeAsc("ROLE_PROFISSIONAL");
    }

    public String jsonValoresConsultaPadraoPorProfissional() {
        return GraficoJsonUtil.serializarValoresConsultaPadrao(mapaValoresConsultaPadraoPorProfissional());
    }

    public void preencherValorConsultaPadraoNoForm(AgendamentoForm form, Long profissionalId, String recorrencia) {
        if (form == null || profissionalId == null || form.getValorProfissionalRecebe() != null) {
            return;
        }
        usuarioRepository.findById(profissionalId).ifPresent(profissional ->
                valorConsultaService.valorPadraoProfissionalRecebe(profissional, recorrencia)
                        .ifPresent(form::setValorProfissionalRecebe)
        );
    }

    public Map<String, Map<String, BigDecimal>> mapaValoresConsultaPadraoPorProfissional() {
        Map<String, Map<String, BigDecimal>> mapa = new LinkedHashMap<>();
        for (Usuario profissional : listarProfissionaisDaEquipe()) {
            if (!authService.elegivelParaGestaoValoresConsulta(profissional)) {
                continue;
            }
            Map<String, BigDecimal> valores = valoresConsultaDoProfissional(profissional);
            if (!valores.isEmpty()) {
                mapa.put(String.valueOf(profissional.getId()), valores);
            }
        }
        return mapa;
    }

    private Map<String, BigDecimal> valoresConsultaDoProfissional(Usuario profissional) {
        Map<String, BigDecimal> valores = new LinkedHashMap<>();
        adicionarValorSePresente(valores, "AVULSO", profissional.getValorConsultaAvulso());
        adicionarValorSePresente(valores, "SEMANAL", profissional.getValorConsultaSemanal());
        adicionarValorSePresente(valores, "QUINZENAL", profissional.getValorConsultaQuinzenal());
        adicionarValorSePresente(valores, "MENSAL", profissional.getValorConsultaMensal());
        return valores;
    }

    private void adicionarValorSePresente(Map<String, BigDecimal> valores, String chave, BigDecimal valor) {
        if (valor != null && valor.signum() > 0) {
            valores.put(chave, valor.setScale(2, RoundingMode.HALF_UP));
        }
    }

    public List<ProfissionalValoresConsultaLinhaView> listarProfissionaisParaGestaoValoresConsulta() {
        return listarProfissionaisDaEquipe().stream()
                .filter(authService::elegivelParaGestaoValoresConsulta)
                .map(ProfissionalValoresConsultaLinhaView::from)
                .toList();
    }

    @Transactional
    public void atualizarValoresConsultaProfissional(
            AtualizarValoresConsultaProfissionalForm form,
            Usuario usuarioLogado
    ) {
        if (!authService.podeGerenciarValoresConsultaProfissionais(usuarioLogado)) {
            throw new RuntimeException("Apenas a Polyana pode alterar os valores de consulta.");
        }
        if (form == null || form.getUsuarioId() == null) {
            throw new RuntimeException("Selecione o profissional.");
        }
        Usuario profissional = usuarioRepository.findById(form.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Profissional nao encontrado."));
        if (!authService.elegivelParaGestaoValoresConsulta(profissional)) {
            throw new RuntimeException("Este usuario nao pode ter valores de consulta cadastrados.");
        }
        profissional.setValorConsultaAvulso(normalizarValorOpcional(form.getValorAvulso(), "avulso"));
        profissional.setValorConsultaSemanal(normalizarValorOpcional(form.getValorSemanal(), "semanal"));
        profissional.setValorConsultaQuinzenal(normalizarValorOpcional(form.getValorQuinzenal(), "quinzenal"));
        profissional.setValorConsultaMensal(normalizarValorOpcional(form.getValorMensal(), "mensal"));
        usuarioRepository.save(profissional);
    }

    private BigDecimal normalizarValorOpcional(BigDecimal valor, String rotulo) {
        if (valor == null) {
            return null;
        }
        if (valor.signum() <= 0) {
            throw new RuntimeException("Informe um valor positivo para " + rotulo + ".");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
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
        if (segurancaProperties.isExigirTrocaSenhaPrimeiroAcesso()) {
            usuario.setDeveTrocarSenha(true);
        }
        return usuarioRepository.save(usuario);
    }

    public boolean usuarioLogadoDeveTrocarSenha() {
        if (!segurancaProperties.isExigirTrocaSenhaPrimeiroAcesso()) {
            return false;
        }
        return authService.buscarUsuarioLogado()
                .map(this::deveTrocarSenha)
                .orElse(false);
    }

    private boolean deveTrocarSenha(Usuario usuarioLogado) {
        if (authService.isAdmin(usuarioLogado)) {
            return false;
        }
        Usuario atualizado = usuarioRepository.findById(usuarioLogado.getId()).orElse(usuarioLogado);
        return Boolean.TRUE.equals(atualizado.getDeveTrocarSenha());
    }

    public boolean exibirModalTrocaSenhaPrimeiroAcesso(HttpSession session, boolean reabrirAposErro) {
        if (!usuarioLogadoDeveTrocarSenha()) {
            return false;
        }
        if (reabrirAposErro) {
            return true;
        }
        if (session == null) {
            return false;
        }
        return Boolean.TRUE.equals(session.getAttribute(ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_TROCA_SENHA));
    }

    public void confirmarExibicaoModalTrocaSenha(HttpSession session) {
        if (session != null) {
            session.removeAttribute(ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_TROCA_SENHA);
        }
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
        return aplicarAlteracaoPeriodicidade(alvo, anterior, nova, false);
    }

    public boolean podeAlterarPeriodicidadePropria(Usuario profissional) {
        Usuario atual = recarregarProfissional(profissional);
        return calcularProximaAlteracaoPeriodicidadePermitida(atual) == null;
    }

    public String mensagemBloqueioPeriodicidade(Usuario profissional) {
        Usuario atual = recarregarProfissional(profissional);
        LocalDateTime proxima = calcularProximaAlteracaoPeriodicidadePermitida(atual);
        if (proxima == null) {
            return null;
        }
        return "Voce podera alterar novamente em " + proxima.format(FORMATO_PROXIMA_ALTERACAO) + ".";
    }

    @Transactional
    public int atualizarPeriodicidadePropria(PeriodicidadePagamento nova, Usuario profissionalLogado) {
        validarProfissionalComPeriodicidade(profissionalLogado);

        Usuario alvo = recarregarProfissional(profissionalLogado);
        PeriodicidadePagamento atual = pagamentoConsultaService.resolverPeriodicidade(alvo);
        if (nova == atual) {
            return 0;
        }
        if (nova == null) {
            throw new RuntimeException("Selecione a forma de pagamento.");
        }

        LocalDateTime proximaAlteracao = calcularProximaAlteracaoPeriodicidadePermitida(alvo);
        if (proximaAlteracao != null) {
            throw new RuntimeException(
                    "A forma de pagamento so pode ser alterada uma vez a cada 24 horas. "
                            + "Voce podera alterar novamente em "
                            + proximaAlteracao.format(FORMATO_PROXIMA_ALTERACAO)
                            + "."
            );
        }

        return aplicarAlteracaoPeriodicidade(alvo, atual, nova, true);
    }

    private int aplicarAlteracaoPeriodicidade(
            Usuario alvo,
            PeriodicidadePagamento anterior,
            PeriodicidadePagamento nova,
            boolean registrarBloqueioProprio
    ) {
        if (nova == anterior) {
            return 0;
        }
        alvo.setPeriodicidadePagamento(nova);
        if (registrarBloqueioProprio) {
            alvo.setPeriodicidadeAlteradaEm(LocalDateTime.now());
        }
        usuarioRepository.save(alvo);
        return pagamentoConsultaService.migrarAgendamentosAoAlterarPeriodicidade(alvo, anterior, nova);
    }

    private LocalDateTime calcularProximaAlteracaoPeriodicidadePermitida(Usuario profissional) {
        if (profissional.getPeriodicidadeAlteradaEm() == null) {
            return null;
        }
        LocalDateTime libera = profissional.getPeriodicidadeAlteradaEm().plus(BLOQUEIO_ALTERACAO_PERIODICIDADE);
        if (!libera.isAfter(LocalDateTime.now())) {
            return null;
        }
        return libera;
    }

    private Usuario recarregarProfissional(Usuario profissional) {
        if (profissional == null || profissional.getId() == null) {
            throw new RuntimeException("Usuario nao encontrado.");
        }
        return usuarioRepository.findById(profissional.getId())
                .orElseThrow(() -> new RuntimeException("Usuario nao encontrado."));
    }

    private void validarProfissionalComPeriodicidade(Usuario usuario) {
        if (usuario == null) {
            throw new RuntimeException("Sessao expirada. Faca login novamente.");
        }
        if (!authService.podeEscolherFormaPagamento(usuario)) {
            throw new RuntimeException("A dona da clinica nao usa cobranca por periodicidade.");
        }
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
        usuario.setDeveTrocarSenha(false);
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
        if (segurancaProperties.isExigirTrocaSenhaPrimeiroAcesso()) {
            alvo.setDeveTrocarSenha(true);
        }
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
            throw new RuntimeException("Acesso negado à central dos profissionais.");
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
