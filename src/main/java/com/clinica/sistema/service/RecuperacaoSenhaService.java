package com.clinica.sistema.service;

import com.clinica.sistema.config.RecuperacaoSenhaProperties;
import com.clinica.sistema.dto.RedefinirSenhaCodigoForm;
import com.clinica.sistema.dto.SolicitarCodigoSenhaForm;
import com.clinica.sistema.model.SenhaRecuperacao;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.SenhaRecuperacaoRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class RecuperacaoSenhaService {

    public static final String MSG_SOLICITACAO_GENERICO =
            "Se o login e o e-mail estiverem corretos, enviamos um codigo valido por alguns minutos.";

    private static final Pattern EMAIL_VALIDO = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final UsuarioRepository usuarioRepository;
    private final SenhaRecuperacaoRepository senhaRecuperacaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailEnvioService emailEnvioService;
    private final RecuperacaoSenhaProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RecuperacaoSenhaService(
            UsuarioRepository usuarioRepository,
            SenhaRecuperacaoRepository senhaRecuperacaoRepository,
            PasswordEncoder passwordEncoder,
            EmailEnvioService emailEnvioService,
            RecuperacaoSenhaProperties properties
    ) {
        this.usuarioRepository = usuarioRepository;
        this.senhaRecuperacaoRepository = senhaRecuperacaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailEnvioService = emailEnvioService;
        this.properties = properties;
    }

    public boolean recuperacaoHabilitada() {
        return properties.isEnabled();
    }

    @Transactional
    public void solicitarCodigo(SolicitarCodigoSenhaForm form) {
        if (!properties.isEnabled()) {
            throw new RuntimeException("Recuperacao de senha indisponivel no momento.");
        }

        String login = normalizarLogin(form.getLogin());
        String email = normalizarEmail(form.getEmail());

        if (login.isBlank()) {
            throw new RuntimeException("Informe seu login do sistema.");
        }
        if (email.isBlank() || !EMAIL_VALIDO.matcher(email).matches()) {
            throw new RuntimeException("Informe um e-mail valido.");
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.findByLogin(login);
        if (usuarioOpt.isEmpty() || !emailCompativel(usuarioOpt.get(), email)) {
            return;
        }

        Usuario usuario = usuarioOpt.get();
        if (emailCadastradoEmBranco(usuario)) {
            usuario.setEmail(email);
            usuarioRepository.save(usuario);
        }

        Optional<SenhaRecuperacao> ultimo = senhaRecuperacaoRepository
                .findFirstByUsuarioIdAndUsadoEmIsNullOrderByCriadoEmDesc(usuario.getId());
        if (ultimo.isPresent()) {
            LocalDateTime limite = ultimo.get().getCriadoEm()
                    .plusMinutes(properties.getIntervaloReenvioMinutos());
            if (LocalDateTime.now().isBefore(limite)) {
                return;
            }
        }

        String codigo = gerarCodigo();
        SenhaRecuperacao registro = new SenhaRecuperacao();
        registro.setUsuarioId(usuario.getId());
        registro.setCodigoHash(passwordEncoder.encode(codigo));
        registro.setCriadoEm(LocalDateTime.now());
        registro.setExpiraEm(LocalDateTime.now().plusMinutes(properties.getCodigoExpiracaoMinutos()));
        senhaRecuperacaoRepository.save(registro);

        emailEnvioService.enviarCodigoRecuperacaoSenha(email, codigo);
    }

    @Transactional
    public void redefinirComCodigo(RedefinirSenhaCodigoForm form) {
        if (!properties.isEnabled()) {
            throw new RuntimeException("Recuperacao de senha indisponivel no momento.");
        }

        String login = normalizarLogin(form.getLogin());
        String codigo = normalizarCodigo(form.getCodigo());
        String novaSenha = normalizarSenha(form.getNovaSenha());
        String confirmarSenha = normalizarSenha(form.getConfirmarSenha());

        if (login.isBlank()) {
            throw new RuntimeException("Informe seu login do sistema.");
        }
        if (codigo.isBlank()) {
            throw new RuntimeException("Informe o codigo recebido por e-mail.");
        }
        if (novaSenha.isBlank()) {
            throw new RuntimeException("Informe a nova senha.");
        }
        if (novaSenha.length() < 4) {
            throw new RuntimeException("A nova senha precisa ter pelo menos 4 caracteres.");
        }
        if (!novaSenha.equals(confirmarSenha)) {
            throw new RuntimeException("A confirmacao da senha nao confere.");
        }

        Usuario usuario = usuarioRepository.findByLogin(login)
                .orElseThrow(() -> new RuntimeException("Codigo invalido ou expirado."));

        SenhaRecuperacao registro = senhaRecuperacaoRepository
                .findFirstByUsuarioIdAndUsadoEmIsNullOrderByCriadoEmDesc(usuario.getId())
                .orElseThrow(() -> new RuntimeException("Codigo invalido ou expirado."));

        if (registro.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Codigo expirado. Solicite um novo codigo.");
        }
        if (!passwordEncoder.matches(codigo, registro.getCodigoHash())) {
            throw new RuntimeException("Codigo invalido ou expirado.");
        }

        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setDeveTrocarSenha(false);
        usuarioRepository.save(usuario);

        registro.setUsadoEm(LocalDateTime.now());
        senhaRecuperacaoRepository.save(registro);
    }

    private boolean emailCompativel(Usuario usuario, String emailInformado) {
        String cadastrado = normalizarEmail(usuario.getEmail());
        if (cadastrado.isBlank()) {
            return true;
        }
        return cadastrado.equals(emailInformado);
    }

    private boolean emailCadastradoEmBranco(Usuario usuario) {
        return usuario.getEmail() == null || usuario.getEmail().isBlank();
    }

    private String gerarCodigo() {
        int valor = secureRandom.nextInt(1_000_000);
        return String.format(Locale.ROOT, "%06d", valor);
    }

    private String normalizarLogin(String login) {
        return login != null ? login.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizarEmail(String email) {
        return email != null ? email.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizarCodigo(String codigo) {
        return codigo != null ? codigo.trim() : "";
    }

    private String normalizarSenha(String senha) {
        return senha != null ? senha.trim() : "";
    }
}
