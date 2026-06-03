package com.clinica.sistema.controller;

import com.clinica.sistema.config.WhatsAppMetaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/whatsapp/meta")
public class WhatsAppMetaWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMetaWebhookController.class);

    private final WhatsAppMetaProperties properties;

    public WhatsAppMetaWebhookController(WhatsAppMetaProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public ResponseEntity<String> verificarWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        if (!"subscribe".equals(mode) || challenge == null || challenge.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!properties.webhookConfigurado()
                || !properties.getWebhookVerifyToken().equals(token)) {
            log.warn("WhatsApp Meta webhook: token de verificacao invalido.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("WhatsApp Meta webhook verificado com sucesso.");
        return ResponseEntity.ok(challenge);
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> receberEventos(@RequestBody Map<String, Object> payload) {
        log.debug("WhatsApp Meta webhook recebido: object={}", payload.get("object"));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
