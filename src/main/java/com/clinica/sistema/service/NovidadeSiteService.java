package com.clinica.sistema.service;

import com.clinica.sistema.dto.NovidadeSiteView;
import com.clinica.sistema.model.NovidadeLeituraUsuario;
import com.clinica.sistema.model.NovidadePublicoAlvo;
import com.clinica.sistema.model.NovidadeSite;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.NovidadeLeituraUsuarioRepository;
import com.clinica.sistema.repository.NovidadeSiteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NovidadeSiteService {

    public static final int DIAS_EXIBICAO_SEM_MARCAR = 3;

    private final NovidadeSiteRepository novidadeSiteRepository;
    private final NovidadeLeituraUsuarioRepository leituraRepository;
    private final AuthService authService;

    public NovidadeSiteService(
            NovidadeSiteRepository novidadeSiteRepository,
            NovidadeLeituraUsuarioRepository leituraRepository,
            AuthService authService
    ) {
        this.novidadeSiteRepository = novidadeSiteRepository;
        this.leituraRepository = leituraRepository;
        this.authService = authService;
    }

    public List<NovidadeSiteView> listarVisiveisParaUsuario(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            return List.of();
        }
        List<NovidadeSite> candidatas = novidadeSiteRepository.findAllByOrderByOrdemExibicaoDescPublicadaEmDesc().stream()
                .filter(novidade -> correspondePublico(novidade, usuario))
                .toList();
        if (candidatas.isEmpty()) {
            return List.of();
        }

        Map<Long, NovidadeLeituraUsuario> leituras = mapearLeituras(
                usuario.getId(),
                candidatas.stream().map(NovidadeSite::getId).toList()
        );
        LocalDateTime agora = LocalDateTime.now();
        List<NovidadeSiteView> visiveis = new ArrayList<>();
        for (NovidadeSite novidade : candidatas) {
            if (deveExibir(novidade, leituras.get(novidade.getId()), agora)) {
                visiveis.add(paraView(novidade));
            }
        }
        return visiveis;
    }

    @Transactional
    public void registrarVisualizacao(Usuario usuario, List<Long> novidadeIds) {
        if (usuario == null || usuario.getId() == null || novidadeIds == null || novidadeIds.isEmpty()) {
            return;
        }
        LocalDateTime agora = LocalDateTime.now();
        for (Long novidadeId : novidadeIds) {
            if (novidadeId == null) {
                continue;
            }
            NovidadeLeituraUsuario leitura = leituraRepository
                    .findByUsuarioIdAndNovidadeId(usuario.getId(), novidadeId)
                    .orElse(null);
            if (leitura == null) {
                leitura = new NovidadeLeituraUsuario();
                leitura.setUsuarioId(usuario.getId());
                leitura.setNovidadeId(novidadeId);
                leitura.setPrimeiraVisualizacaoEm(agora);
                leitura.setOcultarDefinitivo(false);
                leituraRepository.save(leitura);
            }
        }
    }

    @Transactional
    public void dispensarNovidades(Usuario usuario, List<Long> novidadeIds, boolean naoMostrarMais) {
        if (usuario == null || usuario.getId() == null || novidadeIds == null || novidadeIds.isEmpty()) {
            return;
        }
        LocalDateTime agora = LocalDateTime.now();
        for (Long novidadeId : novidadeIds) {
            if (novidadeId == null) {
                continue;
            }
            NovidadeLeituraUsuario leitura = leituraRepository
                    .findByUsuarioIdAndNovidadeId(usuario.getId(), novidadeId)
                    .orElseGet(() -> {
                        NovidadeLeituraUsuario nova = new NovidadeLeituraUsuario();
                        nova.setUsuarioId(usuario.getId());
                        nova.setNovidadeId(novidadeId);
                        nova.setPrimeiraVisualizacaoEm(agora);
                        return nova;
                    });
            if (leitura.getPrimeiraVisualizacaoEm() == null) {
                leitura.setPrimeiraVisualizacaoEm(agora);
            }
            if (naoMostrarMais) {
                leitura.setOcultarDefinitivo(true);
            }
            leituraRepository.save(leitura);
        }
    }

    private Map<Long, NovidadeLeituraUsuario> mapearLeituras(Long usuarioId, List<Long> novidadeIds) {
        if (novidadeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, NovidadeLeituraUsuario> mapa = new HashMap<>();
        leituraRepository.findByUsuarioIdAndNovidadeIdIn(usuarioId, novidadeIds)
                .forEach(leitura -> mapa.put(leitura.getNovidadeId(), leitura));
        return mapa;
    }

    private boolean correspondePublico(NovidadeSite novidade, Usuario usuario) {
        if (novidade == null || novidade.getPublicoAlvo() == null) {
            return false;
        }
        if (authService.isDonaClinica(usuario)) {
            return true;
        }
        if (authService.isAdmin(usuario)) {
            return novidade.getPublicoAlvo() == NovidadePublicoAlvo.PROFISSIONAL
                    || novidade.getPublicoAlvo() == NovidadePublicoAlvo.ADMIN;
        }
        return novidade.getPublicoAlvo() == NovidadePublicoAlvo.PROFISSIONAL;
    }

    private boolean deveExibir(
            NovidadeSite novidade,
            NovidadeLeituraUsuario leitura,
            LocalDateTime agora
    ) {
        if (leitura == null) {
            return true;
        }
        if (leitura.isOcultarDefinitivo()) {
            return false;
        }
        LocalDateTime inicio = leitura.getPrimeiraVisualizacaoEm() != null
                ? leitura.getPrimeiraVisualizacaoEm()
                : novidade.getPublicadaEm();
        if (inicio == null) {
            return true;
        }
        return !agora.isAfter(inicio.plusDays(DIAS_EXIBICAO_SEM_MARCAR));
    }

    private NovidadeSiteView paraView(NovidadeSite novidade) {
        return new NovidadeSiteView(
                novidade.getId(),
                novidade.getVersao(),
                novidade.getTitulo(),
                novidade.getDescricao(),
                novidade.getPublicoAlvo(),
                novidade.getPublicadaEm()
        );
    }
}
