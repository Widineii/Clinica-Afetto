package com.clinica.sistema.dto;

import com.clinica.sistema.util.MoedaBrasilUtil;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Getter
public class DespesaResumoMesView {

    private static final DateTimeFormatter MES_ANO_LABEL =
            DateTimeFormatter.ofPattern("MMMM 'de' yyyy", new Locale("pt", "BR"));

    private final YearMonth mesSelecionado;
    private final String mesAnoLabel;
    private final String mesAnoInput;
    private final int mes;
    private final int ano;
    private final List<DespesaLinhaView> despesasMensais;
    private final List<DespesaLinhaView> despesasUnicas;
    private final BigDecimal totalMensais;
    private final BigDecimal totalUnicas;
    private final BigDecimal totalGeral;
    private final String totalMensaisFormatado;
    private final String totalUnicasFormatado;
    private final String totalGeralFormatado;

    public DespesaResumoMesView(
            YearMonth mesSelecionado,
            List<DespesaLinhaView> despesasMensais,
            List<DespesaLinhaView> despesasUnicas,
            BigDecimal totalMensais,
            BigDecimal totalUnicas
    ) {
        this.mesSelecionado = mesSelecionado;
        this.mesAnoLabel = capitalize(mesSelecionado.format(MES_ANO_LABEL));
        this.mesAnoInput = mesSelecionado.toString();
        this.mes = mesSelecionado.getMonthValue();
        this.ano = mesSelecionado.getYear();
        this.despesasMensais = despesasMensais;
        this.despesasUnicas = despesasUnicas;
        this.totalMensais = totalMensais;
        this.totalUnicas = totalUnicas;
        this.totalGeral = totalMensais.add(totalUnicas);
        this.totalMensaisFormatado = MoedaBrasilUtil.formatar(totalMensais);
        this.totalUnicasFormatado = MoedaBrasilUtil.formatar(totalUnicas);
        this.totalGeralFormatado = MoedaBrasilUtil.formatar(totalGeral);
    }

    private static String capitalize(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }
}
