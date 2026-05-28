package com.clinica.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "salas")
@Data
public class Sala {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
}