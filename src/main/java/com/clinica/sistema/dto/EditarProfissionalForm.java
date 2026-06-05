package com.clinica.sistema.dto;

import lombok.Data;

@Data
public class EditarProfissionalForm {
    private Long usuarioId;
    private String nome;
    private String login;
    /** WhatsApp com DDD (somente digitos). Opcional — preencha só para cadastrar ou trocar. */
    private String telefoneWhatsapp;
    /** Quando true, remove o WhatsApp cadastrado mesmo com o campo vazio. */
    private Boolean removerWhatsapp;
}
