package com.clinica.sistema.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria_eventos")
@Data
public class AuditoriaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "autor_id")
    private Long autorId;

    @Column(name = "autor_nome", length = 120)
    private String autorNome;

    @Column(name = "tipo", nullable = false, length = 40)
    private String tipo;

    @Column(name = "descricao", nullable = false, length = 500)
    private String descricao;
}
