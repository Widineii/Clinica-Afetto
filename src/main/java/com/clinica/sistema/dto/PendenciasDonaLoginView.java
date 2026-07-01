package com.clinica.sistema.dto;

import java.util.List;

public record PendenciasDonaLoginView(
        String primeiroNome,
        int totalIndicacoes,
        int totalContas,
        List<PendenciasDonaIndicacaoItemView> indicacoes,
        List<PendenciasDonaContaItemView> contas
) {
    public boolean temPendencias() {
        return totalIndicacoes > 0 || totalContas > 0;
    }

    public int totalPendencias() {
        return totalIndicacoes + totalContas;
    }

    public static PendenciasDonaLoginView vazio(String primeiroNome) {
        return new PendenciasDonaLoginView(primeiroNome, 0, 0, List.of(), List.of());
    }
}
