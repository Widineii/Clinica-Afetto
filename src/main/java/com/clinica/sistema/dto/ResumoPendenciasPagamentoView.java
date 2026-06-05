package com.clinica.sistema.dto;

/**
 * Resumo amigável de taxas de sala em aberto para lembrete na agenda e em Meus pagamentos.
 */
public record ResumoPendenciasPagamentoView(
        int quantidade,
        String valorTotalFormatado,
        String titulo,
        String mensagemResumo,
        String mensagemConvite,
        String rotuloPeriodo,
        String urlMeusPagamentos
) {
    public boolean temPendencias() {
        return quantidade > 0;
    }

    public boolean exibeRotuloPeriodo() {
        return rotuloPeriodo != null && !rotuloPeriodo.isBlank();
    }

    public static ResumoPendenciasPagamentoView vazio() {
        return new ResumoPendenciasPagamentoView(
                0,
                "R$ 0,00",
                "",
                "",
                "",
                "",
                "/agendamentos/meus-pagamentos"
        );
    }
}
