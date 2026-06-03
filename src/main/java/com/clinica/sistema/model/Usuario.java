package com.clinica.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
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

    /** Ultimo login bem-sucedido no sistema. */
    @Column(name = "ultimo_acesso_em")
    private LocalDateTime ultimoAcessoEm;

    /** Valores padrao da consulta (quanto o cliente paga ao profissional), por tipo de agendamento. */
    @Column(name = "valor_consulta_avulso", precision = 10, scale = 2)
    private BigDecimal valorConsultaAvulso;

    @Column(name = "valor_consulta_semanal", precision = 10, scale = 2)
    private BigDecimal valorConsultaSemanal;

    @Column(name = "valor_consulta_quinzenal", precision = 10, scale = 2)
    private BigDecimal valorConsultaQuinzenal;

    @Column(name = "valor_consulta_mensal", precision = 10, scale = 2)
    private BigDecimal valorConsultaMensal;
}