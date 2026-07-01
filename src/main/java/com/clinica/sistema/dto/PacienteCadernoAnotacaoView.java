package com.clinica.sistema.dto;

import com.clinica.sistema.model.EvolucaoClinica;
import com.clinica.sistema.model.PacienteCadernoObservacao;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
public class PacienteCadernoAnotacaoView {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter LEMBRETE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter LEMBRETE_INPUT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Long id;
    private final String texto;
    private final String dataRotulo;
    private final String evolucaoRotulo;
    private final String lembreteRotulo;
    private final String lembreteDataInput;

    public PacienteCadernoAnotacaoView(
            Long id,
            String texto,
            String dataRotulo,
            String evolucaoRotulo,
            String lembreteRotulo,
            String lembreteDataInput
    ) {
        this.id = id;
        this.texto = texto != null ? texto : "";
        this.dataRotulo = dataRotulo != null ? dataRotulo : "";
        this.evolucaoRotulo = evolucaoRotulo != null ? evolucaoRotulo : "";
        this.lembreteRotulo = lembreteRotulo != null ? lembreteRotulo : "";
        this.lembreteDataInput = lembreteDataInput != null ? lembreteDataInput : "";
    }

    public static PacienteCadernoAnotacaoView de(PacienteCadernoObservacao observacao) {
        if (observacao == null) {
            return new PacienteCadernoAnotacaoView(null, "", "", "", "", "");
        }
        var data = observacao.getAtualizadoEm() != null
                ? observacao.getAtualizadoEm()
                : observacao.getCriadoEm();
        EvolucaoClinica evolucao = EvolucaoClinica.parse(observacao.getEvolucaoClinica());
        String evolucaoRotulo = evolucao != null ? evolucao.getRotulo() : "";
        String lembreteRotulo = "";
        String lembreteDataInput = "";
        if (observacao.getLembreteEm() != null) {
            lembreteRotulo = LEMBRETE.format(observacao.getLembreteEm());
            lembreteDataInput = LEMBRETE_INPUT.format(observacao.getLembreteEm());
        }
        return new PacienteCadernoAnotacaoView(
                observacao.getId(),
                observacao.getTexto(),
                data != null ? DATA_HORA.format(data) : "",
                evolucaoRotulo,
                lembreteRotulo,
                lembreteDataInput
        );
    }
}
