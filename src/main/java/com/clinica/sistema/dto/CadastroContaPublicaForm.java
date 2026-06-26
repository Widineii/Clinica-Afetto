package com.clinica.sistema.dto;

import lombok.Data;

@Data
public class CadastroContaPublicaForm {
    private String nome;
    private String login;
    private String email;
    private String telefoneWhatsapp;
    private String senha;
    private String confirmarSenha;
}
