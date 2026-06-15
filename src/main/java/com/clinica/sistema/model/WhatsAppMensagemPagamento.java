package com.clinica.sistema.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "whatsapp_mensagem_pagamento")
@Data
public class WhatsAppMensagemPagamento {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "periodicidade", length = 20)
    private PeriodicidadePagamento periodicidade;

    @Column(name = "texto", nullable = false, length = 2000)
    private String texto;
}
