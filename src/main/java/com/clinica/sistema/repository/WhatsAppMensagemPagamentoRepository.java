package com.clinica.sistema.repository;

import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.WhatsAppMensagemPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppMensagemPagamentoRepository extends JpaRepository<WhatsAppMensagemPagamento, PeriodicidadePagamento> {
}
