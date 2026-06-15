package com.clinica.sistema.dto;

import java.util.List;

/**
 * Profissionais com pendência de pagamento, separados por forma de cobrança.
 */
public record PendenciasPagamentoWhatsappAgrupadasView(
        List<ProfissionalPendenciaPagamentoWhatsappView> diario,
        List<ProfissionalPendenciaPagamentoWhatsappView> semanal,
        List<ProfissionalPendenciaPagamentoWhatsappView> mensal
) {
    public int total() {
        return diario.size() + semanal.size() + mensal.size();
    }

    public boolean vazio() {
        return total() == 0;
    }
}
