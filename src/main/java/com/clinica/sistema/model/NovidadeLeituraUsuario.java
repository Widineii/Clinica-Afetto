package com.clinica.sistema.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "novidades_leitura_usuario",
        uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "novidade_id"})
)
@Data
public class NovidadeLeituraUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "novidade_id", nullable = false)
    private Long novidadeId;

    @Column(name = "primeira_visualizacao_em", nullable = false)
    private LocalDateTime primeiraVisualizacaoEm;

    @Column(name = "ocultar_definitivo", nullable = false)
    private boolean ocultarDefinitivo;
}
