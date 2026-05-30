package com.clinica.sistema.dto;

import lombok.Getter;

@Getter
public class ProfissionalBloqueioPagamentoView {

    private final Long profissionalId;
    private final String nome;
    private final String login;
    private final int pendencias;

    public ProfissionalBloqueioPagamentoView(Long profissionalId, String nome, String login, int pendencias) {
        this.profissionalId = profissionalId;
        this.nome = nome;
        this.login = login;
        this.pendencias = pendencias;
    }

    public String getMensagemBloqueio() {
        return "Sistema do usuário " + nome + " foi bloqueado por não pagar.";
    }
}
