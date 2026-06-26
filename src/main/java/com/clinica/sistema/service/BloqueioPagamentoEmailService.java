package com.clinica.sistema.service;

import com.clinica.sistema.config.AvisoPagamentoEmailProperties;
import com.clinica.sistema.dto.ProfissionalBloqueioPagamentoEmailView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BloqueioPagamentoEmailService {

    private static final Logger log = LoggerFactory.getLogger(BloqueioPagamentoEmailService.class);

    private final AvisoPagamentoEmailProperties properties;
    private final EmailEnvioService emailEnvioService;
    private final PagamentoConsultaService pagamentoConsultaService;

    public BloqueioPagamentoEmailService(
            AvisoPagamentoEmailProperties properties,
            EmailEnvioService emailEnvioService,
            PagamentoConsultaService pagamentoConsultaService
    ) {
        this.properties = properties;
        this.emailEnvioService = emailEnvioService;
        this.pagamentoConsultaService = pagamentoConsultaService;
    }

    public boolean avisoBloqueioAtivo() {
        return properties.isEnabled()
                && properties.isBloqueioEnabled()
                && emailEnvioService.envioRealDisponivel();
    }

    @Transactional(readOnly = true)
    public int processarAvisoDiarioBloqueio() {
        if (!avisoBloqueioAtivo()) {
            return 0;
        }

        List<ProfissionalBloqueioPagamentoEmailView> bloqueados =
                pagamentoConsultaService.listarProfissionaisBloqueadosParaAvisoEmail();
        int enviados = 0;
        for (ProfissionalBloqueioPagamentoEmailView linha : bloqueados) {
            if (enviarParaProfissional(linha)) {
                enviados++;
            }
        }
        if (enviados > 0) {
            log.info("[BloqueioPagamentoEmail] {} e-mail(s) de bloqueio enviado(s).", enviados);
        }
        return enviados;
    }

    private boolean enviarParaProfissional(ProfissionalBloqueioPagamentoEmailView linha) {
        String destino = normalizarEmail(linha.email());
        if (destino.isBlank()) {
            return false;
        }
        String urlPagamentos = normalizarUrlSite(properties.getUrlSite()) + "/agendamentos/meus-pagamentos";
        String assunto = "Agenda bloqueada — quite o pagamento | Agenda Afetto";
        BloqueioPagamentoEmailTemplate.ConteudoEmail conteudo = BloqueioPagamentoEmailTemplate.montar(
                linha.nome(),
                linha.mensagemBloqueio(),
                linha.valorTotalFormatado(),
                urlPagamentos
        );
        return emailEnvioService.enviarHtml(destino, assunto, conteudo.textoPlano(), conteudo.html());
    }

    private String normalizarEmail(String email) {
        return email != null ? email.trim().toLowerCase() : "";
    }

    private String normalizarUrlSite(String urlSite) {
        if (urlSite == null || urlSite.isBlank()) {
            return "http://localhost:8081";
        }
        String base = urlSite.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
