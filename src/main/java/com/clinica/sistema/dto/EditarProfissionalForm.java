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
    /** E-mail pessoal para avisos de pagamento. Opcional — preencha para cadastrar ou trocar. */
    private String email;
    /** Quando true, remove o e-mail cadastrado. */
    private Boolean removerEmail;
}
