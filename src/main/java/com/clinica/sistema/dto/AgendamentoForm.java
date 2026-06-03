package com.clinica.sistema.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class AgendamentoForm {
    private Long profissionalId;
    private Long salaId;
    private String nomeCliente;
    /** Opcional: lembrete WhatsApp (Meta) na vespera da consulta. */
    private String telefoneCliente;
    private LocalDate dataAtendimento;
    private LocalTime horarioAtendimento;
    /** Locação de sala: TURNO_MANHA (8h–13h) ou TURNO_TARDE (13h–18h). */
    private String turnoLocacao;
    private boolean fixo;
    private String recorrencia = "AVULSO";
    private BigDecimal valorProfissionalRecebe;
    private BigDecimal valorClinicaCobra;
    private boolean indicacaoDona;
    /** Próxima consulta via "Marcar outra consulta" — cobrança segue periodicidade do profissional. */
    private boolean continuacaoMensal;
    /** Card mensal de origem ao remarcar (para acumular datas no mesmo card). */
    private Long agendamentoOrigemId;
}
