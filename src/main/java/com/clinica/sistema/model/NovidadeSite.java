package com.clinica.sistema.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "novidades_site")
@Data
public class NovidadeSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String codigo;

    @Column(nullable = false, length = 20)
    private String versao;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, length = 2000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "publico_alvo", nullable = false, length = 30)
    private NovidadePublicoAlvo publicoAlvo;

    @Column(name = "publicada_em", nullable = false)
    private LocalDateTime publicadaEm;

    @Column(name = "ordem_exibicao", nullable = false)
    private int ordemExibicao;
}
