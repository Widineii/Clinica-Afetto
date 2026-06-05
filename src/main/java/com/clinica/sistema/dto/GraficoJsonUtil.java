package com.clinica.sistema.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class GraficoJsonUtil {

    private GraficoJsonUtil() {
    }

    public static String serializarPagamentosPix(List<ReceitaPixLinhaView> pagamentos) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < pagamentos.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            ReceitaPixLinhaView pagamento = pagamentos.get(indice);
            json.append('{')
                    .append("\"profissionalChave\":").append(texto(pagamento.getProfissionalChave())).append(',')
                    .append("\"profissionalNome\":").append(texto(pagamento.getProfissionalNome())).append(',')
                    .append("\"salaChave\":").append(texto(pagamento.getSalaChave())).append(',')
                    .append("\"salaNome\":").append(texto(pagamento.getSalaNome())).append(',')
                    .append("\"tipoRecorrencia\":").append(texto(pagamento.getTipoRecorrencia())).append(',')
                    .append("\"tipoRecorrenciaRotulo\":").append(texto(pagamento.getTipoRecorrenciaRotulo())).append(',')
                    .append("\"valorTaxa\":").append(valor(pagamento.getValorTaxa())).append(',')
                    .append("\"valorTaxaFormatado\":").append(texto(pagamento.getValorTaxaFormatado())).append(',')
                    .append("\"dataPagamentoRotulo\":").append(texto(pagamento.getDataPagamentoRotulo())).append(',')
                    .append("\"nomeCliente\":").append(texto(pagamento.getNomeCliente())).append(',')
                    .append("\"consultaRotulo\":").append(texto(pagamento.getConsultaRotulo()))
                    .append('}');
        }
        json.append(']');
        return json.toString();
    }

    public static String serializarUsoSalasRelatorio(List<RelatorioLinhaView> linhas) {
        long sala1 = 0;
        long sala2 = 0;
        long sala3 = 0;
        long sala4 = 0;
        for (RelatorioLinhaView linha : linhas) {
            sala1 += linha.getSala1();
            sala2 += linha.getSala2();
            sala3 += linha.getSala3();
            sala4 += linha.getSala4();
        }
        return "["
                + fatia("Sala 1", sala1)
                + ',' + fatia("Sala 2", sala2)
                + ',' + fatia("Sala 3", sala3)
                + ',' + fatia("Sala 4", sala4)
                + "]";
    }

    public static String serializarProfissionaisRelatorio(List<RelatorioLinhaView> linhas) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < linhas.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            RelatorioLinhaView linha = linhas.get(indice);
            json.append('{')
                    .append("\"rotulo\":").append(texto(linha.getProfissionalNome())).append(',')
                    .append("\"valor\":").append(linha.getTotalHorarios())
                    .append('}');
        }
        json.append(']');
        return json.toString();
    }

    public static String serializarContagensPorRotulo(List<ContagemGraficoView> itens) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < itens.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            ContagemGraficoView item = itens.get(indice);
            json.append('{')
                    .append("\"rotulo\":").append(texto(item.getRotulo())).append(',')
                    .append("\"valor\":").append(item.getQuantidade())
                    .append('}');
        }
        json.append(']');
        return json.toString();
    }

    public static String serializarPendentes(List<ReceitaPendenteLinhaView> pendentes) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < pendentes.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            ReceitaPendenteLinhaView linha = pendentes.get(indice);
            json.append('{')
                    .append("\"profissionalChave\":").append(texto(linha.getProfissionalChave())).append(',')
                    .append("\"profissionalNome\":").append(texto(linha.getProfissionalNome())).append(',')
                    .append("\"salaChave\":").append(texto(linha.getSalaChave())).append(',')
                    .append("\"salaNome\":").append(texto(linha.getSalaNome())).append(',')
                    .append("\"tipoRecorrencia\":").append(texto(linha.getTipoRecorrencia())).append(',')
                    .append("\"tipoRecorrenciaRotulo\":").append(texto(linha.getTipoRecorrenciaRotulo())).append(',')
                    .append("\"valorTaxa\":").append(valor(linha.getValorTaxa())).append(',')
                    .append("\"valorTaxaFormatado\":").append(texto(linha.getValorTaxaFormatado())).append(',')
                    .append("\"statusPagamentoRotulo\":").append(texto(linha.getStatusPagamentoRotulo())).append(',')
                    .append("\"nomeCliente\":").append(texto(linha.getNomeCliente())).append(',')
                    .append("\"consultaRotulo\":").append(texto(linha.getConsultaRotulo())).append(',')
                    .append("\"dataConsulta\":").append(texto(
                            linha.getDataConsulta() != null ? linha.getDataConsulta().toString() : ""
                    )).append(',')
                    .append("\"dataConsultaRotulo\":").append(texto(linha.getDataConsultaRotulo()))
                    .append('}');
        }
        json.append(']');
        return json.toString();
    }

    public static String serializarProfissionaisPainel(List<ProfissionalReceitaPainelView> profissionais) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < profissionais.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            ProfissionalReceitaPainelView profissional = profissionais.get(indice);
            json.append('{')
                    .append("\"chave\":").append(texto(profissional.getChave())).append(',')
                    .append("\"nome\":").append(texto(profissional.getNome())).append(',')
                    .append("\"valorMesAtual\":").append(valor(profissional.getValorMesAtual())).append(',')
                    .append("\"valorMesAtualFormatado\":").append(texto(profissional.getValorMesAtualFormatado())).append(',')
                    .append("\"atendimentosMesAtual\":").append(profissional.getAtendimentosMesAtual()).append(',')
                    .append("\"melhorMesLabel\":").append(texto(profissional.getMelhorMesLabel())).append(',')
                    .append("\"valorMelhorMes\":").append(valor(profissional.getValorMelhorMes())).append(',')
                    .append("\"valorMelhorMesFormatado\":").append(texto(profissional.getValorMelhorMesFormatado())).append(',')
                    .append("\"atendimentosMelhorMes\":").append(profissional.getAtendimentosMelhorMes()).append(',')
                    .append("\"valorAReceberMes\":").append(valor(profissional.getValorAReceberMes())).append(',')
                    .append("\"valorAReceberMesFormatado\":").append(texto(profissional.getValorAReceberMesFormatado())).append(',')
                    .append("\"quantidadePendentesMes\":").append(profissional.getQuantidadePendentesMes())
                    .append('}');
        }
        json.append(']');
        return json.toString();
    }

    public static String serializarValoresConsultaPadrao(Map<String, Map<String, BigDecimal>> mapa) {
        if (mapa == null || mapa.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        int indiceProfissional = 0;
        for (Map.Entry<String, Map<String, BigDecimal>> profissional : mapa.entrySet()) {
            if (indiceProfissional++ > 0) {
                json.append(',');
            }
            json.append(texto(profissional.getKey())).append(':').append('{');
            int indiceValor = 0;
            for (Map.Entry<String, BigDecimal> valor : profissional.getValue().entrySet()) {
                if (indiceValor++ > 0) {
                    json.append(',');
                }
                json.append(texto(valor.getKey())).append(':').append(valor(valor.getValue()));
            }
            json.append('}');
        }
        json.append('}');
        return json.toString();
    }

    private static String fatia(String rotulo, long valor) {
        return "{\"rotulo\":" + texto(rotulo) + ",\"valor\":" + valor + "}";
    }

    private static String valor(java.math.BigDecimal valor) {
        return valor != null ? valor.toPlainString() : "0";
    }

    private static String texto(String valor) {
        if (valor == null) {
            return "\"\"";
        }
        return "\"" + valor
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e") + "\"";
    }
}
