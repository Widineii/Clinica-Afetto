package com.clinica.sistema.service;

import com.clinica.sistema.config.InfinitePayProperties;
import com.clinica.sistema.dto.LinkPagamentoGerado;
import com.clinica.sistema.model.Agendamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InfinitePayService {

    private static final Logger log = LoggerFactory.getLogger(InfinitePayService.class);

    private static final String API_LINKS = "https://api.checkout.infinitepay.io/links";
    private static final String API_PAYMENT_CHECK_CHECKOUT = "https://api.checkout.infinitepay.io/payment_check";
    private static final String API_PAYMENT_CHECK_LEGACY =
            "https://api.infinitepay.io/invoices/public/checkout/payment_check";

    private final InfinitePayProperties properties;
    private final ValorConsultaService valorConsultaService;
    private final RestTemplate restTemplate;

    public InfinitePayService(InfinitePayProperties properties, ValorConsultaService valorConsultaService) {
        this.properties = properties;
        this.valorConsultaService = valorConsultaService;
        this.restTemplate = new RestTemplate();
    }

    public LinkPagamentoGerado gerarLinkPagamento(Agendamento agendamento) {
        String orderNsu = "ag-" + agendamento.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        BigDecimal valor = valorPagamento(agendamento);
        if (valor == null || valor.signum() <= 0) {
            throw new RuntimeException("Valor da taxa da clinica invalido para pagamento.");
        }

        if (properties.isModoTeste()) {
            String link = properties.getBaseUrl().replaceAll("/$", "")
                    + "/pagamentos/checkout-teste?order=" + orderNsu
                    + "&agendamento=" + agendamento.getId();
            return new LinkPagamentoGerado(orderNsu, link, "teste-" + orderNsu);
        }

        return gerarLinkReal(orderNsu, List.of(itemCheckout(agendamento, valor)));
    }

    public LinkPagamentoGerado gerarLinkPagamentoSemana(List<Agendamento> agendamentos) {
        return gerarLinkPagamentoLote(agendamentos, "sem-", "semana", "/pagamentos/checkout-teste-semana?order=");
    }

    public LinkPagamentoGerado gerarLinkPagamentoDia(List<Agendamento> agendamentos) {
        return gerarLinkPagamentoLote(agendamentos, "dia-", "dia", "/pagamentos/checkout-teste-dia?order=");
    }

    public LinkPagamentoGerado gerarLinkPagamentoMes(List<Agendamento> agendamentos) {
        return gerarLinkPagamentoLote(agendamentos, "mes-", "mes", "/pagamentos/checkout-teste-mes?order=");
    }

    private LinkPagamentoGerado gerarLinkPagamentoLote(
            List<Agendamento> agendamentos,
            String prefixoPedido,
            String rotuloErro,
            String caminhoCheckoutTeste
    ) {
        if (agendamentos == null || agendamentos.isEmpty()) {
            throw new RuntimeException("Nenhuma consulta informada para pagamento do " + rotuloErro + ".");
        }
        Long profissionalId = agendamentos.get(0).getProfissional() != null
                ? agendamentos.get(0).getProfissional().getId()
                : 0L;
        String orderNsu = prefixoPedido + profissionalId + "-" + UUID.randomUUID().toString().substring(0, 8);

        List<Map<String, Object>> itens = new ArrayList<>();
        for (Agendamento agendamento : agendamentos) {
            BigDecimal valor = valorPagamento(agendamento);
            if (valor == null || valor.signum() <= 0) {
                throw new RuntimeException("Valor da taxa da clinica invalido para pagamento do " + rotuloErro + ".");
            }
            itens.add(itemCheckout(agendamento, valor));
        }

        if (properties.isModoTeste()) {
            String link = properties.getBaseUrl().replaceAll("/$", "")
                    + caminhoCheckoutTeste
                    + orderNsu;
            return new LinkPagamentoGerado(orderNsu, link, "teste-" + orderNsu);
        }

        return gerarLinkReal(orderNsu, itens);
    }

    private Map<String, Object> itemCheckout(Agendamento agendamento, BigDecimal valor) {
        int centavos = valor.multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        return Map.of(
                "quantity", 1,
                "price", centavos,
                "description", descricaoItem(agendamento)
        );
    }

    private LinkPagamentoGerado gerarLinkReal(String orderNsu, List<Map<String, Object>> itens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("handle", properties.getHandle());
        body.put("order_nsu", orderNsu);
        body.put("webhook_url", properties.getBaseUrl().replaceAll("/$", "") + "/api/webhooks/infinitepay");
        body.put("items", itens);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resposta = restTemplate.postForObject(API_LINKS, request, Map.class);
            if (resposta == null) {
                throw new RuntimeException("InfinitePay nao retornou resposta.");
            }
            Object url = resposta.get("checkout_url");
            if (url == null || url.toString().isBlank()) {
                url = resposta.get("url");
            }
            if (url == null || url.toString().isBlank()) {
                url = resposta.get("link");
            }
            Object slug = resposta.get("slug");
            if (slug == null || slug.toString().isBlank()) {
                slug = resposta.get("invoice_slug");
            }
            if (url == null || url.toString().isBlank()) {
                throw new RuntimeException("InfinitePay nao retornou link de pagamento.");
            }
            String urlCheckout = url.toString();
            String slugFinal = slug != null && !slug.toString().isBlank()
                    ? slug.toString().trim()
                    : extrairSlugDoCheckoutUrl(urlCheckout);
            return new LinkPagamentoGerado(
                    orderNsu,
                    urlCheckout,
                    slugFinal
            );
        } catch (RestClientException ex) {
            throw new RuntimeException("Falha ao gerar link InfinitePay: " + ex.getMessage());
        }
    }

    public BigDecimal valorPagamento(Agendamento agendamento) {
        return resolverValorTaxaClinica(agendamento);
    }

    /**
     * Consulta na InfinitePay se o pedido foi pago (PIX ou cartao).
     */
    public boolean consultarPagamentoConfirmado(String orderNsu, String slugSalvo, String linkPagamento) {
        return consultarPagamentoConfirmado(orderNsu, slugSalvo, linkPagamento, null);
    }

    public boolean consultarPagamentoConfirmado(
            String orderNsu,
            String slugSalvo,
            String linkPagamento,
            String transactionNsu
    ) {
        if (orderNsu == null || orderNsu.isBlank()) {
            return false;
        }
        String slug = resolverSlugPagamento(slugSalvo, linkPagamento);
        Map<String, Object> body = montarCorpoPaymentCheck(orderNsu, slug, transactionNsu);

        for (String endpoint : List.of(API_PAYMENT_CHECK_CHECKOUT, API_PAYMENT_CHECK_LEGACY)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> resposta = postPaymentCheck(endpoint, body);
                if (respostaPagamentoConfirmado(resposta)) {
                    return true;
                }
                log.warn(
                        "InfinitePay payment_check nao confirmou em {}: order_nsu={} slug={} resposta={}",
                        endpoint,
                        orderNsu,
                        slug,
                        resposta
                );
            } catch (RestClientException ex) {
                log.warn(
                        "Falha ao consultar InfinitePay em {}: order_nsu={} slug={} erro={}",
                        endpoint,
                        orderNsu,
                        slug,
                        ex.getMessage()
                );
            }
        }
        return false;
    }

    public String resolverSlugPagamento(String slugSalvo, String linkPagamento) {
        if (slugSalvo != null && !slugSalvo.isBlank()) {
            return slugSalvo.trim();
        }
        return extrairSlugDoCheckoutUrl(linkPagamento);
    }

    public String extrairSlugDoCheckoutUrl(String linkPagamento) {
        if (linkPagamento == null || linkPagamento.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(linkPagamento.trim());
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String[] partes = path.split("/");
            for (int i = partes.length - 1; i >= 0; i--) {
                String parte = partes[i] == null ? "" : partes[i].trim();
                if (parte.isBlank() || "pay".equalsIgnoreCase(parte)) {
                    continue;
                }
                if (parte.equalsIgnoreCase(properties.getHandle())) {
                    continue;
                }
                return parte;
            }
        } catch (IllegalArgumentException ex) {
            log.warn("Nao foi possivel extrair slug do checkout InfinitePay: {}", linkPagamento);
        }
        return null;
    }

    private Map<String, Object> montarCorpoPaymentCheck(String orderNsu, String slug, String transactionNsu) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("handle", properties.getHandle());
        body.put("order_nsu", orderNsu.trim());
        if (slug != null && !slug.isBlank()) {
            body.put("slug", slug.trim());
        }
        if (transactionNsu != null && !transactionNsu.isBlank()) {
            body.put("transaction_nsu", transactionNsu.trim());
        }
        return body;
    }

    private Map<String, Object> postPaymentCheck(String endpoint, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        @SuppressWarnings("unchecked")
        Map<String, Object> resposta = restTemplate.postForObject(endpoint, request, Map.class);
        return resposta;
    }

    private boolean respostaPagamentoConfirmado(Map<String, Object> resposta) {
        if (resposta == null) {
            return false;
        }
        Object paid = resposta.get("paid");
        if (Boolean.TRUE.equals(paid)) {
            return true;
        }
        Object success = resposta.get("success");
        return Boolean.TRUE.equals(success) && Boolean.TRUE.equals(paid);
    }

    /**
     * Valor cobrado no PIX: taxa da clinica salva no agendamento ou tarifa padrao
     * (Sala 4 = 25, avulso/semanal/quinzenal = 35, mensal = 32, indicacao = 30%).
     */
    public BigDecimal resolverValorTaxaClinica(Agendamento agendamento) {
        if (agendamento == null) {
            return BigDecimal.ZERO;
        }
        if (agendamento.getValorPagamento() != null && agendamento.getValorPagamento().signum() > 0) {
            return agendamento.getValorPagamento().setScale(2, RoundingMode.HALF_UP);
        }
        if (agendamento.getValorClinicaCobra() != null && agendamento.getValorClinicaCobra().signum() > 0) {
            return agendamento.getValorClinicaCobra().setScale(2, RoundingMode.HALF_UP);
        }
        if (Boolean.TRUE.equals(agendamento.getIndicacaoDona())
                && agendamento.getValorProfissionalRecebe() != null
                && agendamento.getValorProfissionalRecebe().signum() > 0) {
            return valorConsultaService.calcularTarifaClinicaIndicacao(agendamento.getValorProfissionalRecebe());
        }
        return valorConsultaService.calcularTarifaClinicaPadrao(
                agendamento.getSala(),
                recorrenciaDoAgendamento(agendamento)
        );
    }

    private String recorrenciaDoAgendamento(Agendamento agendamento) {
        if (agendamento.getTipoRecorrencia() != null && !agendamento.getTipoRecorrencia().isBlank()) {
            return agendamento.getTipoRecorrencia();
        }
        if (Boolean.TRUE.equals(agendamento.getFixo())) {
            return "SEMANAL";
        }
        return "AVULSO";
    }

    private String descricaoItem(Agendamento agendamento) {
        String cliente = agendamento.getNomeCliente() != null ? agendamento.getNomeCliente() : "Consulta";
        String sala = agendamento.getSala() != null && agendamento.getSala().getNome() != null
                ? agendamento.getSala().getNome()
                : "Sala";
        return "Taxa clinica - " + cliente + " - " + sala;
    }
}
