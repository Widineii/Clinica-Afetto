package com.clinica.sistema.service;

import com.clinica.sistema.config.ArquivoSistemaGitHubProperties;
import com.clinica.sistema.dto.ArquivoSistemaBreadcrumbView;
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
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ArquivoSistemaGitHubService {

    private static final Pattern LINK_LAST_PAGE = Pattern.compile("[?&]page=(\\d+)>;\\s*rel=\"last\"");
    private static final int MAX_ENTRADAS_COM_COMMIT = 120;

    private final ArquivoSistemaGitHubProperties properties;
    private final RestTemplate restTemplate;
    private final Map<String, CachedResumo> cachePorDiretorio = new ConcurrentHashMap<>();
    private final Map<String, ArquivoSistemaResumoView> ultimoResumoBom = new ConcurrentHashMap<>();
    private final Map<String, String[]> commitPorCaminho = new ConcurrentHashMap<>();

    public ArquivoSistemaGitHubService(ArquivoSistemaGitHubProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    public ArquivoSistemaResumoView navegar(String diretorio, boolean forcarAtualizacao) {
        String dir = normalizarDiretorio(diretorio);
        if (forcarAtualizacao) {
            cachePorDiretorio.remove(dir);
        } else {
            CachedResumo cached = cachePorDiretorio.get(dir);
            if (cached != null && !cached.expirado()) {
                return cached.resumo();
            }
        }

        try {
            ArquivoSistemaResumoView resumo = montarNavegacao(dir);
            cachePorDiretorio.put(dir, new CachedResumo(resumo, Instant.now().plusSeconds(cacheSegundos())));
            ultimoResumoBom.put(dir, resumo);
            return resumo;
        } catch (ArquivoSistemaIndisponivelException e) {
            ArquivoSistemaResumoView bom = ultimoResumoBom.get(dir);
            if (bom != null) {
                return bom;
            }
            throw e;
        }
    }

    /** Resumo minimo para a tela continuar abrindo mesmo sem conseguir falar com o GitHub. */
    public ArquivoSistemaResumoView resumoIndisponivel(String diretorio) {
        String dir = normalizarDiretorio(diretorio);
        ArquivoSistemaResumoView bom = ultimoResumoBom.get(dir);
        if (bom != null) {
            return bom;
        }
        String branch = properties.getGithubBranch();
        return new ArquivoSistemaResumoView(
                properties.resolverUrlRepositorio(),
                properties.resolverUrlDownloadZip(),
                branch,
                dir,
                dir.isEmpty(),
                calcularPai(dir),
                montarBreadcrumb(dir),
                "",
                "Indisponivel no momento",
                "",
                "",
                0,
                0,
                0,
                dir.isEmpty()
                        ? properties.resolverUrlRepositorio()
                        : properties.resolverUrlRepositorio() + "/tree/" + branch + "/" + dir,
                List.of()
        );
    }

    public String buscarConteudoArquivo(String caminhoRelativo) {
        validarCaminho(caminhoRelativo);
        String url = properties.resolverUrlRaw(caminhoRelativo);
        try {
            ResponseEntity<byte[]> resposta = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headersGitHub()), byte[].class
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
                    "Nao foi possivel carregar o arquivo no GitHub. Tente novamente em alguns minutos.", e
            );
        } catch (ArquivoSistemaIndisponivelException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ArquivoSistemaIndisponivelException(
                    "Nao foi possivel carregar o arquivo no GitHub. Tente novamente em alguns minutos.", e
            );
        }
    }

    public void validarCaminho(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            throw new ArquivoSistemaIndisponivelException("Informe o caminho do arquivo.");
        }
        String valor = caminho.trim();
        if (valor.startsWith("/") || valor.contains("..") || valor.contains("\\")) {
            throw new ArquivoSistemaIndisponivelException("Caminho invalido.");
        }
    }

    public String normalizarDiretorio(String diretorio) {
        if (diretorio == null) {
            return "";
        }
        String valor = diretorio.trim();
        if (valor.isEmpty() || "/".equals(valor)) {
            return "";
        }
        if (valor.contains("..") || valor.contains("\\")) {
            throw new ArquivoSistemaIndisponivelException("Caminho invalido.");
        }
        while (valor.startsWith("/")) {
            valor = valor.substring(1);
        }
        while (valor.endsWith("/")) {
            valor = valor.substring(0, valor.length() - 1);
        }
        return valor;
    }

    private ArquivoSistemaResumoView montarNavegacao(String dir) {
        String branch = properties.getGithubBranch();

        Map<String, Object> headCommit = buscarMap(urlApi("/commits/" + branch));
        String commitSha = texto(headCommit.get("sha"));
        Map<String, Object> commitDetalhe = mapa(headCommit.get("commit"));
        String commitMensagem = primeiraLinha(texto(commitDetalhe.get("message")));
        Map<String, Object> autor = mapa(commitDetalhe.get("author"));
        String commitAutor = texto(autor.get("name"));
        String commitRelativo = relativo(texto(mapa(commitDetalhe.get("committer")).get("date")));

        int totalCommits = contarCommits(branch);

        String contentsUrl = dir.isEmpty()
                ? urlApi("/contents?ref=" + branch)
                : urlApi("/contents/" + codificarCaminho(dir) + "?ref=" + branch);
        List<Map<String, Object>> entradas = buscarLista(contentsUrl);

        List<ArquivoSistemaItemView> itens = new ArrayList<>();
        int pastas = 0;
        int arquivos = 0;
        boolean buscarCommits = entradas.size() <= MAX_ENTRADAS_COM_COMMIT;
        int orcamentoConsultas = 30;
        boolean limiteAtingido = false;

        for (Map<String, Object> entrada : entradas) {
            String tipo = texto(entrada.get("type"));
            String caminho = texto(entrada.get("path"));
            String nome = texto(entrada.get("name"));
            boolean diretorio = "dir".equals(tipo);
            if (diretorio) {
                pastas++;
            } else if ("file".equals(tipo)) {
                arquivos++;
            } else {
                continue;
            }

            String commitMsg = "";
            String commitRel = "";
            String chaveCache = branch + ":" + caminho;
            String[] emCache = commitPorCaminho.get(chaveCache);
            if (emCache != null) {
                commitMsg = emCache[0];
                commitRel = emCache[1];
            } else if (buscarCommits && !limiteAtingido && orcamentoConsultas > 0) {
                orcamentoConsultas--;
                Map<String, Object> ultimo = ultimoCommitDoCaminho(caminho, branch);
                if (ultimo != null) {
                    Map<String, Object> detalhe = mapa(ultimo.get("commit"));
                    commitMsg = primeiraLinha(texto(detalhe.get("message")));
                    commitRel = relativo(texto(mapa(detalhe.get("committer")).get("date")));
                    commitPorCaminho.put(chaveCache, new String[]{commitMsg, commitRel});
                } else {
                    limiteAtingido = true;
                }
            }

            itens.add(new ArquivoSistemaItemView(
                    nome,
                    caminho,
                    diretorio,
                    diretorio ? "" : formatarTamanho(numero(entrada.get("size"))),
                    commitMsg,
                    commitRel,
                    diretorio
                            ? properties.resolverUrlRepositorio() + "/tree/" + branch + "/" + caminho
                            : properties.resolverUrlArquivoNoGitHub(caminho)
            ));
        }

        itens.sort(Comparator
                .comparing(ArquivoSistemaItemView::isDiretorio, Comparator.reverseOrder())
                .thenComparing(ArquivoSistemaItemView::getNome, String.CASE_INSENSITIVE_ORDER));

        return new ArquivoSistemaResumoView(
                properties.resolverUrlRepositorio(),
                properties.resolverUrlDownloadZip(),
                branch,
                dir,
                dir.isEmpty(),
                calcularPai(dir),
                montarBreadcrumb(dir),
                encurtarSha(commitSha),
                commitMensagem.isBlank() ? "Sem mensagem" : commitMensagem,
                commitAutor,
                commitRelativo,
                totalCommits,
                pastas,
                arquivos,
                dir.isEmpty()
                        ? properties.resolverUrlRepositorio()
                        : properties.resolverUrlRepositorio() + "/tree/" + branch + "/" + dir,
                itens
        );
    }

    private Map<String, Object> ultimoCommitDoCaminho(String caminho, String branch) {
        try {
            List<Map<String, Object>> commits = buscarLista(
                    urlApi("/commits?sha=" + branch + "&per_page=1&path=" + codificarCaminho(caminho))
            );
            return commits.isEmpty() ? null : commits.get(0);
        } catch (ArquivoSistemaIndisponivelException e) {
            return null;
        }
    }

    private int contarCommits(String branch) {
        try {
            ResponseEntity<List<Map<String, Object>>> resposta = restTemplate.exchange(
                    urlApi("/commits?sha=" + branch + "&per_page=1"),
                    HttpMethod.GET,
                    new HttpEntity<>(headersGitHub()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            String link = resposta.getHeaders().getFirst("Link");
            if (link != null) {
                Matcher matcher = LINK_LAST_PAGE.matcher(link);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
            List<Map<String, Object>> corpo = resposta.getBody();
            return corpo != null ? corpo.size() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private List<ArquivoSistemaBreadcrumbView> montarBreadcrumb(String dir) {
        List<ArquivoSistemaBreadcrumbView> crumbs = new ArrayList<>();
        crumbs.add(new ArquivoSistemaBreadcrumbView(properties.getGithubRepo(), ""));
        if (dir.isEmpty()) {
            return crumbs;
        }
        String[] partes = dir.split("/");
        StringBuilder acumulado = new StringBuilder();
        for (String parte : partes) {
            if (parte.isBlank()) {
                continue;
            }
            if (acumulado.length() > 0) {
                acumulado.append('/');
            }
            acumulado.append(parte);
            crumbs.add(new ArquivoSistemaBreadcrumbView(parte, acumulado.toString()));
        }
        return crumbs;
    }

    private String calcularPai(String dir) {
        if (dir == null || dir.isEmpty()) {
            return "";
        }
        int corte = dir.lastIndexOf('/');
        return corte < 0 ? "" : dir.substring(0, corte);
    }

    private Map<String, Object> buscarMap(String url) {
        try {
            ResponseEntity<Map<String, Object>> resposta = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headersGitHub()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            Map<String, Object> corpo = resposta.getBody();
            if (corpo == null) {
                throw new ArquivoSistemaIndisponivelException("Resposta vazia do GitHub.");
            }
            return corpo;
        } catch (HttpStatusCodeException e) {
            throw traduzirErro(e);
        } catch (ArquivoSistemaIndisponivelException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ArquivoSistemaIndisponivelException(
                    "Nao foi possivel consultar o GitHub agora. Tente novamente em alguns minutos.", e
            );
        }
    }

    private List<Map<String, Object>> buscarLista(String url) {
        try {
            ResponseEntity<List<Map<String, Object>>> resposta = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headersGitHub()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            List<Map<String, Object>> corpo = resposta.getBody();
            return corpo != null ? corpo : List.of();
        } catch (HttpStatusCodeException e) {
            throw traduzirErro(e);
        } catch (RuntimeException e) {
            throw new ArquivoSistemaIndisponivelException(
                    "Nao foi possivel consultar o GitHub agora. Tente novamente em alguns minutos.", e
            );
        }
    }

    private ArquivoSistemaIndisponivelException traduzirErro(HttpStatusCodeException e) {
        if (e.getStatusCode().value() == 403) {
            return new ArquivoSistemaIndisponivelException(
                    "Limite de consultas ao GitHub atingido. Aguarde alguns minutos ou configure um token de acesso."
            );
        }
        if (e.getStatusCode().value() == 404) {
            return new ArquivoSistemaIndisponivelException("Pasta ou arquivo nao encontrado no GitHub.");
        }
        return new ArquivoSistemaIndisponivelException(
                "Nao foi possivel consultar o GitHub agora. Tente novamente em alguns minutos.", e
        );
    }

    private String urlApi(String sufixo) {
        return "https://api.github.com/repos/"
                + properties.getGithubOwner() + "/" + properties.getGithubRepo() + sufixo;
    }

    private String codificarCaminho(String caminho) {
        return UriUtils.encodePath(caminho, StandardCharsets.UTF_8);
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
        return minutos < 1 ? 60L : minutos * 60L;
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

    private String primeiraLinha(String mensagem) {
        if (mensagem == null) {
            return "";
        }
        String texto = mensagem.strip();
        int quebra = texto.indexOf('\n');
        return quebra < 0 ? texto : texto.substring(0, quebra).strip();
    }

    private String encurtarSha(String sha) {
        if (sha == null || sha.length() < 7) {
            return sha != null ? sha : "";
        }
        return sha.substring(0, 7);
    }

    private String relativo(String iso) {
        if (iso == null || iso.isBlank()) {
            return "";
        }
        try {
            Instant momento = Instant.parse(iso);
            long segundos = Duration.between(momento, Instant.now()).getSeconds();
            if (segundos < 0) {
                segundos = 0;
            }
            if (segundos < 60) {
                return "agora mesmo";
            }
            long minutos = segundos / 60;
            if (minutos < 60) {
                return "há " + minutos + (minutos == 1 ? " minuto" : " minutos");
            }
            long horas = minutos / 60;
            if (horas < 24) {
                return "há " + horas + (horas == 1 ? " hora" : " horas");
            }
            long dias = horas / 24;
            if (dias == 1) {
                return "ontem";
            }
            if (dias < 30) {
                return "há " + dias + " dias";
            }
            long meses = dias / 30;
            if (meses == 1) {
                return "mês passado";
            }
            if (meses < 12) {
                return "há " + meses + " meses";
            }
            long anos = dias / 365;
            if (anos <= 1) {
                return "ano passado";
            }
            return "há " + anos + " anos";
        } catch (RuntimeException e) {
            return "";
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
