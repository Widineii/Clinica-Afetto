package com.clinica.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "salas")
@Data
public class Sala {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;

    /** Taxa de sala cobrada pela clinica nesta sala (ex.: Sala 4). */
    @Column(name = "taxa_clinica", precision = 10, scale = 2)
    private BigDecimal taxaClinica;
}