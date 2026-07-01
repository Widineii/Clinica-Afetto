package com.clinica.sistema.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "paciente_caderno_observacao")
@Data
public class PacienteCadernoObservacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_profissional", nullable = false)
    private Usuario profissional;

    @Column(name = "chave_caderno", nullable = false, length = 40)
    private String chaveCaderno;

    @Column(nullable = false, length = 2000)
    private String texto;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "evolucao_clinica", length = 20)
    private String evolucaoClinica;

    @Column(name = "lembrete_em")
    private LocalDateTime lembreteEm;
}
