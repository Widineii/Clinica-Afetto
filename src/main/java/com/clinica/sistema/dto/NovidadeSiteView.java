package com.clinica.sistema.dto;

import com.clinica.sistema.model.NovidadePublicoAlvo;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
public class NovidadeSiteView {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Long id;
    private final String versao;
    private final String titulo;
    private final String descricao;
    private final NovidadePublicoAlvo publicoAlvo;
    private final String publicoAlvoRotulo;
    private final String dataPublicacaoRotulo;

    public NovidadeSiteView(
            Long id,
            String versao,
            String titulo,
            String descricao,
            NovidadePublicoAlvo publicoAlvo,
            LocalDateTime publicadaEm
    ) {
        this.id = id;
        this.versao = versao;
        this.titulo = titulo;
        this.descricao = descricao;
        this.publicoAlvo = publicoAlvo;
        this.publicoAlvoRotulo = publicoAlvo != null ? publicoAlvo.getRotulo() : "";
        this.dataPublicacaoRotulo = publicadaEm != null ? publicadaEm.format(FORMATO_DATA) : "";
    }
}
