package com.clinica.sistema.service;

import com.clinica.sistema.dto.ContagemGraficoView;
import com.clinica.sistema.dto.GraficoJsonUtil;
import com.clinica.sistema.dto.ReceitaPixLinhaView;
import com.clinica.sistema.dto.RelatorioLinhaView;
import com.clinica.sistema.dto.RelatorioProfissionalAtendimentoView;
import com.clinica.sistema.dto.RelatorioProfissionalMesView;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RelatorioProfissionalService {

    public static final List<String> SALAS_FILTRO = List.of(
            "Sala 1",
            "Sala 2",
            "Sala 3",
            "Sala 4"
    );

    private static final DateTimeFormatter MES_ANO_LABEL =
            DateTimeFormatter.ofPattern("MMMM 'de' yyyy", new Locale("pt", "BR"));

    private final AgendamentoService agendamentoService;
    private final AgendamentoRepository agendamentoRepository;
    private final InfinitePayService infinitePayService;

    public RelatorioProfissionalService(
            AgendamentoService agendamentoService,
            AgendamentoRepository agendamentoRepository,
            InfinitePayService infinitePayService
    ) {
        this.agendamentoService = agendamentoService;
        this.agendamentoRepository = agendamentoRepository;
        this.infinitePayService = infinitePayService;
    }

    public RelatorioProfissionalMesView montarRelatorio(Usuario profissional, YearMonth mesSelecionado) {
        return montarRelatorio(profissional, mesSelecionado, null, true, false);
    }

    public RelatorioProfissionalMesView montarRelatorio(
            Usuario profissional,
            YearMonth mesSelecionado,
            String salaFiltro
    ) {
        return montarRelatorio(profissional, mesSelecionado, salaFiltro, true, false);
    }

    public RelatorioProfissionalMesView montarRelatorio(
            Usuario profissional,
            YearMonth mesSelecionado,
            String salaFiltro,
            boolean exibirTaxas
    ) {
        return montarRelatorio(profissional, mesSelecionado, salaFiltro, exibirTaxas, false);
    }

    public RelatorioProfissionalMesView montarRelatorio(
            Usuario profissional,
            YearMonth mesSelecionado,
            String salaFiltro,
            boolean exibirTaxas,
            boolean exibirGanhosConsulta
    ) {
        if (profissional == null || profissional.getId() == null) {
            return RelatorioProfissionalMesView.vazio(
                    mesSelecionado,
                    "Profissional",
                    resolverSalaFiltro(salaFiltro),
                    exibirTaxas,
                    exibirGanhosConsulta
            );
        }

        String salaSelecionada = resolverSalaFiltro(salaFiltro);

        List<Agendamento> atendimentosMes = agendamentoService.listarAtendimentosProfissionalNoMes(
                profissional.getId(),
                mesSelecionado
        );
        if (salaSelecionada != null) {
            atendimentosMes = atendimentosMes.stream()
                    .filter(agendamento -> agendamento.getSala() != null
                            && salaSelecionada.equals(agendamento.getSala().getNome()))
                    .toList();
        }

        List<ReceitaPixLinhaView> pagamentosMes = List.of();
        BigDecimal totalTaxas = BigDecimal.ZERO;
        ResumoHistorico melhorMes = new ResumoHistorico("—");

        if (exibirTaxas) {
            LocalDateTime inicioPagamento = mesSelecionado.atDay(1).atStartOfDay();
            LocalDateTime fimPagamento = mesSelecionado.plusMonths(1).atDay(1).atStartOfDay();
            pagamentosMes = agendamentoRepository
                    .findPagosPorProfissionalEDataPagamentoNoPeriodo(profissional.getId(), inicioPagamento, fimPagamento)
                    .stream()
                    .map(agendamento -> new ReceitaPixLinhaView(
                            agendamento,
                            infinitePayService.resolverValorTaxaClinica(agendamento)
                    ))
                    .filter(linha -> salaSelecionada == null || salaSelecionada.equals(linha.getSalaNome()))
                    .toList();

            totalTaxas = pagamentosMes.stream()
                    .map(ReceitaPixLinhaView::getValorTaxa)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            melhorMes = calcularMelhorMes(profissional.getId());
        }

        List<RelatorioProfissionalAtendimentoView> linhasAtendimentos = atendimentosMes.stream()
                .map(agendamento -> new RelatorioProfissionalAtendimentoView(
                        agendamento,
                        exibirTaxas ? rotularStatusPagamento(agendamento) : null,
                        exibirTaxas && agendamento.isPagamentoPago()
                                ? infinitePayService.resolverValorTaxaClinica(agendamento)
                                : BigDecimal.ZERO
                ))
                .toList();

        RelatorioLinhaView usoSalas = montarUsoSalas(profissional.getNome(), atendimentosMes);
        List<ContagemGraficoView> usoTipos = montarUsoTipos(atendimentosMes);

        BigDecimal totalGanhosMes = BigDecimal.ZERO;
        int consultasComValorGanhos = 0;
        if (exibirGanhosConsulta) {
            for (Agendamento agendamento : atendimentosMes) {
                BigDecimal valorLiquido = resolverValorLiquidoConsulta(agendamento);
                if (valorLiquido.signum() > 0) {
                    totalGanhosMes = totalGanhosMes.add(valorLiquido);
                    consultasComValorGanhos++;
                }
            }
        }

        return new RelatorioProfissionalMesView(
                mesSelecionado,
                profissional.getNome(),
                atendimentosMes.size(),
                pagamentosMes.size(),
                totalTaxas,
                melhorMes.label(),
                melhorMes.atendimentos(),
                melhorMes.valor(),
                linhasAtendimentos,
                pagamentosMes,
                GraficoJsonUtil.serializarUsoSalasRelatorio(List.of(usoSalas)),
                GraficoJsonUtil.serializarContagensPorRotulo(usoTipos),
                GraficoJsonUtil.serializarPagamentosPix(pagamentosMes),
                salaSelecionada,
                exibirTaxas,
                exibirGanhosConsulta,
                totalGanhosMes,
                consultasComValorGanhos
        );
    }

    public String resolverSalaFiltro(String salaFiltro) {
        if (salaFiltro == null || salaFiltro.isBlank()) {
            return null;
        }
        String normalizado = salaFiltro.trim();
        if ("TODAS".equalsIgnoreCase(normalizado)) {
            return null;
        }
        for (String sala : SALAS_FILTRO) {
            if (sala.equalsIgnoreCase(normalizado)) {
                return sala;
            }
        }
        return null;
    }

    public String rotuloSalaFiltro(String salaFiltro) {
        String sala = resolverSalaFiltro(salaFiltro);
        return sala != null ? sala : "Todas as salas";
    }

    /** Valor que o profissional fica (recebe do cliente menos taxa da clinica/sala). */
    BigDecimal resolverValorLiquidoConsulta(Agendamento agendamento) {
        if (agendamento == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal liquido = agendamento.getValorLiquidoProfissional();
        if (liquido != null) {
            return liquido.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal recebe = agendamento.getValorProfissionalRecebe();
        if (recebe == null || recebe.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal taxaClinica = agendamento.getValorClinicaCobra() != null
                ? agendamento.getValorClinicaCobra()
                : BigDecimal.ZERO;
        return recebe.subtract(taxaClinica).setScale(2, RoundingMode.HALF_UP);
    }

    private ResumoHistorico calcularMelhorMes(Long profissionalId) {
        Map<YearMonth, ResumoHistorico> resumos = new HashMap<>();
        for (Agendamento agendamento : agendamentoRepository.findPagosPorProfissionalComDataPagamento(profissionalId)) {
            if (agendamento.getDataPagamento() == null) {
                continue;
            }
            YearMonth mes = YearMonth.from(agendamento.getDataPagamento());
            ResumoHistorico resumo = resumos.computeIfAbsent(mes, ignored -> new ResumoHistorico(formatarMes(mes)));
            resumo.atendimentos++;
            resumo.valor = resumo.valor.add(infinitePayService.resolverValorTaxaClinica(agendamento));
        }

        return resumos.values().stream()
                .max(Comparator
                        .comparing((ResumoHistorico resumo) -> resumo.atendimentos)
                        .thenComparing(resumo -> resumo.valor))
                .orElse(new ResumoHistorico("—"));
    }

    private RelatorioLinhaView montarUsoSalas(String nomeProfissional, List<Agendamento> atendimentos) {
        Map<String, Long> porSala = new HashMap<>();
        for (Agendamento agendamento : atendimentos) {
            if (agendamento.getSala() == null || agendamento.getSala().getNome() == null) {
                continue;
            }
            String sala = agendamento.getSala().getNome();
            porSala.merge(sala, 1L, Long::sum);
        }

        RelatorioLinhaView linha = new RelatorioLinhaView();
        linha.setProfissionalNome(nomeProfissional);
        linha.setSala1(porSala.getOrDefault("Sala 1", 0L));
        linha.setSala2(porSala.getOrDefault("Sala 2", 0L));
        linha.setSala3(porSala.getOrDefault("Sala 3", 0L));
        linha.setSala4(porSala.getOrDefault("Sala 4", 0L));
        linha.setTotalHorarios(atendimentos.size());
        return linha;
    }

    private List<ContagemGraficoView> montarUsoTipos(List<Agendamento> atendimentos) {
        Map<String, Long> porTipo = new HashMap<>();
        for (Agendamento agendamento : atendimentos) {
            String tipo = rotularTipo(agendamento);
            porTipo.merge(tipo, 1L, Long::sum);
        }

        List<ContagemGraficoView> itens = new ArrayList<>();
        porTipo.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> itens.add(new ContagemGraficoView(entry.getKey(), entry.getValue())));
        return itens;
    }

    private String rotularTipo(Agendamento agendamento) {
        if (agendamento.isQuinzenal()) {
            return "Quinzenal";
        }
        if (agendamento.isMensal()) {
            return "Mensal";
        }
        if (agendamento.isFixoSemanal()) {
            return "Fixo semanal";
        }
        return "Avulso";
    }

    private String rotularStatusPagamento(Agendamento agendamento) {
        PagamentoStatus status = agendamento.getStatusPagamento();
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case PAGO -> "Pago";
            case AGUARDANDO_PAGAMENTO -> "Aguardando pagamento";
            case ESPERANDO_CONFIRMACAO -> "Esperando confirmacao";
            case AGUARDANDO_APROVACAO_INDICACAO -> "Aguardando aprovacao indicacao";
            case AGUARDANDO_CONFIRMACAO_DINHEIRO -> "Aguardando PIX";
            case PAGAMENTO_FUTURO -> "Pagamento futuro";
            case LIBERADO_FALTA_PAGAMENTO -> "Liberado (falta pagamento)";
        };
    }

    private String formatarMes(YearMonth mes) {
        String rotulo = mes.format(MES_ANO_LABEL);
        if (rotulo == null || rotulo.isBlank()) {
            return "—";
        }
        return rotulo.substring(0, 1).toUpperCase(Locale.ROOT) + rotulo.substring(1);
    }

    private static final class ResumoHistorico {
        private final String label;
        private int atendimentos;
        private BigDecimal valor = BigDecimal.ZERO;

        private ResumoHistorico(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        private int atendimentos() {
            return atendimentos;
        }

        private BigDecimal valor() {
            return valor;
        }
    }
}
