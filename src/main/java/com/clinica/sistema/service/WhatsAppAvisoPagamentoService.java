package com.clinica.sistema.service;

import com.clinica.sistema.config.WhatsAppMetaProperties;
import com.clinica.sistema.dto.AvisoWhatsappPeriodicidadePainelView;
import com.clinica.sistema.dto.PendenciasPagamentoWhatsappAgrupadasView;
import com.clinica.sistema.dto.ProfissionalPendenciaPagamentoWhatsappView;
import com.clinica.sistema.model.PeriodicidadePagamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WhatsAppAvisoPagamentoService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppAvisoPagamentoService.class);

    private final WhatsAppMetaProperties properties;
    private final WhatsAppNotificacaoService notificacaoService;
    private final WhatsAppMensagemPagamentoService mensagemPagamentoService;
    private final PagamentoConsultaService pagamentoConsultaService;

    public WhatsAppAvisoPagamentoService(
            WhatsAppMetaProperties properties,
            WhatsAppNotificacaoService notificacaoService,
            WhatsAppMensagemPagamentoService mensagemPagamentoService,
            PagamentoConsultaService pagamentoConsultaService
    ) {
        this.properties = properties;
        this.notificacaoService = notificacaoService;
        this.mensagemPagamentoService = mensagemPagamentoService;
        this.pagamentoConsultaService = pagamentoConsultaService;
    }

    public boolean avisoAutomaticoAtivo() {
        return properties.avisoPagamentoPronto();
    }

    @Transactional(readOnly = true)
    public List<AvisoWhatsappPeriodicidadePainelView> montarPainelCentral() {
        PendenciasPagamentoWhatsappAgrupadasView agrupadas =
                pagamentoConsultaService.agruparProfissionaisMensagemWhatsappPorPeriodicidade();
        String fraseExemplo = montarTextoParaProfissional("Maria", "R$ 50,00");
        return List.of(
                secao(PeriodicidadePagamento.DIARIO, "Diário", "fa-solid fa-calendar-day",
                        "Automático todo dia às 10h, se houver pendência.", fraseExemplo, agrupadas.diario()),
                secao(PeriodicidadePagamento.SEMANAL, "Semanal", "fa-solid fa-calendar-week",
                        "Automático sábado e domingo às 13h, se houver pendência.", fraseExemplo, agrupadas.semanal()),
                secao(PeriodicidadePagamento.MENSAL, "Mensal", "fa-solid fa-calendar-days",
                        "Automático nos dias 5 e 10 às 13h, se houver pendência.", fraseExemplo, agrupadas.mensal())
        );
    }

    @Transactional
    public int processarAvisosDiario() {
        return processarAvisos(PeriodicidadePagamento.DIARIO, "diario 10h");
    }

    @Transactional
    public int processarAvisosSemanalSabado() {
        return processarAvisos(PeriodicidadePagamento.SEMANAL, "semanal sabado 13h");
    }

    @Transactional
    public int processarAvisosSemanalDomingo() {
        return processarAvisos(PeriodicidadePagamento.SEMANAL, "semanal domingo 13h");
    }

    @Transactional
    public int processarAvisosMensalDia5() {
        return processarAvisos(PeriodicidadePagamento.MENSAL, "mensal dia 5 13h");
    }

    @Transactional
    public int processarAvisosMensalDia10() {
        return processarAvisos(PeriodicidadePagamento.MENSAL, "mensal dia 10 13h");
    }

    private int processarAvisos(PeriodicidadePagamento periodicidade, String rotuloJanela) {
        if (!avisoAutomaticoAtivo()) {
            return 0;
        }
        List<ProfissionalPendenciaPagamentoWhatsappView> profissionais =
                pagamentoConsultaService.listarProfissionaisMensagemWhatsappPorPeriodicidade(periodicidade);
        int enviados = 0;
        for (ProfissionalPendenciaPagamentoWhatsappView linha : profissionais) {
            if (linha.quantidadePendencias() <= 0 || !linha.temWhatsapp()) {
                continue;
            }
            String texto = montarTextoParaProfissional(linha.nome(), linha.valorTotalFormatado());
            if (notificacaoService.tentarEnviarTexto(linha.telefoneWhatsapp(), texto)) {
                enviados++;
            }
        }
        if (enviados > 0) {
            log.info("WhatsApp aviso pagamento ({}): {} mensagem(ns) enviada(s).", rotuloJanela, enviados);
        }
        return enviados;
    }

    private String montarTextoParaProfissional(String nome, String total) {
        return pagamentoConsultaService.aplicarVariaveisFraseWhatsapp(
                mensagemPagamentoService.resolverTextoGeral(),
                nome,
                total
        );
    }

    private AvisoWhatsappPeriodicidadePainelView secao(
            PeriodicidadePagamento periodicidade,
            String titulo,
            String icone,
            String horarioEnvio,
            String fraseExemplo,
            List<ProfissionalPendenciaPagamentoWhatsappView> profissionais
    ) {
        return new AvisoWhatsappPeriodicidadePainelView(
                periodicidade,
                titulo,
                icone,
                horarioEnvio,
                fraseExemplo,
                profissionais
        );
    }
}
