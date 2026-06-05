package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConfigurationProperties(prefix = "app.manual")
public class ManualProperties {

    /**
     * URL do video: link embed do YouTube (https://www.youtube.com/embed/...)
     * ou arquivo em /videos/... servido pelo proprio sistema.
     */
    private String videoUrl = "";

    private String videoTitulo = "Como usar a Agenda Afetto (video de amostra)";

    private String videoDescricao =
            "Assista ao passo a passo antes de ler as regras. Este video e uma amostra — a clinica pode trocar pelo tutorial oficial.";

    /** Apenas digitos, com DDI (ex.: 5511999999999). */
    private String whatsappNumero = "";

    private String whatsappMensagemPadrao =
            "Ola! Preciso de suporte com a Agenda Afetto.";

    private static final Pattern YOUTUBE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{11}$");

    public String getVideoUrl() {
        return videoUrl;
    }

    /** URL pronta para iframe (embed) ou caminho local. */
    public String getVideoUrlNormalizada() {
        if (!temVideoConfigurado()) {
            return "";
        }
        String url = videoUrl.trim();
        String youtubeId = extrairIdYoutube(url);
        if (youtubeId != null) {
            return "https://www.youtube.com/embed/" + youtubeId + "?rel=0&modestbranding=1";
        }
        return url;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getVideoTitulo() {
        return videoTitulo;
    }

    public void setVideoTitulo(String videoTitulo) {
        this.videoTitulo = videoTitulo;
    }

    public String getVideoDescricao() {
        return videoDescricao;
    }

    public void setVideoDescricao(String videoDescricao) {
        this.videoDescricao = videoDescricao;
    }

    public boolean temVideoConfigurado() {
        return videoUrl != null && !videoUrl.isBlank();
    }

    /**
     * {@code nenhum}, {@code embed} (YouTube/Vimeo URL) ou {@code arquivo} (caminho local).
     */
    public String resolverModoVideo() {
        if (!temVideoConfigurado()) {
            return "demo";
        }
        String url = videoUrl.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return "embed";
        }
        return "arquivo";
    }

    private String extrairIdYoutube(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            if (host.contains("youtu.be")) {
                String path = uri.getPath();
                if (path != null && path.length() > 1) {
                    return validarIdYoutube(path.substring(1).split("/")[0]);
                }
            }
            if (host.contains("youtube.com")) {
                String path = uri.getPath() != null ? uri.getPath() : "";
                if (path.startsWith("/embed/")) {
                    return validarIdYoutube(path.substring("/embed/".length()).split("/")[0]);
                }
                if (path.startsWith("/shorts/")) {
                    return validarIdYoutube(path.substring("/shorts/".length()).split("/")[0]);
                }
                String id = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("v");
                return validarIdYoutube(id);
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?:v=|/embed/|youtu\\.be/|/shorts/)([a-zA-Z0-9_-]{11})").matcher(url);
        if (matcher.find()) {
            return validarIdYoutube(matcher.group(1));
        }
        return null;
    }

    private String validarIdYoutube(String id) {
        if (id == null || !YOUTUBE_ID_PATTERN.matcher(id).matches()) {
            return null;
        }
        return id;
    }

    public String getWhatsappNumero() {
        return whatsappNumero;
    }

    public void setWhatsappNumero(String whatsappNumero) {
        this.whatsappNumero = whatsappNumero;
    }

    public String getWhatsappMensagemPadrao() {
        return whatsappMensagemPadrao;
    }

    public void setWhatsappMensagemPadrao(String whatsappMensagemPadrao) {
        this.whatsappMensagemPadrao = whatsappMensagemPadrao;
    }

    public boolean temWhatsappSuporte() {
        return normalizarNumeroWhatsapp(whatsappNumero) != null;
    }

    public String resolverLinkWhatsapp() {
        String numero = normalizarNumeroWhatsapp(whatsappNumero);
        if (numero == null) {
            return "";
        }
        String mensagem = whatsappMensagemPadrao != null ? whatsappMensagemPadrao.trim() : "";
        if (mensagem.isBlank()) {
            return "https://wa.me/" + numero;
        }
        return "https://wa.me/" + numero + "?text="
                + UriUtils.encode(mensagem, StandardCharsets.UTF_8);
    }

    /** Ex.: 553182835857 → (31) 8283-5857 */
    public String resolverRotuloWhatsappExibicao() {
        String numero = normalizarNumeroWhatsapp(whatsappNumero);
        if (numero == null) {
            return "";
        }
        if (numero.startsWith("55") && numero.length() >= 12) {
            String ddd = numero.substring(2, 4);
            String local = numero.substring(4);
            if (local.length() == 8) {
                return "(" + ddd + ") " + local.substring(0, 4) + "-" + local.substring(4);
            }
            if (local.length() == 9) {
                return "(" + ddd + ") " + local.substring(0, 5) + "-" + local.substring(5);
            }
        }
        return numero;
    }

    private String normalizarNumeroWhatsapp(String numeroBruto) {
        if (numeroBruto == null || numeroBruto.isBlank()) {
            return null;
        }
        String apenasDigitos = numeroBruto.replaceAll("\\D", "");
        if (apenasDigitos.length() < 10) {
            return null;
        }
        return apenasDigitos;
    }
}
