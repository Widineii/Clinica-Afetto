package com.clinica.sistema.controller;

import com.clinica.sistema.service.PagamentoConsultaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(InfinitePayWebhookController.class);

    private final PagamentoConsultaService pagamentoConsultaService;

    public InfinitePayWebhookController(PagamentoConsultaService pagamentoConsultaService) {
        this.pagamentoConsultaService = pagamentoConsultaService;
    }

    @PostMapping("/infinitepay")
    public ResponseEntity<Map<String, String>> receberWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String webhookSecret
    ) {
        log.info("Webhook InfinitePay recebido: order_nsu={}", payload.get("order_nsu"));
        pagamentoConsultaService.processarNotificacaoInfinitePay(payload, webhookSecret);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
