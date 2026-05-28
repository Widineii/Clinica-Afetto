package com.clinica.sistema.dto;

import com.clinica.sistema.model.Usuario;
import lombok.Getter;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Getter
public class FinanceiroFiltroMesProfissionalView {

    private static final DateTimeFormatter MES_ANO_CURTO =
            DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("pt", "BR"));

    private final String mesAnoInput;
    private final String mesAnoLabelCurto;
    private final int mes;
    private final int ano;
    private final Long profissionalId;
    private final String profissionalNome;
    private final List<Usuario> profissionais;

    public FinanceiroFiltroMesProfissionalView(
            YearMonth mesSelecionado,
            Usuario profissionalSelecionado,
            List<Usuario> profissionais
    ) {
        this.mesAnoInput = mesSelecionado.toString();
        this.mesAnoLabelCurto = mesSelecionado.format(MES_ANO_CURTO).toLowerCase(Locale.ROOT);
        this.mes = mesSelecionado.getMonthValue();
        this.ano = mesSelecionado.getYear();
        this.profissionalId = profissionalSelecionado != null ? profissionalSelecionado.getId() : null;
        this.profissionalNome = profissionalSelecionado != null ? profissionalSelecionado.getNome() : null;
        this.profissionais = profissionais;
    }
}
