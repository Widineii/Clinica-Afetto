package com.clinica.sistema.service;

import com.clinica.sistema.config.PresencaOnlineProperties;
import com.clinica.sistema.dto.PresencaOnlineUsuarioView;
import com.clinica.sistema.dto.PresencaOnlineView;
import com.clinica.sistema.model.Usuario;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresencaOnlineService {

    private final PresencaOnlineProperties properties;
    private final AuthService authService;
    private final Map<Long, PresencaRegistro> presencaPorUsuario = new ConcurrentHashMap<>();

    public PresencaOnlineService(PresencaOnlineProperties properties, AuthService authService) {
        this.properties = properties;
        this.authService = authService;
    }

    public void registrarAtividade(Usuario usuario) {
        if (usuario == null || usuario.getId() == null || !authService.deveAparecerNaPresencaOnline(usuario)) {
            return;
        }
        registrarAtividade(usuario.getId(), resolverNomeExibicao(usuario));
    }

    public void registrarAtividade(Long usuarioId, String nome) {
        if (usuarioId == null) {
            return;
        }
        presencaPorUsuario.put(
                usuarioId,
                new PresencaRegistro(resolverNome(nome), Instant.now())
        );
        limparInativos();
    }

    public int contarUsuariosOnline() {
        limparInativos();
        return presencaPorUsuario.size();
    }

    public List<PresencaOnlineUsuarioView> listarUsuariosOnline() {
        limparInativos();
        List<PresencaOnlineUsuarioView> usuarios = new ArrayList<>();
        presencaPorUsuario.forEach((id, registro) -> usuarios.add(
                new PresencaOnlineUsuarioView(registro.nome(), null)
        ));
        usuarios.sort(Comparator.comparing(PresencaOnlineUsuarioView::nome, String.CASE_INSENSITIVE_ORDER));
        return usuarios;
    }

    public PresencaOnlineView montarVisaoAtual() {
        if (properties.isDemoLocal()) {
            return montarVisaoDemoLocal();
        }
        List<PresencaOnlineUsuarioView> usuarios = listarUsuariosOnline();
        return new PresencaOnlineView(usuarios.size(), getMinutosAtivos(), usuarios);
    }

    private PresencaOnlineView montarVisaoDemoLocal() {
        List<PresencaOnlineUsuarioView> usuarios = properties.getDemoNomes().stream()
                .map(nome -> new PresencaOnlineUsuarioView(nome, null))
                .toList();
        return new PresencaOnlineView(usuarios.size(), getMinutosAtivos(), usuarios);
    }

    public int getMinutosAtivos() {
        return properties.getMinutosAtivos();
    }

    private void limparInativos() {
        Instant limite = Instant.now().minus(Duration.ofMinutes(properties.getMinutosAtivos()));
        Iterator<Map.Entry<Long, PresencaRegistro>> iterator = presencaPorUsuario.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, PresencaRegistro> entry = iterator.next();
            if (entry.getValue().ultimaAtividade().isBefore(limite)) {
                iterator.remove();
            }
        }
    }

    private static String resolverNomeExibicao(Usuario usuario) {
        if (usuario.getNome() != null && !usuario.getNome().isBlank()) {
            return usuario.getNome().trim();
        }
        if (usuario.getLogin() != null && !usuario.getLogin().isBlank()) {
            return usuario.getLogin().trim();
        }
        return "Usuário";
    }

    private static String resolverNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return "Usuário";
        }
        return nome.trim();
    }

    private record PresencaRegistro(String nome, Instant ultimaAtividade) {
    }
}
