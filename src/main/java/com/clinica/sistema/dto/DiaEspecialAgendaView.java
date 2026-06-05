package com.clinica.sistema.dto;

public record DiaEspecialAgendaView(String nome, boolean pontoFacultativo) {

    public String rotuloTipo() {
        return pontoFacultativo ? "ponto facultativo" : "feriado";
    }

    public String rotuloCabecalho() {
        return (pontoFacultativo ? "Ponto facultativo" : "Feriado") + " · " + nome;
    }

    public String rotuloCelula() {
        return "(" + rotuloTipo() + " " + nome + ")";
    }
}
