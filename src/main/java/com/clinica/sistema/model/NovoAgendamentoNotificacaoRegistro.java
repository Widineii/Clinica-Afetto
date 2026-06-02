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
@Table(name = "notificacoes_novo_agendamento")
@Data
public class NovoAgendamentoNotificacaoRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_agendamento")
    private Long agendamentoId;

    @Column(name = "nome_cliente", nullable = false, length = 200)
    private String nomeCliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_profissional", nullable = false)
    private Usuario profissional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sala")
    private Sala sala;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_registrado_por", nullable = false)
    private Usuario registradoPor;

    @Column(name = "data_hora_inicio", nullable = false)
    private LocalDateTime dataHoraInicio;

    @Column(name = "tipo_recorrencia", nullable = false, length = 20)
    private String tipoRecorrencia;

    @Column(name = "quantidade_horarios", nullable = false)
    private int quantidadeHorarios;

    @Column(name = "aguardando_aprovacao_indicacao", nullable = false)
    private boolean aguardandoAprovacaoIndicacao;

    @Column(name = "registrado_em", nullable = false)
    private LocalDateTime registradoEm;
}
