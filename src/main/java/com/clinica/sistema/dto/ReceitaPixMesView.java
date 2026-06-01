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
    private final List<ReceitaPixFatiaView> receitaPorSala;
    private final List<ReceitaPixFatiaView> receitaPorProfissional;
    private final List<ReceitaPixFatiaView> receitaPorTipo;
    private final List<String> salasFiltro;
    private final List<String> profissionaisFiltro;
    private final String pagamentosGraficoJson;
    private final List<ProfissionalReceitaPainelView> profissionaisPainel;
    private final String profissionaisPainelJson;
    private final List<ReceitaPendenteLinhaView> pendentes;
    private final int quantidadePendentes;
    private final BigDecimal totalAReceber;
    private final String totalAReceberFormatado;
    private final String pendentesGraficoJson;

    public static ReceitaPixMesView vazio(YearMonth mesSelecionado) {
        return new ReceitaPixMesView(
                mesSelecionado,
                Collections.emptyList(),
                BigDecimal.ZERO,
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                BigDecimal.ZERO
        );
    }

    public ReceitaPixMesView(
            YearMonth mesSelecionado,
            List<ReceitaPixLinhaView> pagamentos,
            BigDecimal totalRecebido,
            List<ProfissionalReceitaPainelView> profissionaisPainel
    ) {
        this(mesSelecionado, pagamentos, totalRecebido, profissionaisPainel, null, Collections.emptyList(), BigDecimal.ZERO);
    }

    public ReceitaPixMesView(
            YearMonth mesSelecionado,
            List<ReceitaPixLinhaView> pagamentos,
            BigDecimal totalRecebido,
            List<ProfissionalReceitaPainelView> profissionaisPainel,
            List<String> salasFiltro,
            List<ReceitaPendenteLinhaView> pendentes,
            BigDecimal totalAReceber
    ) {
        this.mesSelecionado = mesSelecionado;
        this.mesAnoLabel = capitalize(mesSelecionado.format(MES_ANO_LABEL));
        this.mesAnoInput = mesSelecionado.toString();
        this.mes = mesSelecionado.getMonthValue();
        this.ano = mesSelecionado.getYear();
        this.pagamentos = pagamentos;
        this.quantidadePagamentos = pagamentos.size();
        this.totalRecebido = totalRecebido;
        this.totalRecebidoFormatado = MoedaBrasilUtil.formatar(totalRecebido);
        this.receitaPorSala = ReceitaPixAgregador.porSala(pagamentos, totalRecebido);
        this.receitaPorProfissional = ReceitaPixAgregador.porProfissional(pagamentos, totalRecebido);
        this.receitaPorTipo = ReceitaPixAgregador.porTipo(pagamentos, totalRecebido);
        this.salasFiltro = salasFiltro != null && !salasFiltro.isEmpty()
                ? salasFiltro
                : ReceitaPixAgregador.chavesDistintas(pagamentos, ReceitaPixLinhaView::getSalaChave);
        this.profissionaisFiltro = ReceitaPixAgregador.chavesDistintas(pagamentos, ReceitaPixLinhaView::getProfissionalChave);
        this.pagamentosGraficoJson = GraficoJsonUtil.serializarPagamentosPix(pagamentos);
        this.profissionaisPainel = profissionaisPainel != null ? profissionaisPainel : Collections.emptyList();
        this.profissionaisPainelJson = GraficoJsonUtil.serializarProfissionaisPainel(this.profissionaisPainel);
        this.pendentes = pendentes != null ? pendentes : Collections.emptyList();
        this.quantidadePendentes = this.pendentes.size();
        this.totalAReceber = totalAReceber != null ? totalAReceber : BigDecimal.ZERO;
        this.totalAReceberFormatado = MoedaBrasilUtil.formatar(this.totalAReceber);
        this.pendentesGraficoJson = GraficoJsonUtil.serializarPendentes(this.pendentes);
    }

    private static String capitalize(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }
}
