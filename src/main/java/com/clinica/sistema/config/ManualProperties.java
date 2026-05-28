package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

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

    public String getVideoUrl() {
        return videoUrl;
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
            return "nenhum";
        }
        String url = videoUrl.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return "embed";
        }
        return "arquivo";
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
