package com.clinica.sistema.dto;

import lombok.Getter;

@Getter
public class LinkPagamentoGerado {

    private final String orderNsu;
    private final String linkPagamento;
    private final String slug;

    public LinkPagamentoGerado(String orderNsu, String linkPagamento, String slug) {
        this.orderNsu = orderNsu;
        this.linkPagamento = linkPagamento;
        this.slug = slug;
    }
}
