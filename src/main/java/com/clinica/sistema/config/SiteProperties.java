package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Versao exibida no rodape do site. Atualize {@code app.site.version} a cada deploy.
 * Faixa permitida: 2.xx ate 2.99. {@code 3.0} fica reservado para o redesign visual do projeto.
 */
@Component
@ConfigurationProperties(prefix = "app.site")
public class SiteProperties {

    /** Formato esperado: 2.xxx (ex.: 2.478). Maximo 2.999 — 3.0 e o redesign visual. */
    private String version = "2.478";

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getRotuloExibicao() {
        String valor = version != null ? version.trim() : "";
        if (valor.isEmpty()) {
            return "";
        }
        return "Versão do site " + valor;
    }
}
