package com.clinica.sistema.dto;

import com.clinica.sistema.model.Despesa;
import lombok.Getter;

@Getter
public class DespesaLinhaView {

    private final Long id;
    private final String descricao;
    private final String valorFormatado;
    private final String dataRotulo;

    public DespesaLinhaView(Despesa despesa) {
        this.id = despesa.getId();
        this.descricao = despesa.getDescricao();
        this.valorFormatado = despesa.getValorFormatado();
        this.dataRotulo = despesa.getDataReferenciaFormatada();
    }
}
