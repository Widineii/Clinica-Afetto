package com.clinica.sistema.service;

import com.clinica.sistema.dto.PacienteAgendamentoCardView;
import com.clinica.sistema.dto.PacienteCadernoAnotacaoView;
import com.clinica.sistema.dto.PacienteCadernoLembreteView;
import com.clinica.sistema.dto.PacienteCadernoResumoSemanalView;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.EvolucaoClinica;
import com.clinica.sistema.model.PacienteCadernoObservacao;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.PacienteCadernoObservacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PacienteCadernoObservacaoService {

    private static final int TEXTO_MAXIMO = 2000;
    private static final Pattern CHAVE_CADERNO = Pattern.compile("^(av|sr|mn)-\\d+$");
    private static final DateTimeFormatter DATA_PERIODO = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter LEMBRETE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PacienteCadernoObservacaoRepository repository;
    private final AgendamentoRepository agendamentoRepository;
    private final AuthService authService;

    public PacienteCadernoObservacaoService(
            PacienteCadernoObservacaoRepository repository,
            AgendamentoRepository agendamentoRepository,
            AuthService authService
    ) {
        this.repository = repository;
        this.agendamentoRepository = agendamentoRepository;
        this.authService = authService;
    }

    public List<PacienteCadernoAnotacaoView> listar(Usuario profissional, String chaveCaderno) {
        validarChaveCaderno(chaveCaderno);
        if (profissional == null || profissional.getId() == null) {
            return List.of();
        }
        return repository
                .findByProfissionalIdAndChaveCadernoOrderByCriadoEmAsc(profissional.getId(), chaveCaderno.trim())
                .stream()
                .map(PacienteCadernoAnotacaoView::de)
                .toList();
    }

    @Transactional
    public PacienteCadernoAnotacaoView criar(
            Usuario profissional,
            String chaveCaderno,
            String texto,
            String evolucao,
            String lembreteEmTexto
    ) {
        validarChaveCaderno(chaveCaderno);
        String conteudo = validarTexto(texto);
        validarAcessoCaderno(profissional, chaveCaderno);

        LocalDateTime agora = LocalDateTime.now();
        PacienteCadernoObservacao observacao = new PacienteCadernoObservacao();
        observacao.setProfissional(profissional);
        observacao.setChaveCaderno(chaveCaderno.trim());
        observacao.setTexto(conteudo);
        observacao.setEvolucaoClinica(resolverEvolucao(evolucao));
        observacao.setLembreteEm(parseLembrete(lembreteEmTexto));
        observacao.setCriadoEm(agora);
        observacao.setAtualizadoEm(agora);
        return PacienteCadernoAnotacaoView.de(repository.save(observacao));
    }

    @Transactional
    public PacienteCadernoAnotacaoView atualizar(
            Usuario profissional,
            String chaveCaderno,
            Long anotacaoId,
            String texto,
            String evolucao,
            String lembreteEmTexto
    ) {
        validarChaveCaderno(chaveCaderno);
        if (anotacaoId == null) {
            throw new RuntimeException("Anotação não encontrada.");
        }
        if (profissional == null || profissional.getId() == null) {
            throw new RuntimeException("Usuário não autenticado.");
        }
        String conteudo = validarTexto(texto);

        PacienteCadernoObservacao observacao = repository.findByIdAndProfissionalId(anotacaoId, profissional.getId())
                .orElseThrow(() -> new RuntimeException("Anotação não encontrada."));
        if (!chaveCaderno.trim().equals(observacao.getChaveCaderno())) {
            throw new RuntimeException("Anotação não pertence a este paciente.");
        }
        validarAcessoCaderno(profissional, observacao.getChaveCaderno());

        observacao.setTexto(conteudo);
        observacao.setEvolucaoClinica(resolverEvolucao(evolucao));
        observacao.setLembreteEm(parseLembrete(lembreteEmTexto));
        observacao.setAtualizadoEm(LocalDateTime.now());
        return PacienteCadernoAnotacaoView.de(repository.save(observacao));
    }

    @Transactional
    public void apagar(Usuario profissional, String chaveCaderno) {
        if (profissional == null || profissional.getId() == null || chaveCaderno == null || chaveCaderno.isBlank()) {
            return;
        }
        repository.deleteByProfissionalIdAndChaveCaderno(profissional.getId(), chaveCaderno.trim());
    }

    public Map<String, String> montarTextoBuscaPorCard(Usuario profissional) {
        if (profissional == null || profissional.getId() == null) {
            return Map.of();
        }
        Map<String, StringBuilder> porCard = new LinkedHashMap<>();
        for (PacienteCadernoObservacao observacao : repository.findByProfissionalIdOrderByCriadoEmDesc(profissional.getId())) {
            String chave = observacao.getChaveCaderno();
            if (chave == null || chave.isBlank()) {
                continue;
            }
            porCard.computeIfAbsent(chave, chaveIgnorada -> new StringBuilder())
                    .append(' ')
                    .append(observacao.getTexto() != null ? observacao.getTexto() : "");
            EvolucaoClinica evolucao = EvolucaoClinica.parse(observacao.getEvolucaoClinica());
            if (evolucao != null) {
                porCard.get(chave).append(' ').append(evolucao.getRotulo());
            }
        }
        return porCard.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString().trim()));
    }

    public List<PacienteAgendamentoCardView> enriquecerCardsComBuscaAnotacoes(
            Usuario profissional,
            List<PacienteAgendamentoCardView> cards
    ) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        Map<String, String> textos = montarTextoBuscaPorCard(profissional);
        return cards.stream()
                .map(card -> {
                    String texto = textos.getOrDefault(card.getCardId(), "");
                    return texto.isBlank() ? card : card.comTextoBuscaAnotacoes(texto);
                })
                .toList();
    }

    public PacienteCadernoResumoSemanalView montarResumoSemanal(
            Usuario profissional,
            List<PacienteAgendamentoCardView> cards
    ) {
        if (profissional == null || profissional.getId() == null) {
            return PacienteCadernoResumoSemanalView.vazio();
        }
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicio = hoje.minusDays(6).atStartOfDay();
        List<PacienteCadernoObservacao> notas = repository
                .findByProfissionalIdAndCriadoEmGreaterThanEqualOrderByCriadoEmAsc(profissional.getId(), inicio);
        if (notas.isEmpty()) {
            return PacienteCadernoResumoSemanalView.vazio();
        }

        Map<String, String> nomes = cards != null
                ? cards.stream().collect(Collectors.toMap(
                PacienteAgendamentoCardView::getCardId,
                PacienteAgendamentoCardView::getNomeExibicao,
                (a, b) -> a
        ))
                : Map.of();

        Map<String, List<PacienteCadernoObservacao>> porCard = new LinkedHashMap<>();
        for (PacienteCadernoObservacao nota : notas) {
            porCard.computeIfAbsent(nota.getChaveCaderno(), chave -> new ArrayList<>()).add(nota);
        }

        List<String> linhas = new ArrayList<>();
        for (Map.Entry<String, List<PacienteCadernoObservacao>> entrada : porCard.entrySet()) {
            String nome = nomes.getOrDefault(entrada.getKey(), entrada.getKey());
            List<PacienteCadernoObservacao> lista = entrada.getValue();
            PacienteCadernoObservacao ultima = lista.get(lista.size() - 1);
            EvolucaoClinica evolucao = EvolucaoClinica.parse(ultima.getEvolucaoClinica());
            String trecho = resumirTexto(ultima.getTexto());
            StringBuilder linha = new StringBuilder("• ")
                    .append(nome)
                    .append(" — ")
                    .append(lista.size())
                    .append(lista.size() == 1 ? " anotação" : " anotações");
            if (evolucao != null) {
                linha.append(". Evolução: ").append(evolucao.getRotulo());
            }
            if (!trecho.isBlank()) {
                linha.append(". Última: \"").append(trecho).append('"');
            }
            linhas.add(linha.toString());
        }

        String periodo = inicio.format(DATA_PERIODO) + " – " + hoje.format(DATA_PERIODO);
        return new PacienteCadernoResumoSemanalView(periodo, linhas, notas.size());
    }

    public List<PacienteCadernoLembreteView> listarLembretesProximos(
            Usuario profissional,
            List<PacienteAgendamentoCardView> cards
    ) {
        if (profissional == null || profissional.getId() == null) {
            return List.of();
        }
        LocalDateTime inicio = LocalDate.now().atStartOfDay();
        LocalDateTime limite = LocalDate.now().plusDays(14).atTime(23, 59, 59);
        List<PacienteCadernoObservacao> lembretes = repository
                .findByProfissionalIdAndLembreteEmBetweenOrderByLembreteEmAsc(profissional.getId(), inicio, limite);
        if (lembretes.isEmpty()) {
            return List.of();
        }
        Map<String, String> nomes = cards != null
                ? cards.stream().collect(Collectors.toMap(
                PacienteAgendamentoCardView::getCardId,
                PacienteAgendamentoCardView::getNomeExibicao,
                (a, b) -> a
        ))
                : Map.of();

        return lembretes.stream()
                .map(observacao -> new PacienteCadernoLembreteView(
                        observacao.getId(),
                        observacao.getChaveCaderno(),
                        nomes.getOrDefault(observacao.getChaveCaderno(), observacao.getChaveCaderno()),
                        resumirTexto(observacao.getTexto()),
                        observacao.getLembreteEm() != null ? LEMBRETE.format(observacao.getLembreteEm()) : ""
                ))
                .toList();
    }

    public static String chaveAvulso(Long agendamentoId) {
        return "av-" + agendamentoId;
    }

    public static String chaveSerie(Long agendamentoReferenciaId) {
        return "sr-" + agendamentoReferenciaId;
    }

    public static String chaveMensal(Long agendamentoReferenciaId) {
        return "mn-" + agendamentoReferenciaId;
    }

    private String resumirTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String limpo = texto.trim().replaceAll("\\s+", " ");
        return limpo.length() <= 72 ? limpo : limpo.substring(0, 69) + "...";
    }

    private String resolverEvolucao(String evolucao) {
        EvolucaoClinica valor = EvolucaoClinica.parse(evolucao);
        return valor != null ? valor.name() : null;
    }

    private LocalDateTime parseLembrete(String lembreteEmTexto) {
        if (lembreteEmTexto == null || lembreteEmTexto.isBlank()) {
            return null;
        }
        String valor = lembreteEmTexto.trim();
        try {
            if (valor.length() <= 10 && !valor.contains("T")) {
                return LocalDate.parse(valor).atStartOfDay();
            }
            return LocalDateTime.parse(valor);
        } catch (Exception ignored) {
            // compatibilidade com registros antigos em datetime-local
        }
        try {
            return LocalDateTime.parse(valor, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        } catch (Exception e) {
            throw new RuntimeException("Data do lembrete inválida.");
        }
    }

    private String validarTexto(String texto) {
        String conteudo = texto != null ? texto.trim() : "";
        if (conteudo.isEmpty()) {
            throw new RuntimeException("Escreva algo antes de salvar a anotação.");
        }
        if (conteudo.length() > TEXTO_MAXIMO) {
            throw new RuntimeException("A anotação pode ter no máximo " + TEXTO_MAXIMO + " caracteres.");
        }
        return conteudo;
    }

    private void validarChaveCaderno(String chaveCaderno) {
        if (chaveCaderno == null || !CHAVE_CADERNO.matcher(chaveCaderno.trim()).matches()) {
            throw new RuntimeException("Caderno de paciente inválido.");
        }
    }

    private void validarAcessoCaderno(Usuario profissional, String chaveCaderno) {
        if (profissional == null || profissional.getId() == null) {
            throw new RuntimeException("Usuário não autenticado.");
        }
        if (!authService.podeAcessarMeusPacientes(profissional)) {
            throw new RuntimeException("Sem permissão para anotar neste caderno.");
        }

        String chave = chaveCaderno.trim();
        String prefixo = chave.substring(0, 2);
        long agendamentoId = Long.parseLong(chave.substring(3));
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RuntimeException("Atendimento não encontrado para este caderno."));

        if (agendamento.getProfissional() == null
                || agendamento.getProfissional().getId() == null
                || !agendamento.getProfissional().getId().equals(profissional.getId())) {
            throw new RuntimeException("Sem permissão para anotar neste caderno.");
        }

        if ("av".equals(prefixo) && !agendamento.isAvulsoSemMensal()) {
            throw new RuntimeException("Caderno avulso inválido.");
        }
        if ("sr".equals(prefixo) && (agendamento.getSerieFixaId() == null || agendamento.getSerieFixaId().isBlank())) {
            throw new RuntimeException("Caderno de série inválido.");
        }
        if ("mn".equals(prefixo) && !agendamento.isMensal()) {
            throw new RuntimeException("Caderno mensal inválido.");
        }
    }
}
