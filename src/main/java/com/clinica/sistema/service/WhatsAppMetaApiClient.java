package com.clinica.sistema.service;

import com.clinica.sistema.config.WhatsAppMetaProperties;
import com.clinica.sistema.exception.WhatsAppMetaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WhatsAppMetaApiClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMetaApiClient.class);

    private final WhatsAppMetaProperties properties;
    private final RestTemplate restTemplate;

    public WhatsAppMetaApiClient(WhatsAppMetaProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    public Map<String, Object> enviarTemplate(
            String destinatarioE164,
            String nomeTemplate,
            String codigoIdioma,
            List<String> parametrosCorpo
    ) {
        if (!properties.estaProntoParaEnvio()) {
            throw new WhatsAppMetaException("WhatsApp Meta nao configurado (enabled, token ou phone-number-id).");
        }
        Map<String, Object> body = montarCorpoTemplate(destinatarioE164, nomeTemplate, codigoIdioma, parametrosCorpo);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getAccessToken().trim());
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    properties.resolverUrlEnvioMensagens(),
                    request,
                    Map.class
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> corpo = response.getBody();
            if (corpo == null) {
                throw new WhatsAppMetaException("Resposta vazia da API Meta WhatsApp.");
            }
            log.info("WhatsApp Meta: template {} enviado para {}", nomeTemplate, mascararNumero(destinatarioE164));
            return corpo;
        } catch (HttpStatusCodeException ex) {
            String detalhe = ex.getResponseBodyAsString();
            log.warn("WhatsApp Meta HTTP {}: {}", ex.getStatusCode().value(), detalhe);
            throw new WhatsAppMetaException("Falha ao enviar mensagem WhatsApp: " + resumirErro(detalhe), ex);
        } catch (RestClientException ex) {
            throw new WhatsAppMetaException("Erro de rede ao chamar API Meta WhatsApp.", ex);
        }
    }

    public Map<String, Object> enviarTexto(String destinatarioE164, String texto) {
        if (!properties.estaProntoParaEnvio()) {
            throw new WhatsAppMetaException("WhatsApp Meta nao configurado (enabled, token ou phone-number-id).");
        }
        if (texto == null || texto.isBlank()) {
            throw new WhatsAppMetaException("Texto da mensagem vazio.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", destinatarioE164);
        body.put("type", "text");
        Map<String, Object> conteudo = new LinkedHashMap<>();
        conteudo.put("preview_url", false);
        conteudo.put("body", texto);
        body.put("text", conteudo);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getAccessToken().trim());
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    properties.resolverUrlEnvioMensagens(),
                    request,
                    Map.class
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> corpo = response.getBody();
            if (corpo == null) {
                throw new WhatsAppMetaException("Resposta vazia da API Meta WhatsApp.");
            }
            log.info("WhatsApp Meta: texto enviado para {}", mascararNumero(destinatarioE164));
            return corpo;
        } catch (HttpStatusCodeException ex) {
            String detalhe = ex.getResponseBodyAsString();
            log.warn("WhatsApp Meta HTTP {}: {}", ex.getStatusCode().value(), detalhe);
            throw new WhatsAppMetaException("Falha ao enviar mensagem WhatsApp: " + resumirErro(detalhe), ex);
        } catch (RestClientException ex) {
            throw new WhatsAppMetaException("Erro de rede ao chamar API Meta WhatsApp.", ex);
        }
    }

    private Map<String, Object> montarCorpoTemplate(
            String destinatarioE164,
            String nomeTemplate,
            String codigoIdioma,
            List<String> parametrosCorpo
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", destinatarioE164);
        body.put("type", "template");

        Map<String, Object> language = new LinkedHashMap<>();
        language.put("code", codigoIdioma);

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", nomeTemplate);
        template.put("language", language);

        if (parametrosCorpo != null && !parametrosCorpo.isEmpty()) {
            List<Map<String, Object>> parameters = new ArrayList<>();
            for (String valor : parametrosCorpo) {
                Map<String, Object> param = new LinkedHashMap<>();
                param.put("type", "text");
                param.put("text", valor != null ? valor : "");
                parameters.add(param);
            }
            Map<String, Object> component = new LinkedHashMap<>();
            component.put("type", "body");
            component.put("parameters", parameters);
            template.put("components", List.of(component));
        }

        body.put("template", template);
        return body;
    }

    private String mascararNumero(String e164) {
        if (e164 == null || e164.length() < 6) {
            return "****";
        }
        return e164.substring(0, 4) + "****" + e164.substring(e164.length() - 2);
    }

    private String resumirErro(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return "sem detalhe";
        }
        return corpo.length() > 280 ? corpo.substring(0, 280) + "..." : corpo;
    }
}
