package com.clinica.sistema.dto;

import lombok.Data;

@Data
public class TrocarSenhaForm {
    private String senhaAtual;
    private String novaSenha;
    private String confirmarSenha;
}
