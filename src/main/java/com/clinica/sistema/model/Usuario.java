package com.clinica.sistema.model;

import com.clinica.sistema.util.WhatsAppNumeroUtil;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** Data/hora em que o usuario aceitou o consentimento LGPD do sistema. */
    @Column(name = "lgpd_consentimento_em")
    private LocalDateTime lgpdConsentimentoEm;

    /** Dia de referencia do controle de exibicoes do modal de boas-vindas no login. */
    @Column(name = "boas_vindas_controle_data")
    private LocalDate boasVindasControleData;

    /** Quantas vezes o modal de atendimentos de hoje foi fechado (antes das 21h). */
    @Column(name = "boas_vindas_exibicoes_hoje")
    private Integer boasVindasExibicoesHoje = 0;

    /** Usuario marcou para nao ver atendimentos de hoje novamente (antes das 21h). */
    @Column(name = "boas_vindas_oculto_hoje")
    private Boolean boasVindasOcultoHoje = false;

    /** Quantas vezes o modal de atendimentos de amanha foi fechado (a partir das 21h). */
    @Column(name = "boas_vindas_exibicoes_noite")
    private Integer boasVindasExibicoesNoite = 0;

    /** Usuario marcou para nao ver atendimentos de amanha novamente na noite atual. */
    @Column(name = "boas_vindas_oculto_noite")
    private Boolean boasVindasOcultoNoite = false;

    /** Modal de boas-vindas com atendimentos ja exibido no primeiro login da conta. */
    @Column(name = "boas_vindas_primeiro_login_concluido")
    private Boolean boasVindasPrimeiroLoginConcluido = false;

    /** Profissional existente: tela de boas-vindas so para apresentar a novidade (sem exigir pagamento). */
    @Column(name = "boas_vindas_apenas_apresentacao")
    private Boolean boasVindasApenasApresentacao = false;

    /** Apresentacao unica da novidade ja vista (profissionais que ja usavam o sistema). */
    @Column(name = "boas_vindas_apresentacao_exibida")
    private Boolean boasVindasApresentacaoExibida = false;

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

    /** E-mail para recuperacao de senha e avisos futuros. */
    @Column(length = 120)
    private String email;

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