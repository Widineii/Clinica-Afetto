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
public class RelatorioProfissionalMesView {

    private static final DateTimeFormatter MES_ANO_LABEL =
            DateTimeFormatter.ofPattern("MMMM 'de' yyyy", new Locale("pt", "BR"));

    private final YearMonth mesSelecionado;
    private final String mesAnoLabel;
    private final String mesAnoInput;
    private final String profissionalNome;
    private final int totalAtendimentos;
    private final int totalPixPagos;
    private final BigDecimal totalTaxasPagas;
    private final String totalTaxasPagasFormatado;
    private final String melhorMesLabel;
    private final int atendimentosMelhorMes;
    private final BigDecimal valorMelhorMes;
    private final String valorMelhorMesFormatado;
    private final List<RelatorioProfissionalAtendimentoView> atendimentos;
    private final List<ReceitaPixLinhaView> pagamentosPix;
    private final String graficoSalasJson;
    private final String graficoTiposJson;
    private final String pagamentosGraficoJson;
    private final String salaFiltro;
    private final String salaFiltroRotulo;
    private final boolean exibirTaxas;
    private final boolean exibirGanhosConsulta;
    private final BigDecimal totalGanhosMes;
    private final String totalGanhosMesFormatado;
    private final int consultasComValorGanhos;

    public static RelatorioProfissionalMesView vazio(YearMonth mes, String profissionalNome) {
        return vazio(mes, profissionalNome, null, true, false);
    }

    public static RelatorioProfissionalMesView vazio(YearMonth mes, String profissionalNome, String salaFiltro) {
        return vazio(mes, profissionalNome, salaFiltro, true, false);
    }

    public static RelatorioProfissionalMesView vazio(
            YearMonth mes,
            String profissionalNome,
            String salaFiltro,
            boolean exibirTaxas
    ) {
        return vazio(mes, profissionalNome, salaFiltro, exibirTaxas, false);
    }

    public static RelatorioProfissionalMesView vazio(
            YearMonth mes,
            String profissionalNome,
            String salaFiltro,
            boolean exibirTaxas,
            boolean exibirGanhosConsulta
    ) {
        return new RelatorioProfissionalMesView(
                mes,
                profissionalNome,
                0,
                0,
                BigDecimal.ZERO,
                "—",
                0,
                BigDecimal.ZERO,
                Collections.emptyList(),
                Collections.emptyList(),
                "[]",
                "[]",
                "[]",
                salaFiltro,
                exibirTaxas,
                exibirGanhosConsulta,
                BigDecimal.ZERO,
                0
        );
    }

    public RelatorioProfissionalMesView(
            YearMonth mesSelecionado,
            String profissionalNome,
            int totalAtendimentos,
            int totalPixPagos,
            BigDecimal totalTaxasPagas,
            String melhorMesLabel,
            int atendimentosMelhorMes,
            BigDecimal valorMelhorMes,
            List<RelatorioProfissionalAtendimentoView> atendimentos,
            List<ReceitaPixLinhaView> pagamentosPix,
            String graficoSalasJson,
            String graficoTiposJson,
            String pagamentosGraficoJson,
            String salaFiltro
    ) {
        this(
                mesSelecionado,
                profissionalNome,
                totalAtendimentos,
                totalPixPagos,
                totalTaxasPagas,
                melhorMesLabel,
                atendimentosMelhorMes,
                valorMelhorMes,
                atendimentos,
                pagamentosPix,
                graficoSalasJson,
                graficoTiposJson,
                pagamentosGraficoJson,
                salaFiltro,
                true,
                false,
                BigDecimal.ZERO,
                0
        );
    }

    public RelatorioProfissionalMesView(
            YearMonth mesSelecionado,
            String profissionalNome,
            int totalAtendimentos,
            int totalPixPagos,
            BigDecimal totalTaxasPagas,
            String melhorMesLabel,
            int atendimentosMelhorMes,
            BigDecimal valorMelhorMes,
            List<RelatorioProfissionalAtendimentoView> atendimentos,
            List<ReceitaPixLinhaView> pagamentosPix,
            String graficoSalasJson,
            String graficoTiposJson,
            String pagamentosGraficoJson,
            String salaFiltro,
            boolean exibirTaxas,
            boolean exibirGanhosConsulta,
            BigDecimal totalGanhosMes,
            int consultasComValorGanhos
    ) {
        this.mesSelecionado = mesSelecionado;
        this.mesAnoLabel = capitalize(mesSelecionado.format(MES_ANO_LABEL));
        this.mesAnoInput = mesSelecionado.toString();
        this.profissionalNome = profissionalNome != null ? profissionalNome : "Profissional";
        this.totalAtendimentos = totalAtendimentos;
        this.totalPixPagos = totalPixPagos;
        this.totalTaxasPagas = totalTaxasPagas != null ? totalTaxasPagas : BigDecimal.ZERO;
        this.totalTaxasPagasFormatado = MoedaBrasilUtil.formatar(this.totalTaxasPagas);
        this.melhorMesLabel = melhorMesLabel != null && !melhorMesLabel.isBlank() ? melhorMesLabel : "—";
        this.atendimentosMelhorMes = atendimentosMelhorMes;
        this.valorMelhorMes = valorMelhorMes != null ? valorMelhorMes : BigDecimal.ZERO;
        this.valorMelhorMesFormatado = MoedaBrasilUtil.formatar(this.valorMelhorMes);
        this.atendimentos = atendimentos != null ? atendimentos : Collections.emptyList();
        this.pagamentosPix = pagamentosPix != null ? pagamentosPix : Collections.emptyList();
        this.graficoSalasJson = graficoSalasJson != null ? graficoSalasJson : "[]";
        this.graficoTiposJson = graficoTiposJson != null ? graficoTiposJson : "[]";
        this.pagamentosGraficoJson = pagamentosGraficoJson != null ? pagamentosGraficoJson : "[]";
        this.salaFiltro = salaFiltro != null ? salaFiltro : "";
        this.salaFiltroRotulo = this.salaFiltro.isBlank() ? "Todas as salas" : this.salaFiltro;
        this.exibirTaxas = exibirTaxas;
        this.exibirGanhosConsulta = exibirGanhosConsulta;
        this.totalGanhosMes = totalGanhosMes != null ? totalGanhosMes : BigDecimal.ZERO;
        this.totalGanhosMesFormatado = MoedaBrasilUtil.formatar(this.totalGanhosMes);
        this.consultasComValorGanhos = consultasComValorGanhos;
    }

    private static String capitalize(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }
}
