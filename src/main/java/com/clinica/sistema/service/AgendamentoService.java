package com.clinica.sistema.service;

import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.RelocacaoAgendamentoForm;
import com.clinica.sistema.dto.AgendaSalaLinha;
import com.clinica.sistema.dto.AgendaSalaView;
import com.clinica.sistema.dto.ConfiguracaoTaxasAtendimentosView;
import com.clinica.sistema.dto.ProfissionalAgendamentosResumo;
import com.clinica.sistema.dto.SerieAgendamentoLinha;
import com.clinica.sistema.dto.SerieAgendamentoOcorrencia;
import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.clinica.sistema.dto.RelatorioUsoSalaItem;
import com.clinica.sistema.dto.RelatorioUsoSalaProfissional;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.EncerramentoSerieRegistro;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.EncerramentoSerieRegistroRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class AgendamentoService {
    private static final LocalTime HORA_ABERTURA = LocalTime.of(7, 0);
    private static final LocalTime HORA_FECHAMENTO = LocalTime.of(22, 0);
    private static final int SEMANAS_FIXAS_PADRAO = 12;
    private static final int OCORRENCIAS_QUINZENAIS_PADRAO = 6;
    private static final String RECORRENCIA_AVULSO = "AVULSO";
    private static final String RECORRENCIA_SEMANAL = "SEMANAL";
    private static final String RECORRENCIA_QUINZENAL = "QUINZENAL";
    /** Evita carregar anos de fixos antigos nas listas do dashboard. */
    private static final int MESES_HISTORICO_AGENDA = 4;
    private static final Duration INTERVALO_RENOVACAO_SERIES = Duration.ofMinutes(10);

    private final AgendamentoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final SalaRepository salaRepository;
    private final AuthService authService;
    private final ValorConsultaService valorConsultaService;
    private final PagamentoConsultaService pagamentoConsultaService;
    private final EncerramentoSerieRegistroRepository encerramentoSerieRegistroRepository;

    public AgendamentoService(
            AgendamentoRepository repository,
            UsuarioRepository usuarioRepository,
            SalaRepository salaRepository,
            AuthService authService,
            ValorConsultaService valorConsultaService,
            PagamentoConsultaService pagamentoConsultaService,
            EncerramentoSerieRegistroRepository encerramentoSerieRegistroRepository
    ) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.salaRepository = salaRepository;
        this.authService = authService;
        this.valorConsultaService = valorConsultaService;
        this.pagamentoConsultaService = pagamentoConsultaService;
        this.encerramentoSerieRegistroRepository = encerramentoSerieRegistroRepository;
    }

    private volatile Instant ultimaRenovacaoSeries = Instant.EPOCH;

    public List<Agendamento> buscarParaUsuario(Usuario usuarioLogado) {
        return buscarPorProfissional(usuarioLogado.getId());
    }

    public Optional<Agendamento> buscarPorId(Long id) {
        return repository.findById(id);
    }

    public List<Agendamento> buscarPorProfissional(Long profissionalId) {
        LocalDateTime desde = LocalDateTime.now().minusMonths(MESES_HISTORICO_AGENDA);
        return repository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualOrderByDataHoraInicioAsc(
                profissionalId,
                desde
        );
    }

    public ProfissionalAgendamentosResumo montarResumoAgendamentos(Usuario profissional) {
        return montarResumoAgendamentos(profissional, profissional);
    }

    public ProfissionalAgendamentosResumo montarResumoAgendamentos(Usuario profissional, Usuario usuarioLogado) {
        List<Agendamento> agendamentos = buscarPorProfissional(profissional.getId());
        List<Agendamento> avulsos = listarProximosPorSerie(agendamentos, Agendamento::isAvulso);
        List<SerieAgendamentoLinha> seriesFixas = agruparSeriesAtivas(
                agendamentos,
                Agendamento::isFixoSemanal,
                usuarioLogado
        );
        List<SerieAgendamentoLinha> seriesQuinzenais = agruparSeriesAtivas(
                agendamentos,
                Agendamento::isQuinzenal,
                usuarioLogado
        );
        return new ProfissionalAgendamentosResumo(
                profissional.getId(),
                profissional.getNome(),
                avulsos,
                seriesFixas,
                seriesQuinzenais,
                contarSeries(agendamentos, Agendamento::isAvulso),
                seriesFixas.size(),
                seriesQuinzenais.size()
        );
    }

    public List<ProfissionalAgendamentosResumo> montarResumosProfissionais(List<Usuario> profissionais) {
        return montarResumosProfissionais(profissionais, null);
    }

    public List<ProfissionalAgendamentosResumo> montarResumosProfissionais(
            List<Usuario> profissionais,
            Usuario usuarioLogado
    ) {
        return profissionais.stream()
                .map(profissional -> montarResumoAgendamentos(profissional, usuarioLogado))
                .toList();
    }

    public List<Agendamento> listarProximosPorSerie(List<Agendamento> agendamentos, Predicate<Agendamento> filtro) {
        LocalDateTime limite = LocalDateTime.now();
        Map<String, Agendamento> proximoPorSerie = new LinkedHashMap<>();

        agendamentos.stream()
                .filter(filtro)
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !agendamento.getDataHoraInicio().isBefore(limite))
                .sorted(Comparator.comparing(Agendamento::getDataHoraInicio))
                .forEach(agendamento -> proximoPorSerie.putIfAbsent(chaveSerie(agendamento), agendamento));

        return proximoPorSerie.values().stream()
                .sorted(Comparator.comparing(Agendamento::getDataHoraInicio))
                .limit(24)
                .toList();
    }

    public long contarSeries(List<Agendamento> agendamentos, Predicate<Agendamento> filtro) {
        return listarProximosPorSerie(agendamentos, filtro).size();
    }

    public List<SerieAgendamentoLinha> agruparSeriesAtivas(List<Agendamento> agendamentos, Predicate<Agendamento> filtro) {
        return agruparSeriesAtivas(agendamentos, filtro, null);
    }

    public List<SerieAgendamentoLinha> agruparSeriesAtivas(
            List<Agendamento> agendamentos,
            Predicate<Agendamento> filtro,
            Usuario usuarioLogado
    ) {
        LocalDateTime limite = LocalDateTime.now();
        DateTimeFormatter formatoData = DateTimeFormatter.ofPattern("dd/MM");
        return listarProximosPorSerie(agendamentos, filtro).stream()
                .map(representante -> montarSerieAgendamentoLinha(
                        representante,
                        agendamentos,
                        limite,
                        formatoData,
                        usuarioLogado
                ))
                .toList();
    }

    private SerieAgendamentoLinha montarSerieAgendamentoLinha(
            Agendamento representante,
            List<Agendamento> agendamentos,
            LocalDateTime limite,
            DateTimeFormatter formatoData,
            Usuario usuarioLogado
    ) {
        String chaveSerie = chaveSerie(representante);
        List<SerieAgendamentoOcorrencia> proximasOcorrencias = agendamentos.stream()
                .filter(agendamento -> chaveSerie(agendamento).equals(chaveSerie))
                .filter(agendamento -> agendamento.getId() != null)
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !agendamento.getDataHoraInicio().isBefore(limite))
                .sorted(Comparator.comparing(Agendamento::getDataHoraInicio))
                .map(agendamento -> new SerieAgendamentoOcorrencia(
                        agendamento.getId(),
                        agendamento.getDataHoraInicio().format(formatoData),
                        agendamento.getStatusPagamento(),
                        pagamentoConsultaService.exibirBotaoPagar(agendamento),
                        agendamento.isPagamentoPago(),
                        podeRealocar(agendamento, usuarioLogado)
                ))
                .limit(obterLimiteOcorrenciasFuturas(recorrenciaDoAgendamento(representante)))
                .toList();

        String salaNome = representante.getSala() != null && representante.getSala().getNome() != null
                ? representante.getSala().getNome()
                : "-";
        String diaSemanaRotulo = formatarHorarioDiaSemana(representante.getDataHoraInicio());
        return new SerieAgendamentoLinha(
                representante.getNomeCliente(),
                salaNome,
                representante.getId(),
                recorrenciaDoAgendamento(representante),
                diaSemanaRotulo,
                proximasOcorrencias != null ? proximasOcorrencias : List.of(),
                representante.getValoresConsultaResumo()
        );
    }

    private String formatarHorarioDiaSemana(LocalDateTime dataHora) {
        if (dataHora == null) {
            return null;
        }
        String horario = dataHora.format(DateTimeFormatter.ofPattern("HH:mm"));
        String diaSemana = formatarDiaSemana(dataHora);
        if (diaSemana == null || diaSemana.isBlank()) {
            return horario;
        }
        return horario + " " + diaSemana;
    }

    private String formatarDiaSemana(LocalDateTime dataHora) {
        if (dataHora == null) {
            return null;
        }
        String dia = dataHora.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
        if (dia.isBlank()) {
            return dia;
        }
        return Character.toUpperCase(dia.charAt(0)) + dia.substring(1);
    }

    public List<Agendamento> listarProximasOcorrencias(List<Agendamento> agendamentos, Predicate<Agendamento> filtro) {
        LocalDateTime limite = LocalDateTime.now();
        return agendamentos.stream()
                .filter(filtro)
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !agendamento.getDataHoraInicio().isBefore(limite))
                .sorted(Comparator.comparing(Agendamento::getDataHoraInicio))
                .limit(48)
                .toList();
    }

    public long contarOcorrencias(List<Agendamento> agendamentos, Predicate<Agendamento> filtro) {
        LocalDateTime limite = LocalDateTime.now();
        return agendamentos.stream()
                .filter(filtro)
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !agendamento.getDataHoraInicio().isBefore(limite))
                .count();
    }

    public List<Sala> listarSalas() {
        return salaRepository.findAllByOrderByNomeAsc();
    }

    public List<Usuario> listarProfissionais() {
        return usuarioRepository.findAll().stream()
                .filter(this::podeAtender)
                .sorted(Comparator.comparing(Usuario::getNome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<Usuario> listarProfissionaisComAgendamentoNoMes(YearMonth mesReferencia) {
        LocalDateTime inicio = mesReferencia.atDay(1).atStartOfDay();
        LocalDateTime fim = mesReferencia.plusMonths(1).atDay(1).atStartOfDay();
        return repository.findProfissionaisComAgendamentoNoPeriodo(inicio, fim).stream()
                .filter(profissional -> !authService.profissionalIgnoraValoresEPagamento(profissional))
                .toList();
    }

    public List<Agendamento> listarAtendimentosProfissionalNoMes(Long profissionalId, YearMonth mesReferencia) {
        if (profissionalId == null) {
            return List.of();
        }
        LocalDateTime inicio = mesReferencia.atDay(1).atStartOfDay();
        LocalDateTime fim = mesReferencia.plusMonths(1).atDay(1).atStartOfDay();
        return repository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                profissionalId,
                inicio,
                fim
        );
    }

    public ConfiguracaoTaxasAtendimentosView montarAtendimentosConfiguracaoTaxas(
            Long profissionalId,
            YearMonth mesReferencia
    ) {
        if (profissionalId == null) {
            return ConfiguracaoTaxasAtendimentosView.vazio();
        }

        List<Agendamento> todos = repository.findByProfissionalIdOrderByDataHoraInicioAsc(profissionalId);
        LocalDateTime inicioMes = mesReferencia.atDay(1).atStartOfDay();
        LocalDateTime fimMes = mesReferencia.plusMonths(1).atDay(1).atStartOfDay();

        List<Agendamento> avulsos = todos.stream()
                .filter(Agendamento::isAvulso)
                .filter(agendamento -> estaNoPeriodo(agendamento, inicioMes, fimMes))
                .sorted(Comparator.comparing(Agendamento::getDataHoraInicio))
                .toList();

        List<SerieAgendamentoLinha> seriesFixas = agruparSeriesAtivas(todos, Agendamento::isFixoSemanal).stream()
                .filter(serie -> serieTemOcorrenciaNoMes(serie.getAgendamentoReferenciaId(), todos, inicioMes, fimMes))
                .toList();

        List<SerieAgendamentoLinha> seriesQuinzenais = agruparSeriesAtivas(todos, Agendamento::isQuinzenal).stream()
                .filter(serie -> serieTemOcorrenciaNoMes(serie.getAgendamentoReferenciaId(), todos, inicioMes, fimMes))
                .toList();

        int totalNoMes = listarAtendimentosProfissionalNoMes(profissionalId, mesReferencia).size();
        return new ConfiguracaoTaxasAtendimentosView(avulsos, seriesFixas, seriesQuinzenais, totalNoMes);
    }

    private boolean estaNoPeriodo(Agendamento agendamento, LocalDateTime inicio, LocalDateTime fim) {
        if (agendamento.getDataHoraInicio() == null) {
            return false;
        }
        return !agendamento.getDataHoraInicio().isBefore(inicio)
                && agendamento.getDataHoraInicio().isBefore(fim);
    }

    private boolean serieTemOcorrenciaNoMes(
            Long agendamentoReferenciaId,
            List<Agendamento> agendamentos,
            LocalDateTime inicioMes,
            LocalDateTime fimMes
    ) {
        Agendamento referencia = agendamentos.stream()
                .filter(agendamento -> agendamentoReferenciaId.equals(agendamento.getId()))
                .findFirst()
                .orElse(null);
        if (referencia == null) {
            return false;
        }
        String chaveSerie = chaveSerie(referencia);
        return agendamentos.stream()
                .filter(agendamento -> chaveSerie(agendamento).equals(chaveSerie))
                .anyMatch(agendamento -> estaNoPeriodo(agendamento, inicioMes, fimMes));
    }

    public List<LocalTime> listarHorariosDisponiveis() {
        List<LocalTime> horarios = new ArrayList<>();
        for (LocalTime horario = HORA_ABERTURA; !horario.equals(HORA_FECHAMENTO); horario = horario.plusHours(1)) {
            horarios.add(horario);
        }
        return horarios;
    }

    public AgendaSalaView montarAgendaSala(Long salaId, LocalDate referencia) {
        Sala sala = buscarSalaPadrao(salaId);
        LocalDate inicioSemana = obterInicioSemana(referencia);
        LocalDate fimSemana = inicioSemana.plusDays(5);

        LocalDateTime inicioConsulta = inicioSemana.atTime(HORA_ABERTURA);
        LocalDateTime fimConsulta = fimSemana.plusDays(1).atStartOfDay();

        List<Agendamento> agendamentosSemana =
                repository.findBySalaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                        sala.getId(),
                        inicioConsulta,
                        fimConsulta
                );

        List<LocalDate> diasSemana = inicioSemana.datesUntil(fimSemana.plusDays(1)).toList();
        List<AgendaSalaLinha> linhas = new ArrayList<>();

        for (LocalTime horario = HORA_ABERTURA; horario.isBefore(HORA_FECHAMENTO); horario = horario.plusHours(1)) {
            List<Agendamento> porDia = new ArrayList<>();
            for (LocalDate dia : diasSemana) {
                porDia.add(buscarAgendamentoNaCelula(agendamentosSemana, dia, horario));
            }
            linhas.add(new AgendaSalaLinha(horario, porDia));
        }

        AgendaSalaView view = new AgendaSalaView();
        view.setSala(sala);
        view.setInicioSemana(inicioSemana);
        view.setDiasSemana(diasSemana);
        view.setLinhas(linhas);
        return view;
    }

    @Transactional
    public long limparAgendamentosDoMesPassado() {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);
        LocalDateTime inicio = mesPassado.atDay(1).atStartOfDay();
        LocalDateTime fim = YearMonth.now().atDay(1).atStartOfDay();
        return limparAgendamentosNoPeriodo(inicio, fim);
    }

    public long contarAgendamentosDoMesPassado() {
        return contarAgendamentosNoMes(YearMonth.now().minusMonths(1));
    }

    public long contarAgendamentosNoMes(YearMonth mesReferencia) {
        LocalDateTime inicio = mesReferencia.atDay(1).atStartOfDay();
        LocalDateTime fim = mesReferencia.plusMonths(1).atDay(1).atStartOfDay();
        return repository.countByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThan(inicio, fim);
    }

    /**
     * Remove somente agendamentos avulsos do periodo.
     * Semanal e quinzenal permanecem ate cancelamento ou encerramento da serie;
     * series ativas sao estendidas automaticamente ({@link #renovarSeriesRecorrentesAtivas}).
     */
    @Transactional
    public long limparAgendamentosNoPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        if (!inicio.isBefore(fim)) {
            throw new RuntimeException("Período inválido para limpeza.");
        }
        return repository.deleteAvulsosByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThan(inicio, fim);
    }

    public static final String PREFIXO_CLIENTE_TESTE_RELATORIO_SEMANAL = "TESTE-REL-SEMANAL-";

    /**
     * Cria avulsos na semana atual (segunda ate hoje/sabado) para testar relatorio semanal e regra 24h.
     */
    @Transactional
    public int semearAvulsosSemanaAtualParaTesteRelatorio() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio;
        LocalDate fim;
        if (hoje.getDayOfWeek() == DayOfWeek.SUNDAY) {
            inicio = hoje.minusDays(6);
            fim = inicio.plusDays(5);
        } else {
            inicio = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            fim = hoje;
        }

        repository.deleteByNomeClienteLike(PREFIXO_CLIENTE_TESTE_RELATORIO_SEMANAL + "%");

        List<Usuario> profissionais = usuarioRepository.findAll().stream()
                .filter(usuario -> "ROLE_PROFISSIONAL".equals(usuario.getCargo()))
                .sorted(Comparator.comparing(Usuario::getNome))
                .toList();
        List<Sala> salas = salaRepository.findAllByOrderByNomeAsc();
        if (profissionais.isEmpty() || salas.isEmpty()) {
            return 0;
        }

        LocalDateTime limite24h = LocalDateTime.now().minusHours(24);
        List<Agendamento> criados = new ArrayList<>();
        int indice = 0;

        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            for (int hora : List.of(8, 10, 14)) {
                LocalDateTime inicioSlot = dia.atTime(hora, 0);
                if (inicioSlot.isAfter(limite24h)) {
                    continue;
                }
                Usuario profissional = profissionais.get(indice % profissionais.size());
                Sala sala = salas.get(indice % salas.size());
                Agendamento agendamento = new Agendamento();
                agendamento.setProfissional(profissional);
                agendamento.setSala(sala);
                agendamento.setNomeCliente(
                        PREFIXO_CLIENTE_TESTE_RELATORIO_SEMANAL
                                + dia.format(DateTimeFormatter.ofPattern("dd/MM"))
                                + "-"
                                + hora
                                + "h"
                );
                agendamento.setDataHoraInicio(inicioSlot);
                agendamento.setDataHoraFim(inicioSlot.plusHours(1));
                agendamento.setFixo(false);
                agendamento.setTipoRecorrencia(RECORRENCIA_AVULSO);
                criados.add(agendamento);
                indice++;
            }
        }

        LocalDateTime consultaRecente = LocalDateTime.now().minusHours(2);
        if (!consultaRecente.toLocalDate().isBefore(inicio) && !consultaRecente.toLocalDate().isAfter(fim)) {
            Usuario profissional = profissionais.get(0);
            Sala sala = salas.get(0);
            Agendamento recente = new Agendamento();
            recente.setProfissional(profissional);
            recente.setSala(sala);
            recente.setNomeCliente(PREFIXO_CLIENTE_TESTE_RELATORIO_SEMANAL + "recente-menos-24h");
            recente.setDataHoraInicio(consultaRecente);
            recente.setDataHoraFim(consultaRecente.plusHours(1));
            recente.setFixo(false);
            recente.setTipoRecorrencia(RECORRENCIA_AVULSO);
            criados.add(recente);
        }

        if (!criados.isEmpty()) {
            repository.saveAll(criados);
        }
        return criados.size();
    }

    public RelatorioMensalUsoSalasView montarRelatorioMensalUsoSalas(YearMonth mesReferencia) {
        LocalDate inicio = mesReferencia.atDay(1);
        LocalDate fim = mesReferencia.atEndOfMonth();
        RelatorioMensalUsoSalasView relatorio = montarRelatorioUsoSalasNoPeriodo(
                inicio,
                fim,
                formatarMesReferencia(mesReferencia)
        );
        relatorio.setAnoReferencia(mesReferencia.getYear());
        relatorio.setMesReferencia(mesReferencia.getMonthValue());
        return relatorio;
    }

    public RelatorioMensalUsoSalasView montarRelatorioUsoSalasNoPeriodo(
            LocalDate inicio,
            LocalDate fim,
            String periodoLabel
    ) {
        return montarRelatorioUsoSalasNoPeriodo(inicio, fim, periodoLabel, false);
    }

    public RelatorioMensalUsoSalasView montarRelatorioUsoSalasNoPeriodoAposRegra24h(
            LocalDate inicio,
            LocalDate fim,
            String periodoLabel
    ) {
        return montarRelatorioUsoSalasNoPeriodo(inicio, fim, periodoLabel, true);
    }

    private RelatorioMensalUsoSalasView montarRelatorioUsoSalasNoPeriodo(
            LocalDate inicio,
            LocalDate fim,
            String periodoLabel,
            boolean aplicarRegra24Horas
    ) {
        if (fim.isBefore(inicio)) {
            throw new RuntimeException("Período inválido para o relatório.");
        }
        LocalDateTime inicioDataHora = inicio.atStartOfDay();
        LocalDateTime fimDataHora = fim.plusDays(1).atStartOfDay();

        List<Object[]> linhas;
        if (aplicarRegra24Horas) {
            LocalDateTime corte = LocalDateTime.now().minusHours(24);
            linhas = repository.contarUsoSalasPorProfissionalNoPeriodoAposRegra24h(
                    inicioDataHora,
                    fimDataHora,
                    corte
            );
        } else {
            linhas = repository.contarUsoSalasPorProfissionalNoPeriodo(inicioDataHora, fimDataHora);
        }
        Map<String, RelatorioUsoSalaProfissional> porProfissional = new LinkedHashMap<>();
        long totalGeral = 0;

        for (Object[] linha : linhas) {
            String profissionalNome = (String) linha[0];
            String salaNome = (String) linha[1];
            long quantidade = linha[2] instanceof Number numero ? numero.longValue() : 0L;

            RelatorioUsoSalaProfissional bloco = porProfissional.computeIfAbsent(
                    profissionalNome,
                    nome -> {
                        RelatorioUsoSalaProfissional novo = new RelatorioUsoSalaProfissional();
                        novo.setProfissionalNome(nome);
                        return novo;
                    }
            );

            RelatorioUsoSalaItem item = new RelatorioUsoSalaItem();
            item.setSalaNome(salaNome);
            item.setQuantidade(quantidade);
            bloco.getSalas().add(item);
            bloco.setTotalHorarios(bloco.getTotalHorarios() + quantidade);
            totalGeral += quantidade;
        }

        RelatorioMensalUsoSalasView relatorio = new RelatorioMensalUsoSalasView();
        relatorio.setAnoReferencia(inicio.getYear());
        relatorio.setMesReferencia(inicio.getMonthValue());
        relatorio.setMesReferenciaLabel(periodoLabel);
        relatorio.setProfissionais(new ArrayList<>(porProfissional.values()));
        relatorio.setTotalGeral(totalGeral);
        return relatorio;
    }

    public List<Agendamento> listarAgendamentosDoDia(Usuario usuarioLogado, boolean isAdmin) {
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicio = hoje.atStartOfDay();
        LocalDateTime fim = hoje.plusDays(1).atStartOfDay();

        if (isAdmin) {
            return repository.findByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                    inicio,
                    fim
            );
        }

        return repository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                usuarioLogado.getId(),
                inicio,
                fim
        );
    }

    @Transactional
    public Agendamento salvar(AgendamentoForm form, Usuario usuarioLogado) {
        validarFormulario(form);
        if (!authService.isAdmin(usuarioLogado)
                && !authService.isDonaClinica(usuarioLogado)
                && pagamentoConsultaService.profissionalBloqueadoPorPendenciaPagamento(usuarioLogado)) {
            throw new RuntimeException(pagamentoConsultaService.mensagemBloqueioPagamento(usuarioLogado));
        }

        Usuario profissional = carregarProfissional(form.getProfissionalId(), usuarioLogado);
        Sala sala = salaRepository.findById(form.getSalaId())
                .orElseThrow(() -> new RuntimeException("Sala não encontrada."));

        LocalDateTime inicio = LocalDateTime.of(
                form.getDataAtendimento(),
                form.getHorarioAtendimento().withMinute(0).withSecond(0).withNano(0)
        );
        LocalDateTime fim = inicio.plusHours(1);

        validarHorario(inicio, fim);

        List<Agendamento> novosAgendamentos = new ArrayList<>();
        String recorrencia = normalizarRecorrencia(form);
        int saltoSemanas = obterSaltoSemanas(recorrencia);
        int repeticoes = obterQuantidadeRepeticoes(recorrencia);
        String serieFixaId = RECORRENCIA_AVULSO.equals(recorrencia)
                ? null
                : recorrencia.toLowerCase() + "-" + UUID.randomUUID();

        for (int semana = 0; semana < repeticoes; semana++) {
            LocalDateTime inicioSemana = inicio.plusWeeks((long) semana * saltoSemanas);
            LocalDateTime fimSemana = fim.plusWeeks((long) semana * saltoSemanas);

            validarConflitos(
                    sala,
                    profissional,
                    usuarioLogado,
                    inicioSemana,
                    fimSemana,
                    !RECORRENCIA_AVULSO.equals(recorrencia),
                    semana
            );

            Agendamento novo = new Agendamento();
            novo.setProfissional(profissional);
            novo.setSala(sala);
            novo.setNomeCliente(form.getNomeCliente().trim());
            novo.setDataHoraInicio(inicioSemana);
            novo.setDataHoraFim(fimSemana);
            novo.setFixo(!RECORRENCIA_AVULSO.equals(recorrencia));
            novo.setSerieFixaId(serieFixaId);
            novo.setTipoRecorrencia(recorrencia);
            novo.setRecorrencia(recorrencia);
            if (!authService.profissionalIgnoraValoresEPagamento(profissional)) {
                valorConsultaService.aplicarValores(novo, form, sala, recorrencia);
            }
            novosAgendamentos.add(novo);
        }

        repository.saveAll(novosAgendamentos);
        pagamentoConsultaService.configurarPagamentosAoSalvar(novosAgendamentos, profissional, usuarioLogado);
        repository.saveAll(novosAgendamentos);
        if (!RECORRENCIA_AVULSO.equals(recorrencia)) {
            renovarSeriesRecorrentesAtivas();
        }
        return novosAgendamentos.get(0);
    }

    public boolean isRealocacaoAvulsa(Agendamento agendamento) {
        return RECORRENCIA_AVULSO.equals(recorrenciaDoAgendamento(agendamento));
    }

    /**
     * Serie fixa semanal: proximas 12 datas no mesmo dia da semana da cadencia.
     * Serie quinzenal: proximas 6 datas a cada 2 semanas.
     * Avulso: lista vazia (qualquer data futura na tela).
     */
    public List<LocalDate> listarDatasPermitidasRealocacao(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return List.of();
        }
        if (isRealocacaoAvulsa(agendamento)) {
            return List.of();
        }

        int saltoSemanas = obterSaltoSemanas(recorrenciaDoAgendamento(agendamento));
        int limiteOcorrencias = obterLimiteOcorrenciasFuturas(recorrenciaDoAgendamento(agendamento));
        LocalDate anchor = obterDataAncoraSerie(agendamento);
        LocalTime horario = agendamento.getDataHoraInicio().toLocalTime();
        LocalDateTime agora = LocalDateTime.now();

        List<LocalDate> permitidas = new ArrayList<>();
        LocalDate candidata = primeiraDataPadraoDaSerieApos(LocalDate.now(), anchor, saltoSemanas);
        int guarda = 0;
        while (permitidas.size() < limiteOcorrencias && guarda++ < 52) {
            LocalDateTime inicioCandidato = LocalDateTime.of(candidata, horario);
            if (!inicioCandidato.isBefore(agora)) {
                permitidas.add(candidata);
            }
            candidata = candidata.plusWeeks(saltoSemanas);
        }
        return permitidas;
    }

    public boolean dataPermitidaParaRealocacao(Agendamento agendamento, LocalDate data) {
        if (agendamento == null || data == null) {
            return false;
        }
        if (isRealocacaoAvulsa(agendamento)) {
            return true;
        }
        return listarDatasPermitidasRealocacao(agendamento).contains(data);
    }

    public boolean podeRealocar(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null) {
            return false;
        }
        if (!statusPermiteRealocacao(agendamento)) {
            return false;
        }
        if (agendamento.getDataHoraInicio() == null) {
            return false;
        }
        if (!LocalDateTime.now().isBefore(agendamento.getDataHoraInicio())) {
            return false;
        }
        if (agendamento.getProfissional() != null
                && authService.profissionalIgnoraValoresEPagamento(agendamento.getProfissional())) {
            return false;
        }
        if (podeGerenciarAgendamentoDeOutros(usuarioLogado)) {
            return true;
        }
        return agendamento.getProfissional() != null
                && agendamento.getProfissional().getId().equals(usuarioLogado.getId());
    }

    private boolean statusPermiteRealocacao(Agendamento agendamento) {
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return true;
        }
        if (!PagamentoStatus.PAGAMENTO_FUTURO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        if (agendamento.getProfissional() == null) {
            return false;
        }
        PeriodicidadePagamento periodicidade = pagamentoConsultaService.resolverPeriodicidade(
                agendamento.getProfissional()
        );
        return periodicidade == PeriodicidadePagamento.SEMANAL
                || periodicidade == PeriodicidadePagamento.MENSAL;
    }

    @Transactional
    public Agendamento realocar(Long agendamentoId, RelocacaoAgendamentoForm form, Usuario usuarioLogado) {
        if (form == null || form.getSalaId() == null || form.getDataAtendimento() == null || form.getHorarioAtendimento() == null) {
            throw new RuntimeException("Informe sala, data e horário para realocar.");
        }

        Agendamento agendamento = repository.findById(agendamentoId)
                .orElseThrow(() -> new RuntimeException("Agendamento não encontrado."));
        if (!podeRealocar(agendamento, usuarioLogado)) {
            throw new RuntimeException(
                    "Realocação permitida somente antes do horário do atendimento, com pagamento confirmado "
                            + "ou cobrança semanal/mensal ainda pendente."
            );
        }

        Usuario profissional = agendamento.getProfissional();
        if (profissional == null) {
            throw new RuntimeException("Agendamento sem profissional vinculado.");
        }

        Sala novaSala = salaRepository.findById(form.getSalaId())
                .orElseThrow(() -> new RuntimeException("Sala não encontrada."));

        LocalDateTime novoInicio = LocalDateTime.of(
                form.getDataAtendimento(),
                form.getHorarioAtendimento().withMinute(0).withSecond(0).withNano(0)
        );
        LocalDateTime novoFim = novoInicio.plusHours(1);
        validarHorario(novoInicio, novoFim);

        if (!novoInicio.isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Não é possível realocar para data ou horário no passado.");
        }

        if (!dataPermitidaParaRealocacao(agendamento, form.getDataAtendimento())) {
            String recorrencia = recorrenciaDoAgendamento(agendamento);
            if (RECORRENCIA_QUINZENAL.equals(recorrencia)) {
                throw new RuntimeException(
                        "Para série quinzenal, escolha uma das próximas 6 datas da cadência (a cada 2 semanas, mesmo dia da semana)."
                );
            }
            throw new RuntimeException(
                    "Para série fixa semanal, escolha uma das próximas 12 datas da cadência (mesmo dia da semana)."
            );
        }

        validarConflitos(
                novaSala,
                profissional,
                usuarioLogado,
                novoInicio,
                novoFim,
                false,
                0,
                agendamento.getId()
        );

        preservarReferenciasCobrancaNaRealocacao(agendamento);

        agendamento.setSala(novaSala);
        agendamento.setDataHoraInicio(novoInicio);
        agendamento.setDataHoraFim(novoFim);
        return repository.save(agendamento);
    }

    private void preservarReferenciasCobrancaNaRealocacao(Agendamento agendamento) {
        if (agendamento.getDataHoraInicio() == null) {
            return;
        }
        LocalDate dataOriginal = agendamento.getDataHoraInicio().toLocalDate();
        if (agendamento.getDataReferenciaSemanaPagamento() == null) {
            agendamento.setDataReferenciaSemanaPagamento(dataOriginal);
        }
        if (agendamento.getDataReferenciaMesPagamento() == null) {
            agendamento.setDataReferenciaMesPagamento(dataOriginal.withDayOfMonth(1));
        }
    }

    /**
     * Mantem ocorrencias futuras por serie: 12 no fixo semanal, 6 no quinzenal.
     * Quando um dia passa ou e cancelado, a renovacao cria a proxima na sequencia.
     */
    /**
     * Renova series no maximo a cada {@link #INTERVALO_RENOVACAO_SERIES} (ex.: abrir o dashboard).
     * O {@link com.clinica.sistema.config.SerieRecorrenteScheduler} garante renovacao periodica em background.
     */
    public void renovarSeriesRecorrentesAtivasSeNecessario() {
        Instant agora = Instant.now();
        if (Duration.between(ultimaRenovacaoSeries, agora).compareTo(INTERVALO_RENOVACAO_SERIES) < 0) {
            return;
        }
        synchronized (this) {
            if (Duration.between(ultimaRenovacaoSeries, agora).compareTo(INTERVALO_RENOVACAO_SERIES) < 0) {
                return;
            }
            renovarSeriesRecorrentesAtivas();
            ultimaRenovacaoSeries = Instant.now();
        }
    }

    @Transactional
    public void renovarSeriesRecorrentesAtivas() {
        LocalDateTime agora = LocalDateTime.now();

        for (String serieFixaId : repository.findSerieFixaIdsComOcorrenciasFuturas(agora)) {
            estenderSerieAteHorizonte(serieFixaId);
        }
    }

    private void estenderSerieAteHorizonte(String serieFixaId) {
        if (repository.existsBySerieFixaIdAndSerieEncerradaEmIsNotNull(serieFixaId)) {
            return;
        }
        Agendamento ultimo = repository.findFirstBySerieFixaIdOrderByDataHoraInicioDesc(serieFixaId).orElse(null);
        if (ultimo == null || ultimo.getDataHoraInicio() == null || ultimo.getProfissional() == null || ultimo.getSala() == null) {
            return;
        }

        String recorrencia = recorrenciaDoAgendamento(ultimo);
        if (RECORRENCIA_AVULSO.equals(recorrencia)) {
            return;
        }

        int saltoSemanas = obterSaltoSemanas(recorrencia);
        int limiteOcorrencias = obterLimiteOcorrenciasFuturas(recorrencia);
        LocalDateTime limiteFuturo = LocalDateTime.now().minusDays(1);
        LocalDateTime fimReferencia = ultimo.getDataHoraFim() != null
                ? ultimo.getDataHoraFim()
                : ultimo.getDataHoraInicio().plusHours(1);
        long duracaoMinutos = java.time.Duration.between(ultimo.getDataHoraInicio(), fimReferencia).toMinutes();
        if (duracaoMinutos <= 0) {
            duracaoMinutos = 60;
        }

        List<Agendamento> novos = new ArrayList<>();
        int indiceSemana = 1;
        int guarda = 0;

        while (guarda++ < 52) {
            long futuras = repository.countBySerieFixaIdAndDataHoraInicioGreaterThanEqual(serieFixaId, limiteFuturo)
                    + novos.size();
            if (futuras >= limiteOcorrencias) {
                break;
            }

            ultimo = repository.findFirstBySerieFixaIdOrderByDataHoraInicioDesc(serieFixaId).orElse(ultimo);
            if (ultimo == null || ultimo.getDataHoraInicio() == null) {
                break;
            }

            LocalDateTime candidatoInicio = ultimo.getDataHoraInicio().plusWeeks(saltoSemanas);

            while (repository.existsBySerieFixaIdAndDataHoraInicio(serieFixaId, candidatoInicio)
                    || jaExisteDataNaLista(novos, candidatoInicio)) {
                candidatoInicio = candidatoInicio.plusWeeks(saltoSemanas);
            }

            LocalDateTime proximoFim = candidatoInicio.plusMinutes(duracaoMinutos);
            try {
                validarConflitos(
                        ultimo.getSala(),
                        ultimo.getProfissional(),
                        ultimo.getProfissional(),
                        candidatoInicio,
                        proximoFim,
                        true,
                        indiceSemana
                );
            } catch (RuntimeException ex) {
                break;
            }

            novos.add(criarOcorrenciaDaSerie(ultimo, candidatoInicio, proximoFim));
            ultimo = novos.get(novos.size() - 1);
            indiceSemana++;
        }

        if (!novos.isEmpty()) {
            for (Agendamento novo : novos) {
                pagamentoConsultaService.configurarPagamentoNovaOcorrenciaSerie(novo);
            }
            repository.saveAll(novos);
        }
    }

    public boolean isAgendamentoDoUsuario(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null || agendamento.getProfissional() == null) {
            return false;
        }
        return agendamento.getProfissional().getId().equals(usuarioLogado.getId());
    }

    public boolean podeVerValoresConsulta(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || !agendamento.possuiValoresConsulta()) {
            return false;
        }
        if (agendamento.getProfissional() != null
                && authService.profissionalIgnoraValoresEPagamento(agendamento.getProfissional())) {
            return false;
        }
        if (podeGerenciarAgendamentoDeOutros(usuarioLogado)) {
            return true;
        }
        return isAgendamentoDoUsuario(agendamento, usuarioLogado);
    }

    public boolean podeCancelarAgendamento(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null) {
            return false;
        }
        if (!podeGerenciarAgendamentoDeOutros(usuarioLogado)) {
            return false;
        }
        if (agendamento.getDataHoraInicio() == null
                || !agendamento.getDataHoraInicio().isAfter(LocalDateTime.now())) {
            return false;
        }
        return agendamento.getProfissional() != null;
    }

    public boolean podeEncerrarSerieFixa(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null) {
            return false;
        }
        if (!agendamento.isFixoSemanal() && !agendamento.isQuinzenal()) {
            return false;
        }
        if (agendamento.getSerieFixaId() == null || agendamento.getSerieFixaId().isBlank()) {
            return false;
        }
        if (agendamento.getDataHoraInicio() == null
                || !agendamento.getDataHoraInicio().isAfter(LocalDateTime.now())) {
            return false;
        }
        if (agendamento.getProfissional() == null) {
            return false;
        }
        return podeGerenciarAgendamentoDeOutros(usuarioLogado)
                || isAgendamentoDoUsuario(agendamento, usuarioLogado);
    }

    public String tipoAcaoGrade(Agendamento agendamento) {
        if (agendamento.isQuinzenal()) {
            return "QUINZENAL";
        }
        if (agendamento.isFixoSemanal()) {
            return "SEMANAL";
        }
        return "AVULSO";
    }

    /**
     * IDs dos agendamentos na grade em que o usuario logado pode abrir o popup (duplo clique).
     * Valor = tipo (AVULSO, SEMANAL, QUINZENAL).
     */
    public Map<Long, String> montarAcoesGradePorId(AgendaSalaView agendaSala, Usuario usuarioLogado) {
        Map<Long, String> acoes = new LinkedHashMap<>();
        if (agendaSala == null || agendaSala.getLinhas() == null) {
            return acoes;
        }
        for (AgendaSalaLinha linha : agendaSala.getLinhas()) {
            if (linha.getAgendamentos() == null) {
                continue;
            }
            for (Agendamento agendamento : linha.getAgendamentos()) {
                if (podeCancelarAgendamento(agendamento, usuarioLogado)
                        || podeEncerrarSerieFixa(agendamento, usuarioLogado)) {
                    acoes.put(agendamento.getId(), tipoAcaoGrade(agendamento));
                }
            }
        }
        return acoes;
    }

    @Transactional
    public void cancelar(Long id, Usuario usuarioLogado) {
        Agendamento agendamento = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agendamento não encontrado."));
        validarPermissaoCancelamento(agendamento, usuarioLogado);
        repository.deleteById(id);
    }

    public List<EncerramentoSerieRegistro> listarEncerramentosSerieRecentes() {
        return encerramentoSerieRegistroRepository.findTop30ByOrderByEncerradoEmDesc();
    }

    @Transactional
    public void encerrarSerieFixa(Long id, String motivoEncerramento, Usuario usuarioLogado) {
        Agendamento agendamento = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agendamento não encontrado."));

        if (!Boolean.TRUE.equals(agendamento.getFixo()) || agendamento.getSerieFixaId() == null || agendamento.getSerieFixaId().isBlank()) {
            throw new RuntimeException("Este agendamento não pertence a uma série fixa.");
        }

        String recorrencia = recorrenciaDoAgendamento(agendamento);
        if (RECORRENCIA_AVULSO.equals(recorrencia)) {
            throw new RuntimeException("Somente séries semanal ou quinzenal podem ser encerradas.");
        }

        String motivo = motivoEncerramento != null ? motivoEncerramento.trim() : "";
        if (motivo.length() < 3) {
            throw new RuntimeException("Informe o motivo do encerramento da série (mínimo 3 caracteres).");
        }
        if (motivo.length() > 500) {
            throw new RuntimeException("O motivo do encerramento deve ter no maximo 500 caracteres.");
        }

        validarPermissaoEncerramentoSerie(agendamento, usuarioLogado);

        String serieFixaId = agendamento.getSerieFixaId();
        List<Agendamento> serieCompleta = repository.findBySerieFixaIdOrderByDataHoraInicioAsc(serieFixaId);
        if (serieCompleta.isEmpty()) {
            throw new RuntimeException("Nenhum horário encontrado para esta série.");
        }

        Agendamento referencia = serieCompleta.get(0);
        EncerramentoSerieRegistro registro = new EncerramentoSerieRegistro();
        registro.setSerieFixaId(serieFixaId);
        registro.setNomeCliente(referencia.getNomeCliente() != null ? referencia.getNomeCliente().trim() : "-");
        registro.setProfissional(referencia.getProfissional());
        registro.setSala(referencia.getSala());
        registro.setTipoRecorrencia(recorrencia);
        registro.setMotivo(motivo);
        registro.setEncerradoEm(LocalDateTime.now());
        registro.setEncerradoPor(usuarioLogado);
        registro.setQuantidadeHorarios(serieCompleta.size());
        encerramentoSerieRegistroRepository.save(registro);

        repository.deleteAll(serieCompleta);
    }

    private void validarFormulario(AgendamentoForm form) {
        if (form.getProfissionalId() == null) {
            throw new RuntimeException("Selecione o profissional.");
        }
        if (form.getSalaId() == null) {
            throw new RuntimeException("Selecione a sala.");
        }
        if (form.getDataAtendimento() == null) {
            throw new RuntimeException("Informe a data da consulta.");
        }
        if (form.getHorarioAtendimento() == null) {
            throw new RuntimeException("Selecione um horário fixo.");
        }
        if (normalizarRecorrencia(form) == null) {
            throw new RuntimeException("Selecione um tipo de recorrencia valido.");
        }
        if (form.getNomeCliente() == null || form.getNomeCliente().isBlank()) {
            throw new RuntimeException("Informe o nome do cliente.");
        }
    }

    private String normalizarRecorrencia(AgendamentoForm form) {
        if (form.getRecorrencia() != null && !form.getRecorrencia().isBlank()) {
            return switch (form.getRecorrencia().toUpperCase()) {
                case RECORRENCIA_AVULSO, RECORRENCIA_SEMANAL, RECORRENCIA_QUINZENAL -> form.getRecorrencia().toUpperCase();
                default -> null;
            };
        }

        return form.isFixo() ? RECORRENCIA_SEMANAL : RECORRENCIA_AVULSO;
    }

    private LocalDate obterDataAncoraSerie(Agendamento agendamento) {
        if (agendamento.getSerieFixaId() != null && !agendamento.getSerieFixaId().isBlank()) {
            Optional<Agendamento> primeiro = repository.findFirstBySerieFixaIdOrderByDataHoraInicioAsc(
                    agendamento.getSerieFixaId()
            );
            if (primeiro.isPresent() && primeiro.get().getDataHoraInicio() != null) {
                return primeiro.get().getDataHoraInicio().toLocalDate();
            }
        }
        return agendamento.getDataHoraInicio().toLocalDate();
    }

    private LocalDate primeiraDataPadraoDaSerieApos(LocalDate referencia, LocalDate anchor, int saltoSemanas) {
        if (!referencia.isAfter(anchor)) {
            return anchor;
        }
        long semanasEntre = ChronoUnit.WEEKS.between(anchor, referencia);
        long passos = semanasEntre / saltoSemanas;
        LocalDate candidata = anchor.plusWeeks(passos * saltoSemanas);
        if (candidata.isBefore(referencia)) {
            candidata = candidata.plusWeeks(saltoSemanas);
        }
        return candidata;
    }

    private int obterSaltoSemanas(String recorrencia) {
        if (RECORRENCIA_QUINZENAL.equals(recorrencia)) {
            return 2;
        }
        return 1;
    }

    private int obterQuantidadeRepeticoes(String recorrencia) {
        if (RECORRENCIA_AVULSO.equals(recorrencia)) {
            return 1;
        }
        if (RECORRENCIA_QUINZENAL.equals(recorrencia)) {
            return OCORRENCIAS_QUINZENAIS_PADRAO;
        }
        return SEMANAS_FIXAS_PADRAO;
    }

    private int obterLimiteOcorrenciasFuturas(String recorrencia) {
        return obterQuantidadeRepeticoes(recorrencia);
    }

    private String recorrenciaDoAgendamento(Agendamento agendamento) {
        if (agendamento.isQuinzenal()) {
            return RECORRENCIA_QUINZENAL;
        }
        if (agendamento.isFixoSemanal()) {
            return RECORRENCIA_SEMANAL;
        }
        if (agendamento.getTipoRecorrencia() != null && !agendamento.getTipoRecorrencia().isBlank()) {
            return agendamento.getTipoRecorrencia().toUpperCase(Locale.ROOT);
        }
        if (agendamento.getRecorrencia() != null && !agendamento.getRecorrencia().isBlank()) {
            return agendamento.getRecorrencia().toUpperCase(Locale.ROOT);
        }
        if (Boolean.TRUE.equals(agendamento.getFixo())) {
            return RECORRENCIA_SEMANAL;
        }
        return RECORRENCIA_AVULSO;
    }

    private Agendamento criarOcorrenciaDaSerie(Agendamento modelo, LocalDateTime inicio, LocalDateTime fim) {
        Agendamento novo = new Agendamento();
        novo.setProfissional(modelo.getProfissional());
        novo.setSala(modelo.getSala());
        novo.setNomeCliente(modelo.getNomeCliente());
        novo.setDataHoraInicio(inicio);
        novo.setDataHoraFim(fim);
        novo.setFixo(true);
        novo.setSerieFixaId(modelo.getSerieFixaId());
        String recorrencia = recorrenciaDoAgendamento(modelo);
        novo.setTipoRecorrencia(recorrencia);
        novo.setRecorrencia(recorrencia);
        valorConsultaService.copiarValores(novo, modelo);
        return novo;
    }

    private boolean podeGerenciarAgendamentoDeOutros(Usuario usuarioLogado) {
        return authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado);
    }

    private void validarPermissaoCancelamento(Agendamento agendamento, Usuario usuarioLogado) {
        if (!podeGerenciarAgendamentoDeOutros(usuarioLogado)) {
            throw new RuntimeException(
                    "Somente a administração ou a dona da clínica podem cancelar locações de sala."
            );
        }
        validarAgendamentoParaAcaoGestao(agendamento);
    }

    private void validarPermissaoEncerramentoSerie(Agendamento agendamento, Usuario usuarioLogado) {
        if (!podeGerenciarAgendamentoDeOutros(usuarioLogado)
                && !isAgendamentoDoUsuario(agendamento, usuarioLogado)) {
            throw new RuntimeException(
                    "Você só pode encerrar as suas próprias séries semanal ou quinzenal."
            );
        }
        validarAgendamentoParaAcaoGestao(agendamento);
    }

    private void validarAgendamentoParaAcaoGestao(Agendamento agendamento) {
        if (agendamento.getProfissional() == null) {
            throw new RuntimeException("Agendamento sem profissional vinculado.");
        }
        if (agendamento.getDataHoraInicio() == null) {
            throw new RuntimeException("Agendamento sem data de inicio.");
        }
    }

    private void validarConflitos(
            Sala sala,
            Usuario profissional,
            Usuario usuarioLogado,
            LocalDateTime inicio,
            LocalDateTime fim,
            boolean fixo,
            int indiceSemana
    ) {
        validarConflitos(sala, profissional, usuarioLogado, inicio, fim, fixo, indiceSemana, null);
    }

    private void validarConflitos(
            Sala sala,
            Usuario profissional,
            Usuario usuarioLogado,
            LocalDateTime inicio,
            LocalDateTime fim,
            boolean fixo,
            int indiceSemana,
            Long ignorarAgendamentoId
    ) {
        repository.findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
                        profissional.getId(),
                        fim,
                        inicio
                )
                .filter(conflito -> !mesmoAgendamento(conflito, ignorarAgendamentoId))
                .ifPresent(conflito -> {
                    throw conflitoMensagem(
                            mensagemConflitoProfissional(profissional, conflito, usuarioLogado),
                            inicio,
                            fixo,
                            indiceSemana
                    );
                });

        Long idIgnorado = ignorarAgendamentoId != null ? ignorarAgendamentoId : -1L;
        boolean salaOcupada = repository.findFirstOcupacaoAtivaNoHorarioExceto(
                sala.getId(),
                inicio,
                fim,
                idIgnorado
        ).isPresent();
        if (salaOcupada) {
            throw new RuntimeException(mensagemConflitoSala(sala, inicio));
        }
    }

    private boolean mesmoAgendamento(Agendamento agendamento, Long ignorarAgendamentoId) {
        return ignorarAgendamentoId != null
                && agendamento.getId() != null
                && ignorarAgendamentoId.equals(agendamento.getId());
    }

    private String mensagemConflitoProfissional(Usuario profissional, Agendamento conflito, Usuario usuarioLogado) {
        String salaConflito = conflito.getSala() != null && conflito.getSala().getNome() != null
                ? conflito.getSala().getNome()
                : "outra sala";

        boolean agendandoParaSiMesmo = usuarioLogado != null
                && profissional.getId() != null
                && profissional.getId().equals(usuarioLogado.getId());

        if (agendandoParaSiMesmo) {
            return "Você já tem um agendamento nesse horário na " + salaConflito + ".";
        }

        String nomeProfissional = profissional.getNome() != null && !profissional.getNome().isBlank()
                ? profissional.getNome()
                : "Este profissional";
        return nomeProfissional + " já tem um agendamento nesse horário na " + salaConflito + ".";
    }

    private RuntimeException conflitoMensagem(String mensagemBase, LocalDateTime inicio, boolean fixo, int indiceSemana) {
        if (!fixo || indiceSemana == 0) {
            return new RuntimeException(mensagemBase);
        }
        return new RuntimeException(
                mensagemBase + " Conflito encontrado na repetição da semana de "
                        + inicio.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "."
        );
    }

    private String mensagemConflitoSala(Sala sala, LocalDateTime inicio) {
        String nomeSala = sala != null && sala.getNome() != null && !sala.getNome().isBlank()
                ? sala.getNome()
                : "Sala";
        return "Conflito de agenda: a Sala " + nomeSala
                + " já está ocupada em "
                + inicio.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " às "
                + inicio.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                + ". Não é possível salvar este horário.";
    }

    private Usuario carregarProfissional(Long profissionalId, Usuario usuarioLogado) {
        if (!podeGerenciarAgendamentoDeOutros(usuarioLogado) && !usuarioLogado.getId().equals(profissionalId)) {
            throw new RuntimeException("Você só pode agendar para o seu próprio usuário.");
        }

        Usuario profissional = usuarioRepository.findById(profissionalId)
                .orElseThrow(() -> new RuntimeException("Profissional não encontrado."));

        if (!podeAtender(profissional)) {
            throw new RuntimeException("O usuário selecionado não é um profissional.");
        }

        return profissional;
    }

    private boolean podeAtender(Usuario usuario) {
        return "ROLE_PROFISSIONAL".equals(usuario.getCargo())
                || "ROLE_ADMIN".equals(usuario.getCargo());
    }

    private String formatarMesReferencia(YearMonth mesReferencia) {
        String mes = mesReferencia.getMonth()
                .getDisplayName(TextStyle.FULL_STANDALONE, new Locale("pt", "BR"));
        if (mes != null && !mes.isBlank()) {
            mes = Character.toUpperCase(mes.charAt(0)) + mes.substring(1);
        }
        return mes + " de " + mesReferencia.getYear();
    }

    private void validarHorario(LocalDateTime inicio, LocalDateTime fim) {
        DayOfWeek diaSemana = inicio.getDayOfWeek();
        if (diaSemana == DayOfWeek.SUNDAY) {
            throw new RuntimeException("A clínica funciona somente de segunda a sábado.");
        }

        LocalDate data = inicio.toLocalDate();
        if (!fim.toLocalDate().equals(data)) {
            throw new RuntimeException("Cada consulta deve terminar no mesmo dia.");
        }

        if (inicio.getMinute() != 0 || inicio.getSecond() != 0 || inicio.getNano() != 0) {
            throw new RuntimeException("Os agendamentos precisam iniciar em hora cheia.");
        }

        if (inicio.toLocalTime().isBefore(HORA_ABERTURA) || fim.toLocalTime().isAfter(HORA_FECHAMENTO)) {
            throw new RuntimeException("Os atendimentos devem ficar entre 07:00 e 21:00.");
        }
    }

    private Sala buscarSalaPadrao(Long salaId) {
        List<Sala> salas = listarSalas();
        if (salas.isEmpty()) {
            throw new RuntimeException("Nenhuma sala cadastrada.");
        }

        Long salaSelecionadaId = salaId != null ? salaId : salas.get(0).getId();
        return salas.stream()
                .filter(item -> item.getId().equals(salaSelecionadaId))
                .findFirst()
                .orElse(salas.get(0));
    }

    private boolean jaExisteDataNaLista(List<Agendamento> agendamentos, LocalDateTime dataHoraInicio) {
        for (Agendamento agendamento : agendamentos) {
            if (dataHoraInicio.equals(agendamento.getDataHoraInicio())) {
                return true;
            }
        }
        return false;
    }

    private String chaveSerie(Agendamento agendamento) {
        if (agendamento.getSerieFixaId() != null && !agendamento.getSerieFixaId().isBlank()) {
            return agendamento.getSerieFixaId();
        }
        return "avulso-" + agendamento.getId();
    }

    public Long resolverSalaIdParaGrade(Long salaId, LocalDate referencia) {
        return resolverSalaIdParaGrade(salaId, referencia, contarAgendamentosPorSalaNaSemana(referencia));
    }

    public Long resolverSalaIdParaGrade(Long salaId, LocalDate referencia, Map<Long, Integer> contagemSemana) {
        if (salaId != null) {
            return salaId;
        }

        Map<Long, Integer> contagem = contagemSemana != null ? contagemSemana : contarAgendamentosPorSalaNaSemana(referencia);
        for (Sala sala : listarSalas()) {
            if (contagem.getOrDefault(sala.getId(), 0) > 0) {
                return sala.getId();
            }
        }

        List<Sala> salas = listarSalas();
        if (salas.isEmpty()) {
            throw new RuntimeException("Nenhuma sala cadastrada.");
        }
        return salas.get(0).getId();
    }

    public Map<Long, Integer> contarAgendamentosPorSalaNaSemana(LocalDate referencia) {
        LocalDate inicioSemana = obterInicioSemana(referencia);
        LocalDate fimSemana = inicioSemana.plusDays(5);
        LocalDateTime inicioConsulta = inicioSemana.atTime(HORA_ABERTURA);
        LocalDateTime fimConsulta = fimSemana.plusDays(1).atStartOfDay();

        Map<Long, Integer> contagem = new LinkedHashMap<>();
        for (Object[] linha : repository.contarAgendamentosPorSalaNoPeriodo(inicioConsulta, fimConsulta)) {
            Long salaId = (Long) linha[0];
            Number total = (Number) linha[1];
            contagem.put(salaId, total.intValue());
        }
        return contagem;
    }

    public Optional<String> mensagemAgendamentosEmOutraSala(Long salaIdAtual, LocalDate referencia) {
        return mensagemAgendamentosEmOutraSala(salaIdAtual, contarAgendamentosPorSalaNaSemana(referencia));
    }

    public Optional<String> mensagemAgendamentosEmOutraSala(Long salaIdAtual, Map<Long, Integer> contagem) {
        if (contagem.isEmpty() || contagem.getOrDefault(salaIdAtual, 0) > 0) {
            return Optional.empty();
        }

        StringBuilder salasComHorario = new StringBuilder();
        for (Sala item : listarSalas()) {
            int total = contagem.getOrDefault(item.getId(), 0);
            if (total > 0 && !item.getId().equals(salaIdAtual)) {
                if (salasComHorario.length() > 0) {
                    salasComHorario.append(", ");
                }
                salasComHorario.append(item.getNome()).append(" (").append(total).append(")");
            }
        }

        if (salasComHorario.length() == 0) {
            return Optional.empty();
        }

        return Optional.of(
                "Nesta semana ha agendamentos em: "
                        + salasComHorario
                        + ". Selecione a sala acima para ver na grade."
        );
    }

    private Agendamento buscarAgendamentoNaCelula(
            List<Agendamento> agendamentosSemana,
            LocalDate dia,
            LocalTime horario
    ) {
        LocalDateTime inicioCelula = LocalDateTime.of(dia, horario);
        return agendamentosSemana.stream()
                .filter(agendamento -> inicioHoraCheia(agendamento.getDataHoraInicio()).equals(inicioCelula))
                .filter(agendamento -> pagamentoConsultaService.ocupaVagaNaGrade(agendamento))
                .findFirst()
                .orElse(null);
    }

    private LocalDateTime inicioHoraCheia(LocalDateTime dataHora) {
        return dataHora.withMinute(0).withSecond(0).withNano(0);
    }

    private LocalDate obterInicioSemana(LocalDate referencia) {
        LocalDate base;
        if (referencia != null) {
            base = referencia;
        } else {
            LocalDate hoje = LocalDate.now();
            base = hoje.getDayOfWeek() == DayOfWeek.FRIDAY ? hoje.plusWeeks(1) : hoje;
        }
        return base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
