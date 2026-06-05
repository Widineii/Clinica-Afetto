package com.clinica.sistema.model;

import com.clinica.sistema.util.WhatsAppNumeroUtil;
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

    /** Taxa de sala padrao por tipo de agendamento (Central → Valores). Nao e o valor que o cliente paga ao profissional. */
    @Column(name = "valor_consulta_avulso", precision = 10, scale = 2)
    private BigDecimal valorConsultaAvulso;

    @Column(name = "valor_consulta_semanal", precision = 10, scale = 2)
    private BigDecimal valorConsultaSemanal;

    @Column(name = "valor_consulta_quinzenal", precision = 10, scale = 2)
    private BigDecimal valorConsultaQuinzenal;

    @Column(name = "valor_consulta_mensal", precision = 10, scale = 2)
    private BigDecimal valorConsultaMensal;

    /** Percentual da taxa de indicação da clínica (ex.: 30 = 30%). Vazio usa o padrão do sistema. */
    @Column(name = "percentual_taxa_indicacao", precision = 5, scale = 2)
    private BigDecimal percentualTaxaIndicacao;

    /** WhatsApp do profissional (DDI 55 + DDD + numero) para avisos da clinica, ex.: pagamento pendente. */
    @Column(name = "telefone_whatsapp", length = 20)
    private String telefoneWhatsapp;

    /** Caminho publico da foto de perfil, ex.: /uploads/perfis/12.jpg */
    @Column(name = "foto_perfil", length = 120)
    private String fotoPerfil;

    @Transient
    public String getTelefoneWhatsappFormulario() {
        return WhatsAppNumeroUtil.paraCampoFormulario(telefoneWhatsapp);
    }

    @Transient
    public boolean temFotoPerfil() {
        return fotoPerfil != null && !fotoPerfil.isBlank();
    }
}