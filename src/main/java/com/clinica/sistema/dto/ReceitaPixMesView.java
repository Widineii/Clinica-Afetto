package com.clinica.sistema.dto;

import com.clinica.sistema.util.MoedaBrasilUtil;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Getter
public class ReceitaPixMesView {

    private static final DateTimeFormatter MES_ANO_LABEL =
            DateTimeFormatter.ofPattern("MMMM 'de' yyyy", new Locale("pt", "BR"));

    private final YearMonth mesSelecionado;
    private final String mesAnoLabel;
    private final String mesAnoInput;
    private final int mes;
    private final int ano;
    private final List<ReceitaPixLinhaView> pagamentos;
    private final int quantidadePagamentos;
    private final BigDecimal totalRecebido;
    private final String totalRecebidoFormatado;

    public static ReceitaPixMesView vazio(YearMonth mesSelecionado) {
        return new ReceitaPixMesView(mesSelecionado, Collections.emptyList(), BigDecimal.ZERO);
    }

    public ReceitaPixMesView(YearMonth mesSelecionado, List<ReceitaPixLinhaView> pagamentos, BigDecimal totalRecebido) {
        this.mesSelecionado = mesSelecionado;
        this.mesAnoLabel = capitalize(mesSelecionado.format(MES_ANO_LABEL));
        this.mesAnoInput = mesSelecionado.toString();
        this.mes = mesSelecionado.getMonthValue();
        this.ano = mesSelecionado.getYear();
        this.pagamentos = pagamentos;
        this.quantidadePagamentos = pagamentos.size();
        this.totalRecebido = totalRecebido;
        this.totalRecebidoFormatado = MoedaBrasilUtil.formatar(totalRecebido);
    }

    private static String capitalize(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }
}
