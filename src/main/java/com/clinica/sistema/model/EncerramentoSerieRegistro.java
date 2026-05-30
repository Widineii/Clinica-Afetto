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
@Table(name = "encerramentos_serie")
@Data
public class EncerramentoSerieRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serie_fixa_id", nullable = false, length = 120)
    private String serieFixaId;

    @Column(name = "nome_cliente", nullable = false, length = 200)
    private String nomeCliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_profissional", nullable = false)
    private Usuario profissional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sala")
    private Sala sala;

    @Column(name = "tipo_recorrencia", nullable = false, length = 20)
    private String tipoRecorrencia;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Column(name = "encerrado_em", nullable = false)
    private LocalDateTime encerradoEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_encerrado_por", nullable = false)
    private Usuario encerradoPor;

    @Column(name = "quantidade_horarios", nullable = false)
    private int quantidadeHorarios;
}
