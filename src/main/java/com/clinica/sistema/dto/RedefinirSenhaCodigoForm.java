package com.clinica.sistema.dto;

import lombok.Data;

@Data
public class RedefinirSenhaCodigoForm {
    private String login;
    private String codigo;
    private String novaSenha;
    private String confirmarSenha;
}
