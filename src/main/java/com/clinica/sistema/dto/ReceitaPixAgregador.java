package com.clinica.sistema.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class ReceitaPixAgregador {

    private static final Map<String, String> CORES_SALA = Map.of(
            "sala 1", "#3B82F6",
            "sala 2", "#10B981",
            "sala 3", "#A855F7",
            "sala 4", "#F97316"
    );

    private static final List<String> PALETA_PROFISSIONAL = List.of(
            "#1B4D5C", "#2563EB", "#059669", "#7C3AED", "#EA580C",
            "#0891B2", "#BE185D", "#4338CA", "#15803D", "#B45309"
    );

    private static final Map<String, String> CORES_TIPO = Map.of(
            "avulso", "#1B4D5C",
            "semanal", "#2563EB",
            "quinzenal", "#059669"
    );

    private ReceitaPixAgregador() {
    }

    public static List<ReceitaPixFatiaView> porSala(List<ReceitaPixLinhaView> linhas, BigDecimal total) {
        return agregar(linhas, total, ReceitaPixLinhaView::getSalaChave, ReceitaPixLinhaView::getSalaNome, CORES_SALA);
    }

    public static List<ReceitaPixFatiaView> porProfissional(List<ReceitaPixLinhaView> linhas, BigDecimal total) {
        return agregarComPaleta(linhas, total, ReceitaPixLinhaView::getProfissionalChave, ReceitaPixLinhaView::getProfissionalNome);
    }

    public static List<ReceitaPixFatiaView> porTipo(List<ReceitaPixLinhaView> linhas, BigDecimal total) {
        return agregar(linhas, total, ReceitaPixLinhaView::getTipoRecorrencia, ReceitaPixLinhaView::getTipoRecorrenciaRotulo, CORES_TIPO);
    }

    public static List<String> chavesDistintas(List<ReceitaPixLinhaView> linhas, Function<ReceitaPixLinhaView, String> chaveFn) {
        return linhas.stream()
                .map(chaveFn)
                .filter(chave -> chave != null && !chave.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static List<ReceitaPixFatiaView> agregar(
            List<ReceitaPixLinhaView> linhas,
            BigDecimal total,
            Function<ReceitaPixLinhaView, String> chaveFn,
            Function<ReceitaPixLinhaView, String> rotuloFn,
            Map<String, String> cores
    ) {
        Map<String, GrupoReceita> grupos = new LinkedHashMap<>();
        for (ReceitaPixLinhaView linha : linhas) {
            String chave = normalizarChave(chaveFn.apply(linha));
            if (chave.isBlank()) {
                continue;
            }
            GrupoReceita grupo = grupos.computeIfAbsent(chave, ignored -> new GrupoReceita(rotuloFn.apply(linha)));
            grupo.valor = grupo.valor.add(linha.getValorTaxa());
            grupo.quantidade++;
        }
        return grupos.entrySet().stream()
                .sorted(Map.Entry.<String, GrupoReceita>comparingByValue(
                        Comparator.comparing(grupo -> grupo.valor)).reversed())
                .map(entry -> new ReceitaPixFatiaView(
                        entry.getKey(),
                        entry.getValue().rotulo,
                        entry.getValue().valor,
                        entry.getValue().quantidade,
                        total,
                        cores.getOrDefault(entry.getKey(), "#64748B")
                ))
                .toList();
    }

    private static List<ReceitaPixFatiaView> agregarComPaleta(
            List<ReceitaPixLinhaView> linhas,
            BigDecimal total,
            Function<ReceitaPixLinhaView, String> chaveFn,
            Function<ReceitaPixLinhaView, String> rotuloFn
    ) {
        Map<String, GrupoReceita> grupos = new LinkedHashMap<>();
        for (ReceitaPixLinhaView linha : linhas) {
            String chave = normalizarChave(chaveFn.apply(linha));
            if (chave.isBlank()) {
                continue;
            }
            GrupoReceita grupo = grupos.computeIfAbsent(chave, ignored -> new GrupoReceita(rotuloFn.apply(linha)));
            grupo.valor = grupo.valor.add(linha.getValorTaxa());
            grupo.quantidade++;
        }

        List<Map.Entry<String, GrupoReceita>> ordenados = grupos.entrySet().stream()
                .sorted(Map.Entry.<String, GrupoReceita>comparingByValue(
                        Comparator.comparing(grupo -> grupo.valor)).reversed())
                .toList();

        List<ReceitaPixFatiaView> fatias = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            Map.Entry<String, GrupoReceita> entry = ordenados.get(i);
            String cor = PALETA_PROFISSIONAL.get(i % PALETA_PROFISSIONAL.size());
            fatias.add(new ReceitaPixFatiaView(
                    entry.getKey(),
                    entry.getValue().rotulo,
                    entry.getValue().valor,
                    entry.getValue().quantidade,
                    total,
                    cor
            ));
        }
        return fatias;
    }

    private static String normalizarChave(String chave) {
        if (chave == null) {
            return "";
        }
        return chave.trim().toLowerCase(Locale.ROOT);
    }

    private static final class GrupoReceita {
        private final String rotulo;
        private BigDecimal valor = BigDecimal.ZERO;
        private int quantidade;

        private GrupoReceita(String rotulo) {
            this.rotulo = rotulo != null && !rotulo.isBlank() ? rotulo : "—";
        }
    }
}
