package com.clinica.sistema.service;

import com.clinica.sistema.dto.ProfissionalReceitaPainelView;
import com.clinica.sistema.dto.ReceitaPixLinhaView;
import com.clinica.sistema.dto.ReceitaPixMesView;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FinanceiroReceitaPixService {

    private static final DateTimeFormatter MES_ANO_LABEL =
            DateTimeFormatter.ofPattern("MMMM 'de' yyyy", new Locale("pt", "BR"));

    private final AgendamentoRepository agendamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SalaRepository salaRepository;
    private final InfinitePayService infinitePayService;

    public FinanceiroReceitaPixService(
            AgendamentoRepository agendamentoRepository,
            UsuarioRepository usuarioRepository,
            SalaRepository salaRepository,
            InfinitePayService infinitePayService
    ) {
        this.agendamentoRepository = agendamentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.salaRepository = salaRepository;
        this.infinitePayService = infinitePayService;
    }

    public ReceitaPixMesView montarResumoMes(YearMonth mesSelecionado) {
        LocalDateTime inicio = mesSelecionado.atDay(1).atStartOfDay();
        LocalDateTime fim = mesSelecionado.plusMonths(1).atDay(1).atStartOfDay();

        List<ReceitaPixLinhaView> linhas = agendamentoRepository
                .findPagosPorDataPagamentoNoPeriodo(inicio, fim)
                .stream()
                .filter(this::contaParaReceitaClinica)
                .map(this::paraLinha)
                .toList();

        BigDecimal total = linhas.stream()
                .map(ReceitaPixLinhaView::getValorTaxa)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ProfissionalReceitaPainelView> profissionaisPainel =
                mesclarEquipeCompleta(montarPainelProfissionais(mesSelecionado));

        List<String> salasFiltro = salaRepository.findAllByOrderByNomeAsc().stream()
                .map(sala -> sala.getNome())
                .filter(nome -> nome != null && !nome.isBlank())
                .toList();

        return new ReceitaPixMesView(mesSelecionado, linhas, total, profissionaisPainel, salasFiltro);
    }

    private List<ProfissionalReceitaPainelView> montarPainelProfissionais(YearMonth mesSelecionado) {
        Map<String, String> nomesPorChave = new HashMap<>();
        Map<String, Map<YearMonth, ResumoMensalProfissional>> resumosPorProfissional = new HashMap<>();

        for (Agendamento agendamento : agendamentoRepository.findTodosPagosComDataPagamento()) {
            if (!contaParaReceitaClinica(agendamento) || agendamento.getDataPagamento() == null) {
                continue;
            }
            if (agendamento.getProfissional() == null || agendamento.getProfissional().getNome() == null) {
                continue;
            }

            String chave = normalizarChave(agendamento.getProfissional().getNome());
            if (chave.isBlank()) {
                continue;
            }

            nomesPorChave.putIfAbsent(chave, agendamento.getProfissional().getNome());
            YearMonth mesPagamento = YearMonth.from(agendamento.getDataPagamento());
            Map<YearMonth, ResumoMensalProfissional> resumosMensais = resumosPorProfissional
                    .computeIfAbsent(chave, ignored -> new HashMap<>());
            ResumoMensalProfissional resumoMensal = resumosMensais
                    .computeIfAbsent(mesPagamento, ignored -> new ResumoMensalProfissional());
            resumoMensal.quantidade++;
            resumoMensal.valor = resumoMensal.valor.add(infinitePayService.resolverValorTaxaClinica(agendamento));
        }

        return resumosPorProfissional.entrySet().stream()
                .sorted(Comparator.comparing(entry -> nomesPorChave.get(entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                .map(entry -> construirPainelProfissional(
                        entry.getKey(),
                        nomesPorChave.get(entry.getKey()),
                        entry.getValue(),
                        mesSelecionado
                ))
                .toList();
    }

    private List<ProfissionalReceitaPainelView> mesclarEquipeCompleta(
            List<ProfissionalReceitaPainelView> painelComDados
    ) {
        Map<String, ProfissionalReceitaPainelView> porChave = new HashMap<>();
        for (ProfissionalReceitaPainelView item : painelComDados) {
            porChave.put(item.getChave(), item);
        }

        for (Usuario profissional : usuarioRepository.findByCargoOrderByNomeAsc("ROLE_PROFISSIONAL")) {
            if (Boolean.TRUE.equals(profissional.getDonaClinica())) {
                continue;
            }
            String chave = normalizarChave(profissional.getNome());
            if (chave.isBlank() || porChave.containsKey(chave)) {
                continue;
            }
            porChave.put(chave, new ProfissionalReceitaPainelView(
                    chave,
                    profissional.getNome(),
                    BigDecimal.ZERO,
                    0,
                    "—",
                    BigDecimal.ZERO,
                    0
            ));
        }

        return porChave.values().stream()
                .sorted(Comparator.comparing(ProfissionalReceitaPainelView::getNome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ProfissionalReceitaPainelView construirPainelProfissional(
            String chave,
            String nome,
            Map<YearMonth, ResumoMensalProfissional> resumosMensais,
            YearMonth mesSelecionado
    ) {
        ResumoMensalProfissional mesAtual = resumosMensais.getOrDefault(mesSelecionado, new ResumoMensalProfissional());
        Map.Entry<YearMonth, ResumoMensalProfissional> melhorMes = resumosMensais.entrySet().stream()
                .max(Comparator
                        .comparing((Map.Entry<YearMonth, ResumoMensalProfissional> entry) -> entry.getValue().quantidade)
                        .thenComparing(entry -> entry.getValue().valor))
                .orElse(null);

        String melhorMesLabel = melhorMes != null ? formatarMes(melhorMes.getKey()) : "—";
        BigDecimal valorMelhorMes = melhorMes != null ? melhorMes.getValue().valor : BigDecimal.ZERO;
        int atendimentosMelhorMes = melhorMes != null ? melhorMes.getValue().quantidade : 0;

        return new ProfissionalReceitaPainelView(
                chave,
                nome,
                mesAtual.valor,
                mesAtual.quantidade,
                melhorMesLabel,
                valorMelhorMes,
                atendimentosMelhorMes
        );
    }

    private boolean contaParaReceitaClinica(Agendamento agendamento) {
        if (agendamento.getProfissional() == null) {
            return true;
        }
        return !Boolean.TRUE.equals(agendamento.getProfissional().getDonaClinica());
    }

    private ReceitaPixLinhaView paraLinha(Agendamento agendamento) {
        BigDecimal valorTaxa = infinitePayService.resolverValorTaxaClinica(agendamento);
        return new ReceitaPixLinhaView(agendamento, valorTaxa);
    }

    private String normalizarChave(String nome) {
        if (nome == null) {
            return "";
        }
        return nome.trim().toLowerCase(Locale.ROOT);
    }

    private String formatarMes(YearMonth mes) {
        String rotulo = mes.format(MES_ANO_LABEL);
        if (rotulo == null || rotulo.isBlank()) {
            return "—";
        }
        return rotulo.substring(0, 1).toUpperCase(Locale.ROOT) + rotulo.substring(1);
    }

    private static final class ResumoMensalProfissional {
        private BigDecimal valor = BigDecimal.ZERO;
        private int quantidade;
    }
}
