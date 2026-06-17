package com.clinica.sistema.service;

import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.WhatsAppMensagemPagamento;
import com.clinica.sistema.repository.WhatsAppMensagemPagamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

@Service
public class WhatsAppMensagemPagamentoService {

    private final WhatsAppMensagemPagamentoRepository repository;
    private final PagamentoConsultaService pagamentoConsultaService;

    public WhatsAppMensagemPagamentoService(
            WhatsAppMensagemPagamentoRepository repository,
            PagamentoConsultaService pagamentoConsultaService
    ) {
        this.repository = repository;
        this.pagamentoConsultaService = pagamentoConsultaService;
    }

    @Transactional(readOnly = true)
    public Map<PeriodicidadePagamento, String> mapaMensagensSalvas() {
        String geral = resolverTextoGeral();
        Map<PeriodicidadePagamento, String> mapa = new EnumMap<>(PeriodicidadePagamento.class);
        for (PeriodicidadePagamento periodicidade : PeriodicidadePagamento.values()) {
            mapa.put(periodicidade, geral);
        }
        return mapa;
    }

    @Transactional(readOnly = true)
    public String resolverTextoGeral() {
        try {
            return repository.findById(PeriodicidadePagamento.DIARIO)
                    .map(WhatsAppMensagemPagamento::getTexto)
                    .filter(texto -> !texto.isBlank())
                    .orElseGet(pagamentoConsultaService::frasePadraoWhatsappGeral);
        } catch (RuntimeException ex) {
            return pagamentoConsultaService.frasePadraoWhatsappGeral();
        }
    }

    @Transactional(readOnly = true)
    public String resolverTexto(PeriodicidadePagamento periodicidade) {
        return resolverTextoGeral();
    }

    @Transactional
    public void salvarTextoGeral(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new RuntimeException("Informe o texto da mensagem.");
        }
        String normalizado = texto.trim();
        for (PeriodicidadePagamento periodicidade : PeriodicidadePagamento.values()) {
            salvarTexto(periodicidade, normalizado);
        }
    }

    @Transactional
    public void salvarTexto(PeriodicidadePagamento periodicidade, String texto) {
        if (periodicidade == null) {
            throw new RuntimeException("Informe a forma de pagamento.");
        }
        if (texto == null || texto.isBlank()) {
            throw new RuntimeException("Informe o texto da mensagem.");
        }
        String normalizado = texto.trim();
        WhatsAppMensagemPagamento registro = repository.findById(periodicidade)
                .orElseGet(() -> {
                    WhatsAppMensagemPagamento novo = new WhatsAppMensagemPagamento();
                    novo.setPeriodicidade(periodicidade);
                    return novo;
                });
        registro.setTexto(normalizado);
        repository.save(registro);
    }
}
