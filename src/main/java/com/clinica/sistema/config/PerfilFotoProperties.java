package com.clinica.sistema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "app.perfil.foto")
public class PerfilFotoProperties {

    private String diretorio = "./data/uploads/perfis";
    private long tamanhoMaxBytes = 2 * 1024 * 1024;

    public Path resolverDiretorio() {
        return Path.of(diretorio).toAbsolutePath().normalize();
    }

    public String getDiretorio() {
        return diretorio;
    }

    public void setDiretorio(String diretorio) {
        this.diretorio = diretorio;
    }

    public long getTamanhoMaxBytes() {
        return tamanhoMaxBytes;
    }

    public void setTamanhoMaxBytes(long tamanhoMaxBytes) {
        this.tamanhoMaxBytes = tamanhoMaxBytes;
    }
}
