package com.clinica.sistema.service;

import com.clinica.sistema.config.AvisoPagamentoEmailProperties;
import com.clinica.sistema.dto.AvisoWhatsappPeriodicidadePainelView;
import com.clinica.sistema.dto.ProfissionalPendenciaPagamentoWhatsappView;
import com.clinica.sistema.model.AvisoPagamentoEmailJanela;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AvisoPagamentoEmailService {

    private static final Logger log = LoggerFactory.getLogger(AvisoPagamentoEmailService.class);

    private final AvisoPagamentoEmailProperties properties;
    private final EmailEnvioService emailEnvioService;
    private final PagamentoConsultaService pagamentoConsultaService;

    public AvisoPagamentoEmailService(
            AvisoPagamentoEmailProperties properties,
            EmailEnvioService emailEnvioService,
            PagamentoConsultaService pagamentoConsultaService
    ) {
        this.properties = properties;
        this.emailEnvioService = emailEnvioService;
        this.pagamentoConsultaService = pagamentoConsultaService;
    }

    public boolean avisoAutomaticoAtivo() {
        return properties.isEnabled() && emailEnvioService.envioRealDisponivel();
    }

    @Transactional(readOnly = true)
    public List<AvisoWhatsappPeriodicidadePainelView> montarPainelCentral() {
        var agrupadas = pagamentoConsultaService.agruparProfissionaisMensagemWhatsappPorPeriodicidade();
        return List.of(
                secao(PeriodicidadePagamento.DIARIO, "Diário", "fa-solid fa-calendar-day",
                        "Automático todo dia às 8h e às 17h, se houver pendência.",
                        exemplo(PeriodicidadePagamento.DIARIO), agrupadas.diario()),
                secao(PeriodicidadePagamento.SEMANAL, "Semanal", "fa-solid fa-calendar-week",
                        "Automático sábado 17h, domingo 8h e domingo 17h, se houver pendência.",
                        exemplo(PeriodicidadePagamento.SEMANAL), agrupadas.semanal()),
                secao(PeriodicidadePagamento.MENSAL, "Mensal", "fa-solid fa-calendar-days",
                        "Automático dias 1, 5 e 10 (8h e 17h no dia 10), se houver pendência.",
                        exemplo(PeriodicidadePagamento.MENSAL), agrupadas.mensal())
        );
    }

    @Transactional
    public int processarJanela(AvisoPagamentoEmailJanela janela) {
        if (!avisoAutomaticoAtivo()) {
            return 0;
        }
        List<ProfissionalPendenciaPagamentoWhatsappView> profissionais =
                pagamentoConsultaService.listarProfissionaisMensagemWhatsappPorPeriodicidade(janela.getPeriodicidade());
        int enviados = 0;
        for (ProfissionalPendenciaPagamentoWhatsappView linha : profissionais) {
            if (linha.quantidadePendencias() <= 0 || !linha.temEmail()) {
                continue;
            }
            if (enviarParaProfissional(linha.email(), linha.nome(), linha.valorTotalFormatado(), janela)) {
                enviados++;
            }
        }
        if (enviados > 0) {
            log.info("[AvisoPagamentoEmail] {} e-mail(s) enviado(s) — {}.", enviados, janela.name());
        }
        return enviados;
    }

    @Transactional
    public void enviarManualProfissional(Long profissionalId, AvisoPagamentoEmailJanela janela) {
        Usuario profissional = pagamentoConsultaService.buscarProfissionalParaAvisoWhatsapp(profissionalId);
        String email = normalizarEmail(profissional.getEmail());
        if (email.isBlank()) {
            throw new RuntimeException("Cadastre o e-mail do profissional antes de enviar o aviso.");
        }
        var resumo = pagamentoConsultaService.montarResumoPendenciasPagamento(profissional);
        if (!resumo.temPendencias()) {
            throw new RuntimeException("Este profissional nao tem pendencia de pagamento.");
        }
        AvisoPagamentoEmailJanela janelaEfetiva = janela != null
                ? janela
                : janelaPadraoParaPeriodicidade(pagamentoConsultaService.resolverPeriodicidade(profissional));
        if (!enviarParaProfissional(email, profissional.getNome(), resumo.valorTotalFormatado(), janelaEfetiva)) {
            throw new RuntimeException("Nao foi possivel enviar o e-mail agora. Tente novamente em alguns minutos.");
        }
    }

    private boolean enviarParaProfissional(
            String email,
            String nome,
            String total,
            AvisoPagamentoEmailJanela janela
    ) {
        String destino = normalizarEmail(email);
        if (destino.isBlank()) {
            return false;
        }
        String urlPagamentos = normalizarUrlSite(properties.getUrlSite()) + "/agendamentos/meus-pagamentos";
        String assunto = "Pagamento pendente — " + janela.getTituloAssunto() + " | Agenda Afetto";
        AvisoPagamentoEmailTemplate.ConteudoEmail conteudo = AvisoPagamentoEmailTemplate.montar(
                janela,
                nome,
                total,
                urlPagamentos
        );
        return emailEnvioService.enviarHtml(destino, assunto, conteudo.textoPlano(), conteudo.html());
    }

    private AvisoPagamentoEmailJanela janelaPadraoParaPeriodicidade(PeriodicidadePagamento periodicidade) {
        return switch (periodicidade) {
            case DIARIO -> AvisoPagamentoEmailJanela.DIARIO_MANHA;
            case SEMANAL -> AvisoPagamentoEmailJanela.SEMANAL_SABADO_TARDE;
            case MENSAL -> AvisoPagamentoEmailJanela.MENSAL_DIA1;
        };
    }

    private String exemplo(PeriodicidadePagamento periodicidade) {
        AvisoPagamentoEmailJanela janela = janelaPadraoParaPeriodicidade(periodicidade);
        return janela.montarCorpo("Maria", "R$ 50,00");
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
