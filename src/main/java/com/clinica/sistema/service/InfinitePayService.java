package com.clinica.sistema.service;

import com.clinica.sistema.config.InfinitePayProperties;
import com.clinica.sistema.dto.LinkPagamentoGerado;
import com.clinica.sistema.model.Agendamento;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InfinitePayService {

    private static final String API_LINKS = "https://api.checkout.infinitepay.io/links";

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
            Object url = resposta.get("url");
            Object slug = resposta.get("slug");
            if (url == null || url.toString().isBlank()) {
                throw new RuntimeException("InfinitePay nao retornou link de pagamento.");
            }
            return new LinkPagamentoGerado(
                    orderNsu,
                    url.toString(),
                    slug != null ? slug.toString() : null
            );
        } catch (RestClientException ex) {
            throw new RuntimeException("Falha ao gerar link InfinitePay: " + ex.getMessage());
        }
    }

    public BigDecimal valorPagamento(Agendamento agendamento) {
        return resolverValorTaxaClinica(agendamento);
    }

    /**
     * Valor cobrado no PIX: taxa da clinica salva no agendamento ou tarifa padrao
     * (Sala 4 = 25, fixo = 32, avulso/quinzenal = 35, indicacao = 30%).
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
