package com.clinica.sistema.service;

import com.clinica.sistema.dto.NovoAgendamentoNotificacaoView;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.NovoAgendamentoNotificacaoRegistro;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.NovoAgendamentoNotificacaoRegistroRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class NovoAgendamentoNotificacaoService {

    private static final String SESSAO_ULTIMO_NOVO_AGENDAMENTO_VISTO_ID = "ultimoNovoAgendamentoNotificacaoVistoId";
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"));
    private static final int LIMITE_ITENS_PAINEL = 8;

    private final NovoAgendamentoNotificacaoRegistroRepository repository;

    public NovoAgendamentoNotificacaoService(NovoAgendamentoNotificacaoRegistroRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrarNovosAgendamentos(List<Agendamento> criados, Usuario registradoPor) {
        if (criados == null || criados.isEmpty() || registradoPor == null) {
            return;
        }
        Agendamento primeiro = criados.get(0);
        if (primeiro.getProfissional() == null || primeiro.getDataHoraInicio() == null) {
            return;
        }

        NovoAgendamentoNotificacaoRegistro registro = new NovoAgendamentoNotificacaoRegistro();
        registro.setAgendamentoId(primeiro.getId());
        registro.setNomeCliente(primeiro.getNomeCliente() != null ? primeiro.getNomeCliente().trim() : "Cliente");
        registro.setProfissional(primeiro.getProfissional());
        registro.setSala(primeiro.getSala());
        registro.setRegistradoPor(registradoPor);
        registro.setDataHoraInicio(primeiro.getDataHoraInicio());
        registro.setTipoRecorrencia(normalizarTipoRecorrencia(primeiro.getTipoRecorrencia()));
        registro.setQuantidadeHorarios(criados.size());
        registro.setAguardandoAprovacaoIndicacao(criados.stream()
                .anyMatch(a -> a.getStatusPagamento() == PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO));
        registro.setRegistradoEm(LocalDateTime.now());
        repository.save(registro);
    }

    public List<NovoAgendamentoNotificacaoView> listarPendentes(HttpSession session) {
        long ultimoVistoId = obterUltimoVistoId(session);
        return repository.findTop15ByIdGreaterThanOrderByRegistradoEmDescIdDesc(ultimoVistoId).stream()
                .limit(LIMITE_ITENS_PAINEL)
                .map(this::mapearView)
                .toList();
    }

    public long contarPendentes(HttpSession session) {
        return repository.countByIdGreaterThan(obterUltimoVistoId(session));
    }

    public boolean deveExibirBolinha(HttpSession session) {
        return contarPendentes(session) > 0;
    }

    public void adicionarNotificacaoAoModelSeAplicavel(Model model, HttpSession session) {
        List<NovoAgendamentoNotificacaoView> pendentes = listarPendentes(session);
        long totalPendentes = contarPendentes(session);
        boolean exibir = !pendentes.isEmpty();
        model.addAttribute("notificacoesNovosAgendamentos", pendentes);
        model.addAttribute("totalNotificacoesNovosAgendamentos", totalPendentes);
        model.addAttribute("exibirBolinhaNotificacaoNovoAgendamento", exibir);
    }

    public void marcarComoVisto(HttpSession session) {
        if (session == null) {
            return;
        }
        repository.findFirstByOrderByRegistradoEmDescIdDesc()
                .map(NovoAgendamentoNotificacaoRegistro::getId)
                .ifPresent(id -> session.setAttribute(SESSAO_ULTIMO_NOVO_AGENDAMENTO_VISTO_ID, id));
    }

    public long obterUltimoVistoId(HttpSession session) {
        if (session == null) {
            return 0L;
        }
        Object valor = session.getAttribute(SESSAO_ULTIMO_NOVO_AGENDAMENTO_VISTO_ID);
        if (valor instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private NovoAgendamentoNotificacaoView mapearView(NovoAgendamentoNotificacaoRegistro registro) {
        String profissional = nomeSeguro(registro.getProfissional());
        String cliente = registro.getNomeCliente() != null ? registro.getNomeCliente().trim() : "Cliente";
        String sala = registro.getSala() != null && registro.getSala().getNome() != null
                ? registro.getSala().getNome().trim()
                : "Sala";
        String tipo = rotuloTipoRecorrencia(registro.getTipoRecorrencia());
        String dataHora = registro.getDataHoraInicio().format(DATA_HORA);

        String resumo = registro.getQuantidadeHorarios() > 1
                ? profissional + " agendou " + tipo + " (" + registro.getQuantidadeHorarios() + " horários)"
                : profissional + " agendou " + tipo;

        StringBuilder detalhe = new StringBuilder();
        detalhe.append("Cliente: ").append(cliente);
        detalhe.append(" · Sala: ").append(sala);
        detalhe.append(" · ").append(dataHora);
        if (registro.isAguardandoAprovacaoIndicacao()) {
            detalhe.append(" · Indicação aguardando aprovação");
        }
        if (!nomeSeguro(registro.getRegistradoPor()).equals(profissional)) {
            detalhe.append(" · Registrado por ").append(nomeSeguro(registro.getRegistradoPor()));
        }

        LocalDate semana = registro.getDataHoraInicio().toLocalDate()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        Long salaId = registro.getSala() != null ? registro.getSala().getId() : null;
        String urlAgenda = "/agendamentos/dashboard?viaNotificacaoNovoAgendamento=true&semana=" + semana
                + (salaId != null ? "&salaId=" + salaId : "");

        return new NovoAgendamentoNotificacaoView(
                registro.getId(),
                resumo,
                detalhe.toString(),
                dataHora,
                urlAgenda
        );
    }

    private String nomeSeguro(Usuario usuario) {
        if (usuario == null || usuario.getNome() == null || usuario.getNome().isBlank()) {
            return "Profissional";
        }
        return usuario.getNome().trim();
    }

    private String normalizarTipoRecorrencia(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return "AVULSO";
        }
        return tipo.trim().toUpperCase(Locale.ROOT);
    }

    private String rotuloTipoRecorrencia(String tipo) {
        return switch (normalizarTipoRecorrencia(tipo)) {
            case "SEMANAL" -> "série semanal";
            case "QUINZENAL" -> "série quinzenal";
            case "MENSAL" -> "consulta mensal";
            default -> "consulta avulsa";
        };
    }
}
