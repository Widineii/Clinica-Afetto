package com.clinica.sistema.controller;

import com.clinica.sistema.service.PagamentoConsultaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class InfinitePayWebhookController {

    private final PagamentoConsultaService pagamentoConsultaService;

    public InfinitePayWebhookController(PagamentoConsultaService pagamentoConsultaService) {
        this.pagamentoConsultaService = pagamentoConsultaService;
    }

    @PostMapping("/infinitepay")
    public ResponseEntity<Map<String, String>> receberWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String webhookSecret
    ) {
        pagamentoConsultaService.validarAutenticacaoWebhook(webhookSecret);
        Object orderNsu = payload.get("order_nsu");
        if (orderNsu == null || orderNsu.toString().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "order_nsu obrigatorio"));
        }
        pagamentoConsultaService.confirmarPagamentoPorOrderNsu(orderNsu.toString());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
