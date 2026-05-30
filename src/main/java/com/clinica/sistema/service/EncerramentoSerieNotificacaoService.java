package com.clinica.sistema.service;

import com.clinica.sistema.dto.EncerramentoSerieNotificacaoView;
import com.clinica.sistema.model.EncerramentoSerieRegistro;
import com.clinica.sistema.repository.EncerramentoSerieRegistroRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.util.Optional;

@Service
public class EncerramentoSerieNotificacaoService {

    public static final String URL_ENCERRAMENTOS_VIA_NOTIFICACAO =
            "/agendamentos/central-profissionais?aba=encerramentos&viaNotificacaoEncerramento=1";

    public static final String SESSAO_ULTIMO_ENCERRAMENTO_VISTO_ID =
            "notificacaoEncerramentoSerieUltimoVistoId";

    private final EncerramentoSerieRegistroRepository encerramentoSerieRegistroRepository;

    public EncerramentoSerieNotificacaoService(
            EncerramentoSerieRegistroRepository encerramentoSerieRegistroRepository
    ) {
        this.encerramentoSerieRegistroRepository = encerramentoSerieRegistroRepository;
    }

    public Optional<EncerramentoSerieNotificacaoView> avaliarNotificacao(HttpSession session) {
        Optional<EncerramentoSerieRegistro> maisRecente =
                encerramentoSerieRegistroRepository.findFirstByOrderByEncerradoEmDescIdDesc();
        if (maisRecente.isEmpty()) {
            return Optional.empty();
        }

        long ultimoVistoId = obterUltimoVistoId(session);
        long pendentes = encerramentoSerieRegistroRepository.countByIdGreaterThan(ultimoVistoId);
        if (pendentes <= 0) {
            return Optional.empty();
        }

        EncerramentoSerieRegistro registro = maisRecente.get();
        String encerradoPor = registro.getEncerradoPor() != null
                ? registro.getEncerradoPor().getNome()
                : "Alguém";
        String cliente = registro.getNomeCliente() != null ? registro.getNomeCliente().trim() : "cliente";
        String tipoSerie = "QUINZENAL".equalsIgnoreCase(registro.getTipoRecorrencia())
                ? "quinzenal"
                : "semanal";

        String mensagemResumo = pendentes == 1
                ? encerradoPor + " encerrou uma série " + tipoSerie
                : pendentes + " séries encerradas recentemente";

        String mensagemPainel = pendentes == 1
                ? encerradoPor + " encerrou a série " + tipoSerie + " de " + cliente + "."
                : "Há " + pendentes + " encerramentos novos. O mais recente: "
                        + encerradoPor + " encerrou a série de " + cliente + ".";

        return Optional.of(new EncerramentoSerieNotificacaoView(
                pendentes,
                mensagemResumo,
                mensagemPainel,
                URL_ENCERRAMENTOS_VIA_NOTIFICACAO
        ));
    }

    public boolean deveExibirBolinha(HttpSession session) {
        return avaliarNotificacao(session).isPresent();
    }

    public void adicionarNotificacaoAoModelSeAplicavel(Model model, HttpSession session) {
        Optional<EncerramentoSerieNotificacaoView> notificacao = avaliarNotificacao(session);
        boolean exibirBolinha = notificacao.isPresent();
        model.addAttribute("notificacaoEncerramentoSerie", exibirBolinha ? notificacao.orElse(null) : null);
        model.addAttribute("exibirBolinhaNotificacaoEncerramento", exibirBolinha);
    }

    public void marcarComoVisto(HttpSession session) {
        if (session == null) {
            return;
        }
        encerramentoSerieRegistroRepository.findFirstByOrderByEncerradoEmDescIdDesc()
                .map(EncerramentoSerieRegistro::getId)
                .ifPresent(id -> session.setAttribute(SESSAO_ULTIMO_ENCERRAMENTO_VISTO_ID, id));
    }

    public long obterUltimoVistoId(HttpSession session) {
        if (session == null) {
            return 0L;
        }
        Object valor = session.getAttribute(SESSAO_ULTIMO_ENCERRAMENTO_VISTO_ID);
        if (valor instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
