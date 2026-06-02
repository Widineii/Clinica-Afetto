package com.clinica.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Data
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
    private String login;
    private String senha;
    private String cargo; // ROLE_ADMIN ou ROLE_PROFISSIONAL

    @Column(name = "dona_clinica")
    private Boolean donaClinica = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicidade_pagamento", length = 20)
    private PeriodicidadePagamento periodicidadePagamento = PeriodicidadePagamento.DIARIO;

    @Column(name = "periodicidade_alterada_em")
    private LocalDateTime periodicidadeAlteradaEm;

    @Column(name = "deve_trocar_senha")
    private Boolean deveTrocarSenha = false;

    /** Registrado apenas no perfil local (ultimo login no sistema). */
    @Column(name = "ultimo_acesso_em")
    private LocalDateTime ultimoAcessoEm;
}