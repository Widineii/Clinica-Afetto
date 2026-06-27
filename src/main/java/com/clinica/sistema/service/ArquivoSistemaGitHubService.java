package com.clinica.sistema.service;

import com.clinica.sistema.config.ArquivoSistemaGitHubProperties;
import com.clinica.sistema.dto.ArquivoSistemaItemView;
import com.clinica.sistema.dto.ArquivoSistemaResumoView;
import com.clinica.sistema.exception.ArquivoSistemaIndisponivelException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ArquivoSistemaGitHubService {

    private static final DateTimeFormatter DATA_COMMIT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
                    .withZone(ZoneId.of("America/Sao_Paulo"));

    private final ArquivoSistemaGitHubProperties properties;
    private final RestTemplate restTemplate;
    private final AtomicReference<CachedResumo> cacheResumo = new AtomicReference<>();

    public ArquivoSistemaGitHubService(ArquivoSistemaGitHubProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    public ArquivoSistemaResumoView montarResumo(boolean forcarAtualizacao) {
        if (!forcarAtualizacao) {
            CachedResumo cached = cacheResumo.get();
            if (cached != null && !cached.expirado()) {
                return cached.resumo();
            }
        }

        ArquivoSistemaResumoView resumo = buscarResumoNoGitHub();
        cacheResumo.set(new CachedResumo(resumo, Instant.now().plusSeconds(cacheSegundos())));
        return resumo;
    }

    public String buscarConteudoArquivo(String caminhoRelativo) {
        validarCaminhoRelativo(caminhoRelativo);
        String url = properties.resolverUrlRaw(caminhoRelativo);
        try {
            ResponseEntity<byte[]> resposta = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headersGitHub()),
                    byte[].class
            );
            byte[] corpo = resposta.getBody();
            if (corpo == null) {
                throw new ArquivoSistemaIndisponivelException("Arquivo vazio ou indisponivel no GitHub.");
            }
            if (corpo.length > 1_500_000) {
                throw new ArquivoSistemaIndisponivelException(
                        "Arquivo muito grande para visualizar aqui. Use o link do GitHub ou baixe o ZIP completo."
                );
            }
            return new String(corpo, StandardCharsets.UTF_8);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                throw new ArquivoSistemaIndisponivelException("Arquivo nao encontrado no GitHub: " + caminhoRelativo);
            }
            throw new ArquivoSistemaIndisponivelException(
                    "Nao foi possivel carregar o arquivo no GitHub. Tente novamente em alguns minutos.",
                    e
            );
        } catch (ArquivoSistemaIndisponivelException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ArquivoSistemaIndisponivelException(
                    "Nao foi possivel carregar o arquivo no GitHub. Tente novamente em alguns minutos.",
                    e
            );
        }
    }

    public void validarCaminhoRelativo(String caminhoRelativo) {
        if (caminhoRelativo == null || caminhoRelativo.isBlank()) {
            throw new ArquivoSistemaIndisponivelException("Informe o caminho do arquivo.");
        }
        String caminho = caminhoRelativo.trim();
        if (caminho.startsWith("/") || caminho.contains("..") || caminho.contains("\\")) {
            throw new ArquivoSistemaIndisponivelException("Caminho de arquivo invalido.");
        }
    }

    private ArquivoSistemaResumoView buscarResumoNoGitHub() {
        String branch = properties.getGithubBranch();
        Map<String, Object> commit = buscarMap(
                "https://api.github.com/repos/"
                        + properties.getGithubOwner() + "/"
                        + properties.getGithubRepo() + "/commits/" + branch
        );

        String commitSha = texto(commit.get("sha"));
        Map<String, Object> commitDetalhe = mapa(commit.get("commit"));
        String commitMensagem = texto(commitDetalhe.get("message"));
        if (commitMensagem.contains("\n")) {
            commitMensagem = commitMensagem.substring(0, commitMensagem.indexOf('\n')).trim();
        }
        Map<String, Object> committer = mapa(commitDetalhe.get("committer"));
        String commitDataLabel = formatarDataCommit(texto(committer.get("date")));

        Map<String, Object> treeCommit = mapa(commitDetalhe.get("tree"));
        String treeSha = texto(treeCommit.get("sha"));
        Map<String, Object> arvore = buscarMap(
                "https://api.github.com/repos/"
                        + properties.getGithubOwner() + "/"
                        + properties.getGithubRepo() + "/git/trees/" + treeSha + "?recursive=1"
        );

        boolean truncada = Boolean.TRUE.equals(arvore.get("truncated"));
        List<ArquivoSistemaItemView> arquivos = new ArrayList<>();
        int pastas = 0;
        Object treeObj = arvore.get("tree");
        if (treeObj instanceof List<?> tree) {
            for (Object itemObj : tree) {
                if (!(itemObj instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> no = mapa(itemObj);
                String tipo = texto(no.get("type"));
                String caminho = texto(no.get("path"));
                if ("tree".equals(tipo)) {
                    pastas++;
                    continue;
                }
                if (!"blob".equals(tipo) || caminho.isBlank()) {
                    continue;
                }
                long tamanho = numero(no.get("size"));
                arquivos.add(new ArquivoSistemaItemView(
                        caminho,
                        tipo,
                        tamanho,
                        formatarTamanho(tamanho),
                        properties.resolverUrlArquivoNoGitHub(caminho),
                        properties.resolverUrlRaw(caminho)
                ));
            }
        }

        arquivos.sort(Comparator.comparing(ArquivoSistemaItemView::getCaminho, String.CASE_INSENSITIVE_ORDER));

        return new ArquivoSistemaResumoView(
                properties.resolverUrlRepositorio(),
                properties.resolverUrlDownloadZip(),
                branch,
                encurtarSha(commitSha),
                commitMensagem.isBlank() ? "Sem mensagem" : commitMensagem,
                commitDataLabel,
                arquivos.size(),
                pastas,
                truncada,
                arquivos
        );
    }

    private Map<String, Object> buscarMap(String url) {
        try {
            ResponseEntity<Map<String, Object>> resposta = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headersGitHub()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            Map<String, Object> corpo = resposta.getBody();
            if (corpo == null) {
                throw new ArquivoSistemaIndisponivelException("Resposta vazia do GitHub.");
            }
            return corpo;
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 403) {
                throw new ArquivoSistemaIndisponivelException(
                        "Limite de consultas ao GitHub atingido. Aguarde alguns minutos ou configure um token de acesso."
                );
            }
            throw new ArquivoSistemaIndisponivelException(
                    "Nao foi possivel consultar o GitHub agora. Tente novamente em alguns minutos.",
                    e
            );
        } catch (ArquivoSistemaIndisponivelException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ArquivoSistemaIndisponivelException(
                    "Nao foi possivel consultar o GitHub agora. Tente novamente em alguns minutos.",
                    e
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapa(Object valor) {
        if (valor instanceof Map<?, ?> mapaBruto) {
            return (Map<String, Object>) mapaBruto;
        }
        return Map.of();
    }

    private String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private long numero(Object valor) {
        if (valor instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(texto(valor));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private HttpHeaders headersGitHub() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.set("User-Agent", "Agenda-Afetto-Arquivo-Sistema");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        String token = properties.getGithubToken();
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token.trim());
        }
        return headers;
    }

    private long cacheSegundos() {
        int minutos = properties.getCacheMinutos();
        if (minutos < 1) {
            return 60L;
        }
        return minutos * 60L;
    }

    private String encurtarSha(String sha) {
        if (sha == null || sha.length() < 7) {
            return sha != null ? sha : "";
        }
        return sha.substring(0, 7);
    }

    private String formatarDataCommit(String iso) {
        if (iso == null || iso.isBlank()) {
            return "—";
        }
        try {
            return DATA_COMMIT.format(Instant.parse(iso));
        } catch (RuntimeException e) {
            return iso;
        }
    }

    private String formatarTamanho(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.forLanguageTag("pt-BR"), "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private record CachedResumo(ArquivoSistemaResumoView resumo, Instant expiraEm) {
        boolean expirado() {
            return Instant.now().isAfter(expiraEm);
        }
    }
}
