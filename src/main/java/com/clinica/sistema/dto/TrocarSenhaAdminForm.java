package com.clinica.sistema.dto;

import lombok.Data;

@Data
public class TrocarSenhaAdminForm {
    private Long usuarioId;
    private String novaSenha;
    private String confirmarSenha;
}
