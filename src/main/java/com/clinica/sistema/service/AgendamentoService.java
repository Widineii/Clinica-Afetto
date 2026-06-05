package com.clinica.sistema.service;

import com.clinica.sistema.dto.AcompanhamentoAgendaFiltros;
import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.RelocacaoAgendamentoForm;
import com.clinica.sistema.dto.AgendaGradeCelula;
import com.clinica.sistema.dto.AgendaSalaLinha;
import com.clinica.sistema.dto.AgendaSalaView;
import com.clinica.sistema.dto.ProximaConsultaMensalPreparacao;
import com.clinica.sistema.dto.MensalAgendamentoLinha;
import com.clinica.sistema.dto.TurnoLocacao;
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
import com.clinica.sistema.util.MoedaBrasilUtil;
import com.clinica.sistema.util.WhatsAppNumeroUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final String RECORRENCIA_MENSAL = "MENSAL";
    private static final int LIMITE_DATAS_MENSAL_VISIVEIS = 6;
    private static final int MESES_HISTORICO_AGENDA_PADRAO = 4;
    private static final Duration INTERVALO_RENOVACAO_SERIES = Duration.ofMinutes(10);
    private static final int ANTECEDENCIA_MINIMA_CANCELAMENTO_HORAS = 24;

    private final AgendamentoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final SalaRepository salaRepository;
    private final AuthService authService;
    private final ValorConsultaService valorConsultaService;
    private final PagamentoConsultaService pagamentoConsultaService;
    private final EncerramentoSerieRegistroRepository encerramentoSerieRegistroRepository;
    private final NovoAgendamentoNotificacaoService novoAgendamentoNotificacaoService;
    private final FeriadoBeloHorizonteService feriadoBeloHorizonteService;
    private final int mesesHistoricoAgenda;

    public AgendamentoService(
            AgendamentoRepository repository,
            UsuarioRepository usuarioRepository,
            SalaRepository salaRepository,
            AuthService authService,
            ValorConsultaService valorConsultaService,
            PagamentoConsultaService pagamentoConsultaService,
            EncerramentoSerieRegistroRepository encerramentoSerieRegistroRepository,
            NovoAgendamentoNotificacaoService novoAgendamentoNotificacaoService,
            FeriadoBeloHorizonteService feriadoBeloHorizonteService,
            @Value("${app.agenda.meses-historico:4}") int mesesHistoricoAgenda
    ) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.salaRepository = salaRepository;
        this.authService = authService;
        this.valorConsultaService = valorConsultaService;
        this.pagamentoConsultaService = pagamentoConsultaService;
        this.encerramentoSerieRegistroRepository = encerramentoSerieRegistroRepository;
        this.novoAgendamentoNotificacaoService = novoAgendamentoNotificacaoService;
        this.feriadoBeloHorizonteService = feriadoBeloHorizonteService;
        this.mesesHistoricoAgenda = Math.max(1, Math.min(mesesHistoricoAgenda, MESES_HISTORICO_AGENDA_PADRAO));
    }

    private volatile Instant ultimaRenovacaoSeries = Instant.EPOCH;

    public List<Agendamento> buscarParaUsuario(Usuario usuarioLogado) {
        return buscarPorProfissional(usuarioLogado.getId());
    }

    public Optional<Agendamento> buscarPorId(Long id) {
        return repository.findById(id);
    }

    public List<Agendamento> buscarPorProfissional(Long profissionalId) {
        var janela = intervaloListaAgenda();
        return repository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                profissionalId,
                janela.desde(),
                janela.ate()
        );
    }

    private record IntervaloListaAgenda(LocalDateTime desde, LocalDateTime ate) {
    }

    private IntervaloListaAgenda intervaloListaAgenda() {
        LocalDateTime desde = LocalDateTime.now().minusMonths(mesesHistoricoAgenda);
        LocalDateTime ate = LocalDate.now().plusWeeks(SEMANAS_FIXAS_PADRAO + 2).plusDays(1).atStartOfDay();
        return new IntervaloListaAgenda(desde, ate);
    }

    public ProfissionalAgendamentosResumo montarResumoAgendamentos(Usuario profissional) {
        return montarResumoAgendamentos(profissional, profissional);
    }

    public ProfissionalAgendamentosResumo montarResumoAgendamentos(Usuario profissional, Usuario usuarioLogado) {
        return montarResumoAgendamentos(profissional, buscarPorProfissional(profissional.getId()), usuarioLogado);
    }

    private ProfissionalAgendamentosResumo montarResumoAgendamentos(
            Usuario profissional,
            List<Agendamento> agendamentos,
            Usuario usuarioLogado
    ) {
        List<Agendamento> avulsos = listarProximosPorSerie(agendamentos, Agendamento::isAvulsoSemMensal);
        List<MensalAgendamentoLinha> mensais = agruparMensaisAtivos(agendamentos, usuarioLogado);
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
                mensais,
                avulsos.size(),
                seriesFixas.size(),
                seriesQuinzenais.size(),
                mensais.size()
        );
    }

    public List<ProfissionalAgendamentosResumo> montarResumosProfissionais(List<Usuario> profissionais) {
        return montarResumosProfissionais(profissionais, null);
    }

    public List<ProfissionalAgendamentosResumo> montarResumosProfissionais(
            List<Usuario> profissionais,
            Usuario usuarioLogado
    ) {
        if (profissionais == null || profissionais.isEmpty()) {
            return List.of();
        }
        List<Long> ids = profissionais.stream().map(Usuario::getId).toList();
        var janela = intervaloListaAgenda();
        List<Agendamento> carregados = repository
                .findByProfissionalIdInAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                        ids,
                        janela.desde(),
                        janela.ate()
                );
        java.util.Map<Long, List<Agendamento>> porProfissional = carregados.stream()
                .filter(agendamento -> agendamento.getProfissional() != null)
                .collect(java.util.stream.Collectors.groupingBy(agendamento -> agendamento.getProfissional().getId()));
        return profissionais.stream()
                .map(profissional -> montarResumoAgendamentos(
                        profissional,
                        porProfissional.getOrDefault(profissional.getId(), List.of()),
                        usuarioLogado
                ))
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

    public List<MensalAgendamentoLinha> agruparMensaisAtivos(List<Agendamento> agendamentos, Usuario usuarioLogado) {
        DateTimeFormatter formatoData = DateTimeFormatter.ofPattern("dd/MM");
        Map<String, List<Agendamento>> porCliente = new LinkedHashMap<>();
        agendamentos.stream()
                .filter(Agendamento::isMensal)
                .filter(agendamento -> agendamento.getNomeCliente() != null && !agendamento.getNomeCliente().isBlank())
                .forEach(agendamento -> porCliente
                        .computeIfAbsent(chaveMensalCliente(agendamento), chave -> new ArrayList<>())
                        .add(agendamento));

        return porCliente.values().stream()
                .map(lista -> montarMensalAgendamentoLinha(lista, formatoData, usuarioLogado))
                .sorted(Comparator.comparing(
                        linha -> linha.getAgendamentoReferencia() != null
                                && linha.getAgendamentoReferencia().getDataHoraInicio() != null
                                ? linha.getAgendamentoReferencia().getDataHoraInicio()
                                : LocalDateTime.MAX,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();
    }

    private MensalAgendamentoLinha montarMensalAgendamentoLinha(
            List<Agendamento> agendamentosCliente,
            DateTimeFormatter formatoData,
            Usuario usuarioLogado
    ) {
        List<Agendamento> ordenados = agendamentosCliente.stream()
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .sorted(Comparator.comparing(Agendamento::getDataHoraInicio))
                .toList();
        if (ordenados.isEmpty()) {
            throw new IllegalStateException("Série mensal sem datas.");
        }

        List<Agendamento> paraExibir = ordenados.size() > LIMITE_DATAS_MENSAL_VISIVEIS
                ? List.of(ordenados.get(ordenados.size() - 1))
                : ordenados;

        Agendamento referencia = ordenados.get(ordenados.size() - 1);
        Agendamento referenciaValores = resolverReferenciaValoresExibicaoSerie(ordenados, referencia);
        List<SerieAgendamentoOcorrencia> datasHistorico = montarDatasHistoricoMensal(
                referencia,
                ordenados,
                paraExibir,
                formatoData,
                usuarioLogado
        );

        return new MensalAgendamentoLinha(
                referencia,
                datasHistorico,
                referenciaValores.getValoresConsultaResumo()
        );
    }

    private List<SerieAgendamentoOcorrencia> montarDatasHistoricoMensal(
            Agendamento referencia,
            List<Agendamento> ordenados,
            List<Agendamento> fallbackAgendamentos,
            DateTimeFormatter formatoData,
            Usuario usuarioLogado
    ) {
        List<String> rotulos = parseHistoricoDatasMensal(referencia.getHistoricoDatasMensal());
        if (rotulos.isEmpty()) {
            return fallbackAgendamentos.stream()
                    .map(agendamento -> ocorrenciaMensal(agendamento, formatoData, usuarioLogado))
                    .toList();
        }
        if (rotulos.size() > LIMITE_DATAS_MENSAL_VISIVEIS) {
            rotulos = List.of(rotulos.get(rotulos.size() - 1));
        }
        return rotulos.stream()
                .map(rotulo -> {
                    Agendamento agendamento = encontrarAgendamentoPorRotulo(ordenados, rotulo, formatoData)
                            .orElse(null);
                    if (agendamento != null) {
                        return ocorrenciaMensal(agendamento, formatoData, usuarioLogado);
                    }
                    return new SerieAgendamentoOcorrencia(
                            null,
                            rotulo,
                            null,
                            false,
                            false,
                            false,
                            false
                    );
                })
                .toList();
    }

    private SerieAgendamentoOcorrencia ocorrenciaMensal(
            Agendamento agendamento,
            DateTimeFormatter formatoData,
            Usuario usuarioLogado
    ) {
        return new SerieAgendamentoOcorrencia(
                agendamento.getId(),
                agendamento.getDataHoraInicio().format(formatoData),
                agendamento.getStatusPagamento(),
                pagamentoConsultaService.exibirBotaoPagar(agendamento),
                agendamento.isPagamentoPago(),
                podeRealocar(agendamento, usuarioLogado),
                podeCancelarAgendamento(agendamento, usuarioLogado)
        );
    }

    private Optional<Agendamento> encontrarAgendamentoPorRotulo(
            List<Agendamento> agendamentos,
            String rotulo,
            DateTimeFormatter formatoData
    ) {
        return agendamentos.stream()
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> rotulo.equals(agendamento.getDataHoraInicio().format(formatoData)))
                .findFirst();
    }

    private List<String> parseHistoricoDatasMensal(String historico) {
        if (historico == null || historico.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(historico.split("\\|"))
                .map(String::trim)
                .filter(parte -> !parte.isBlank())
                .toList();
    }

    private String serializarHistoricoDatasMensal(List<String> datas) {
        return String.join("|", datas);
    }

    private String formatarDataHistoricoMensal(LocalDateTime dataHora) {
        return dataHora.format(DateTimeFormatter.ofPattern("dd/MM"));
    }

    private void registrarHistoricoMensal(Agendamento origem, Agendamento novo) {
        List<String> historico = new ArrayList<>();
        if (origem != null && origem.getHistoricoDatasMensal() != null && !origem.getHistoricoDatasMensal().isBlank()) {
            historico.addAll(parseHistoricoDatasMensal(origem.getHistoricoDatasMensal()));
        } else if (origem != null && origem.getDataHoraInicio() != null) {
            historico.add(formatarDataHistoricoMensal(origem.getDataHoraInicio()));
        }
        if (novo.getDataHoraInicio() != null) {
            String novaData = formatarDataHistoricoMensal(novo.getDataHoraInicio());
            if (!historico.contains(novaData)) {
                historico.add(novaData);
            }
        }
        if (historico.size() > LIMITE_DATAS_MENSAL_VISIVEIS) {
            historico = new ArrayList<>(List.of(historico.get(historico.size() - 1)));
        }
        novo.setHistoricoDatasMensal(serializarHistoricoDatasMensal(historico));
    }

    private void registrarValoresConsulta(
            Agendamento agendamento,
            AgendamentoForm form,
            Sala sala,
            String recorrencia,
            Usuario profissional,
            boolean primeiraConsultaSerie
    ) {
        if (!authService.profissionalIgnoraValoresEPagamento(profissional)) {
            boolean permitirIndicacao = primeiraConsultaSerie
                    && !TurnoLocacao.isTurno(form.getTurnoLocacao());
            valorConsultaService.aplicarValores(agendamento, form, sala, recorrencia, permitirIndicacao, profissional);
            return;
        }
        if (authService.podeAcompanharGanhosConsultaPropria(profissional)
                && profissional.getId() != null
                && form.getProfissionalId() != null
                && profissional.getId().equals(form.getProfissionalId())) {
            valorConsultaService.aplicarValorConsultaPropriaDona(agendamento, form);
            return;
        }
        agendamento.setIndicacaoDona(form.isIndicacaoDona() && primeiraConsultaSerie);
    }

    private String chaveMensalCliente(Agendamento agendamento) {
        Long profissionalId = agendamento.getProfissional() != null ? agendamento.getProfissional().getId() : 0L;
        String cliente = agendamento.getNomeCliente() != null
                ? agendamento.getNomeCliente().trim().toLowerCase(Locale.ROOT)
                : "";
        return "mensal-" + profissionalId + "-" + cliente;
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
                .map(agendamento -> montarOcorrenciaSerie(agendamento, formatoData, usuarioLogado))
                .limit(obterLimiteOcorrenciasFuturas(recorrenciaDoAgendamento(representante)))
                .toList();

        String salaNome = representante.getSala() != null && representante.getSala().getNome() != null
                ? representante.getSala().getNome()
                : "-";
        String diaSemanaRotulo = formatarHorarioDiaSemana(representante.getDataHoraInicio());
        Agendamento referenciaValores = resolverReferenciaValoresExibicaoSerie(
                agendamentos,
                chaveSerie,
                limite,
                representante
        );
        String valorProfInput = referenciaValores.getValorProfissionalRecebe() != null
                && referenciaValores.getValorProfissionalRecebe().signum() > 0
                ? MoedaBrasilUtil.formatarDecimal(referenciaValores.getValorProfissionalRecebe())
                : null;
        return new SerieAgendamentoLinha(
                representante.getNomeCliente(),
                salaNome,
                representante.getId(),
                recorrenciaDoAgendamento(representante),
                diaSemanaRotulo,
                proximasOcorrencias != null ? proximasOcorrencias : List.of(),
                referenciaValores.getValoresConsultaResumo(),
                valorProfInput
        );
    }

    /**
     * Valores exibidos no card da serie: primeira consulta futura ainda nao paga (taxa/clinica atualizada).
     * Se todas estiverem pagas, usa o representante da serie.
     */
    private Agendamento resolverReferenciaValoresExibicaoSerie(
            List<Agendamento> agendamentos,
            String chaveSerie,
            LocalDateTime limite,
            Agendamento fallback
    ) {
        return agendamentos.stream()
                .filter(agendamento -> chaveSerie(agendamento).equals(chaveSerie))
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !agendamento.getDataHoraInicio().isBefore(limite))
                .filter(agendamento -> !PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento()))
                .filter(Agendamento::possuiValoresConsulta)
                .min(Comparator.comparing(Agendamento::getDataHoraInicio))
                .orElse(fallback);
    }

    private Agendamento resolverReferenciaValoresExibicaoSerie(
            List<Agendamento> agendamentosSerie,
            Agendamento fallback
    ) {
        return agendamentosSerie.stream()
                .filter(agendamento -> !PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento()))
                .filter(Agendamento::possuiValoresConsulta)
                .max(Comparator.comparing(Agendamento::getDataHoraInicio))
                .orElse(fallback);
    }

    private SerieAgendamentoOcorrencia montarOcorrenciaSerie(
            Agendamento agendamento,
            DateTimeFormatter formatoData,
            Usuario usuarioLogado
    ) {
        boolean podeVerPagamento = pagamentoConsultaService.podeVerPagamento(agendamento, usuarioLogado);
        return new SerieAgendamentoOcorrencia(
                agendamento.getId(),
                agendamento.getDataHoraInicio().format(formatoData),
                agendamento.getStatusPagamento(),
                pagamentoConsultaService.exibirBotaoPagar(agendamento) && podeVerPagamento,
                agendamento.isPagamentoPago() && podeVerPagamento,
                podeRealocar(agendamento, usuarioLogado),
                podeCancelarAgendamento(agendamento, usuarioLogado)
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

    public List<LocalTime> listarHorariosDisponiveis() {
        List<LocalTime> horarios = new ArrayList<>();
        for (LocalTime horario = HORA_ABERTURA; !horario.equals(HORA_FECHAMENTO); horario = horario.plusHours(1)) {
            horarios.add(horario);
        }
        return horarios;
    }

    public List<TurnoLocacao> listarTurnosLocacao() {
        return List.of(TurnoLocacao.values());
    }

    public AgendaSalaView montarAgendaSala(Long salaId, LocalDate referencia) {
        return montarAgendaSala(salaId, referencia, null);
    }

    public AgendaSalaView montarAgendaSala(Long salaId, LocalDate referencia, Long profissionalIdParaDisponibilidade) {
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

        List<Agendamento> agendamentosProfissionalSemana = carregarAgendamentosProfissionalNaSemana(
                profissionalIdParaDisponibilidade,
                inicioConsulta,
                fimConsulta
        );

        List<LocalDate> diasSemana = inicioSemana.datesUntil(fimSemana.plusDays(1)).toList();
        List<AgendaSalaLinha> linhas = new ArrayList<>();

        for (LocalTime horario = HORA_ABERTURA; horario.isBefore(HORA_FECHAMENTO); horario = horario.plusHours(1)) {
            List<AgendaGradeCelula> porDia = new ArrayList<>();
            for (LocalDate dia : diasSemana) {
                porDia.add(resolverCelulaGrade(
                        agendamentosSemana,
                        agendamentosProfissionalSemana,
                        dia,
                        horario,
                        sala.getId()
                ));
            }
            linhas.add(new AgendaSalaLinha(horario, porDia));
        }

        AgendaSalaView view = new AgendaSalaView();
        view.setSala(sala);
        view.setInicioSemana(inicioSemana);
        view.setDiasSemana(diasSemana);
        view.setDiasEspeciaisPorDia(feriadoBeloHorizonteService.resolverDiasEspeciaisDaSemana(diasSemana));
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

    /**
     * Painel de gestão da dona da clínica: lista compilada com filtros de período, profissional e tipo.
     */
    public List<Agendamento> listarAgendamentosAcompanhamento(
            AcompanhamentoAgendaFiltros.FiltroProfissional filtroProfissional,
            AcompanhamentoAgendaFiltros.IntervaloPeriodo intervalo,
            AcompanhamentoAgendaFiltros.RecorrenciaConsulta recorrencia
    ) {
        if (intervalo == null) {
            return List.of();
        }
        LocalDateTime inicio = intervalo.inicio().atStartOfDay();
        LocalDateTime fim = intervalo.fim().plusDays(1).atStartOfDay();

        List<Agendamento> base;
        if (filtroProfissional != null && !filtroProfissional.todos()) {
            base = repository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                    filtroProfissional.profissionalId(),
                    inicio,
                    fim
            );
        } else {
            base = repository.findByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                    inicio,
                    fim
            );
        }

        return base.stream()
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !agendamento.isLocacaoTurno())
                .filter(agendamento -> {
                    String cliente = agendamento.getNomeCliente();
                    return cliente != null && !cliente.isBlank();
                })
                .filter(recorrencia::aceita)
                .toList();
    }

    @Transactional
    public Agendamento salvar(AgendamentoForm form, Usuario usuarioLogado) {
        validarFormulario(form);
        validarContinuacaoMensal(form);
        if (!authService.isAdmin(usuarioLogado)
                && !authService.isDonaClinica(usuarioLogado)
                && pagamentoConsultaService.profissionalBloqueadoPorPendenciaPagamento(usuarioLogado)) {
            throw new RuntimeException(pagamentoConsultaService.mensagemBloqueioPagamento(usuarioLogado));
        }

        Usuario profissional = carregarProfissional(form.getProfissionalId(), usuarioLogado);
        Sala sala = salaRepository.findById(form.getSalaId())
                .orElseThrow(() -> new RuntimeException("Sala não encontrada."));

        IntervaloAtendimento intervalo = resolverIntervaloAtendimento(form);
        LocalDateTime inicio = LocalDateTime.of(form.getDataAtendimento(), intervalo.inicio());
        LocalDateTime fim = LocalDateTime.of(form.getDataAtendimento(), intervalo.fim());
        String turnoLocacao = intervalo.turnoCodigo();

        validarHorario(inicio, fim, turnoLocacao);

        List<Agendamento> novosAgendamentos = new ArrayList<>();
        String recorrencia = normalizarRecorrencia(form);
        int repeticoes = obterQuantidadeRepeticoes(recorrencia);
        String serieFixaId = isRecorrenciaUnica(recorrencia)
                ? null
                : recorrencia.toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID();

        for (int indice = 0; indice < repeticoes; indice++) {
            LocalDateTime inicioOcorrencia = calcularInicioOcorrenciaSerie(inicio, recorrencia, indice);
            LocalDateTime fimOcorrencia = calcularFimOcorrenciaSerie(fim, recorrencia, indice);

            validarConflitos(
                    sala,
                    profissional,
                    usuarioLogado,
                    inicioOcorrencia,
                    fimOcorrencia,
                    isRecorrenciaComSerie(recorrencia),
                    indice,
                    null,
                    novosAgendamentos
            );

            Agendamento novo = new Agendamento();
            novo.setProfissional(profissional);
            novo.setSala(sala);
            novo.setNomeCliente(form.getNomeCliente().trim());
            novo.setTelefoneCliente(normalizarTelefoneCliente(form.getTelefoneCliente()));
            novo.setDataHoraInicio(inicioOcorrencia);
            novo.setDataHoraFim(fimOcorrencia);
            novo.setFixo(isRecorrenciaComSerie(recorrencia));
            novo.setSerieFixaId(serieFixaId);
            novo.setTipoRecorrencia(recorrencia);
            novo.setRecorrencia(recorrencia);
            novo.setTurnoLocacao(turnoLocacao);
            boolean primeiraConsultaSerie = indice == 0;
            registrarValoresConsulta(novo, form, sala, recorrencia, profissional, primeiraConsultaSerie);
            novosAgendamentos.add(novo);
        }

        repository.saveAll(novosAgendamentos);
        if (RECORRENCIA_MENSAL.equals(recorrencia)) {
            Agendamento criado = novosAgendamentos.get(0);
            if (form.isContinuacaoMensal() && form.getAgendamentoOrigemId() != null) {
                Agendamento origem = repository.findById(form.getAgendamentoOrigemId()).orElse(null);
                registrarHistoricoMensal(origem, criado);
            } else if (criado.getDataHoraInicio() != null) {
                criado.setHistoricoDatasMensal(formatarDataHistoricoMensal(criado.getDataHoraInicio()));
            }
            repository.save(criado);
        }
        if (RECORRENCIA_MENSAL.equals(recorrencia) && form.isContinuacaoMensal()) {
            for (Agendamento agendamento : novosAgendamentos) {
                pagamentoConsultaService.configurarPagamentoNovaOcorrenciaSerie(agendamento);
            }
        } else {
            pagamentoConsultaService.configurarPagamentosAoSalvar(novosAgendamentos, profissional, usuarioLogado);
        }
        repository.saveAll(novosAgendamentos);
        novoAgendamentoNotificacaoService.registrarNovosAgendamentos(novosAgendamentos, usuarioLogado);
        return novosAgendamentos.get(0);
    }

    public boolean isRealocacaoAvulsa(Agendamento agendamento) {
        String recorrencia = recorrenciaDoAgendamento(agendamento);
        return isRecorrenciaUnica(recorrencia);
    }

    public boolean requerPagamentoUltimaConsultaParaRenovarMensal(Agendamento agendamento) {
        if (agendamento == null || !agendamento.isMensal()) {
            return false;
        }
        if (agendamento.getProfissional() != null
                && authService.profissionalIgnoraValoresEPagamento(agendamento.getProfissional())) {
            return false;
        }
        if (quantidadeDatasSerieMensal(agendamento) < LIMITE_DATAS_MENSAL_VISIVEIS) {
            return false;
        }
        return !agendamento.isPagamentoPago();
    }

    public boolean podeMarcarProximaConsultaMensal(Agendamento agendamento, Usuario usuarioLogado) {
        if (!possuiPermissaoMarcarProximaConsultaMensal(agendamento, usuarioLogado)) {
            return false;
        }
        return !requerPagamentoUltimaConsultaParaRenovarMensal(agendamento);
    }

    private boolean possuiPermissaoMarcarProximaConsultaMensal(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null) {
            return false;
        }
        if (!agendamento.isMensal()) {
            return false;
        }
        if (agendamento.getProfissional() == null) {
            return false;
        }
        return podeGerenciarAgendamentoDeOutros(usuarioLogado)
                || isAgendamentoDoUsuario(agendamento, usuarioLogado);
    }

    private int quantidadeDatasSerieMensal(Agendamento referencia) {
        if (referencia == null) {
            return 0;
        }
        List<String> rotulos = parseHistoricoDatasMensal(referencia.getHistoricoDatasMensal());
        int porHistorico = rotulos.isEmpty()
                ? (referencia.getDataHoraInicio() != null ? 1 : 0)
                : rotulos.size();
        if (referencia.getProfissional() != null
                && referencia.getProfissional().getId() != null
                && referencia.getNomeCliente() != null
                && !referencia.getNomeCliente().isBlank()) {
            int porRegistros = repository.countMensalByProfissionalIdAndNomeCliente(
                    referencia.getProfissional().getId(),
                    referencia.getNomeCliente().trim()
            );
            return Math.max(porHistorico, porRegistros);
        }
        return porHistorico;
    }

    private void validarContinuacaoMensal(AgendamentoForm form) {
        if (!form.isContinuacaoMensal() || form.getAgendamentoOrigemId() == null) {
            return;
        }
        Agendamento origem = repository.findById(form.getAgendamentoOrigemId())
                .orElseThrow(() -> new RuntimeException("Consulta mensal de origem não encontrada."));
        if (requerPagamentoUltimaConsultaParaRenovarMensal(origem)) {
            throw new RuntimeException(
                    "Com 6 consultas marcadas, pague a última antes de renovar. Sem pagamento, a série não continua."
            );
        }
    }

    public boolean podeCancelarSerieMensal(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null) {
            return false;
        }
        if (!agendamento.isMensal()) {
            return false;
        }
        if (agendamento.getProfissional() == null) {
            return false;
        }
        if (!podeGerenciarAgendamentoDeOutros(usuarioLogado)
                && !isAgendamentoDoUsuario(agendamento, usuarioLogado)) {
            return false;
        }
        return !listarConsultasMensaisFuturasDoCliente(agendamento).isEmpty();
    }

    @Transactional
    public int cancelarSerieMensal(Long agendamentoId, Usuario usuarioLogado) {
        Agendamento referencia = repository.findById(agendamentoId)
                .orElseThrow(() -> new RuntimeException("Agendamento não encontrado."));
        if (!referencia.isMensal()) {
            throw new RuntimeException("Somente consultas mensais podem ser canceladas em série.");
        }
        if (!podeCancelarSerieMensal(referencia, usuarioLogado)) {
            throw new RuntimeException("Sem permissão para cancelar a série mensal deste cliente.");
        }

        List<Agendamento> cancelar = listarConsultasMensaisFuturasDoCliente(referencia);
        if (cancelar.isEmpty()) {
            throw new RuntimeException("Não há consultas mensais futuras para cancelar.");
        }
        repository.deleteAll(cancelar);
        return cancelar.size();
    }

    private List<Agendamento> listarConsultasMensaisFuturasDoCliente(Agendamento referencia) {
        if (referencia.getProfissional() == null
                || referencia.getProfissional().getId() == null
                || referencia.getNomeCliente() == null
                || referencia.getNomeCliente().isBlank()) {
            return List.of();
        }
        LocalDateTime agora = LocalDateTime.now();
        String cliente = referencia.getNomeCliente().trim();
        Long profissionalId = referencia.getProfissional().getId();
        return repository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualOrderByDataHoraInicioAsc(
                        profissionalId,
                        agora
                ).stream()
                .filter(Agendamento::isMensal)
                .filter(agendamento -> mesmoNomeCliente(agendamento, cliente))
                .toList();
    }

    private boolean mesmoNomeCliente(Agendamento agendamento, String nomeReferencia) {
        if (agendamento.getNomeCliente() == null || nomeReferencia == null) {
            return false;
        }
        return agendamento.getNomeCliente().trim().equalsIgnoreCase(nomeReferencia.trim());
    }

    public LocalDate obterDataSugeridaProximaConsultaMensal(Agendamento origem) {
        return calcularDataSugeridaProximaConsultaMensal(origem);
    }

    public LocalTime obterHorarioSugeridoProximaConsultaMensal(Agendamento origem) {
        LocalTime horarioOrigem = origem.getDataHoraInicio() != null
                ? origem.getDataHoraInicio().toLocalTime()
                : listarHorariosDisponiveis().get(0);
        return normalizarHorarioSugerido(horarioOrigem);
    }

    public ProximaConsultaMensalPreparacao prepararProximaConsultaMensal(Long agendamentoId, Usuario usuarioLogado) {
        Agendamento origem = repository.findById(agendamentoId)
                .orElseThrow(() -> new RuntimeException("Agendamento não encontrado."));
        if (!podeMarcarProximaConsultaMensal(origem, usuarioLogado)) {
            throw new RuntimeException("Sem permissão para marcar a próxima consulta mensal.");
        }

        AgendamentoForm form = new AgendamentoForm();
        form.setProfissionalId(origem.getProfissional().getId());
        form.setSalaId(origem.getSala() != null ? origem.getSala().getId() : null);
        form.setNomeCliente(origem.getNomeCliente());
        form.setRecorrencia(RECORRENCIA_MENSAL);
        form.setFixo(false);
        form.setContinuacaoMensal(true);
        form.setIndicacaoDona(false);
        form.setValorProfissionalRecebe(origem.getValorProfissionalRecebe());
        form.setValorClinicaCobra(origem.getValorClinicaCobra());

        LocalDate dataSugerida = calcularDataSugeridaProximaConsultaMensal(origem);
        form.setDataAtendimento(dataSugerida);

        LocalTime horarioOrigem = origem.getDataHoraInicio() != null
                ? origem.getDataHoraInicio().toLocalTime()
                : listarHorariosDisponiveis().get(0);
        form.setHorarioAtendimento(normalizarHorarioSugerido(horarioOrigem));

        String cliente = origem.getNomeCliente() != null && !origem.getNomeCliente().isBlank()
                ? origem.getNomeCliente()
                : "cliente";
        String mensagem = "Próxima consulta mensal para "
                + cliente
                + ". Data sugerida: "
                + dataSugerida.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " — escolha outro dia ou horário se preferir. "
                + "Sem pagamento agora: entra na regra do seu pagamento (diário na véspera, semanal ou mensal).";

        return new ProximaConsultaMensalPreparacao(form, mensagem);
    }

    private LocalDate calcularDataSugeridaProximaConsultaMensal(Agendamento origem) {
        LocalDate base = origem.getDataHoraInicio() != null
                ? origem.getDataHoraInicio().toLocalDate().plusMonths(1)
                : LocalDate.now().plusMonths(1);
        return ajustarDataAtendimentoSugerida(base);
    }

    private LocalDate ajustarDataAtendimentoSugerida(LocalDate data) {
        LocalDate candidata = data;
        while (candidata.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidata = candidata.plusDays(1);
        }
        LocalDate hoje = LocalDate.now();
        if (candidata.isBefore(hoje)) {
            candidata = hoje;
            while (candidata.getDayOfWeek() == DayOfWeek.SUNDAY) {
                candidata = candidata.plusDays(1);
            }
        }
        return candidata;
    }

    private LocalTime normalizarHorarioSugerido(LocalTime horario) {
        LocalTime horaCheia = horario.withMinute(0).withSecond(0).withNano(0);
        List<LocalTime> disponiveis = listarHorariosDisponiveis();
        if (disponiveis.contains(horaCheia)) {
            return horaCheia;
        }
        return disponiveis.stream()
                .filter(hora -> !hora.isBefore(horaCheia))
                .findFirst()
                .orElse(disponiveis.get(disponiveis.size() - 1));
    }

    /**
     * Serie fixa semanal: proximas 12 datas no mesmo dia da semana da cadencia.
     * Serie quinzenal: proximas 6 datas a cada 2 semanas.
     * Avulso e mensal: lista vazia (qualquer data futura na tela).
     */
    public List<LocalDate> listarDatasPermitidasRealocacao(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return List.of();
        }
        if (isRealocacaoAvulsa(agendamento)) {
            return List.of();
        }

        String recorrencia = recorrenciaDoAgendamento(agendamento);
        int limiteOcorrencias = obterLimiteOcorrenciasFuturas(recorrencia);
        LocalDate anchor = obterDataAncoraSerie(agendamento);
        LocalTime horario = agendamento.getDataHoraInicio().toLocalTime();
        LocalDateTime agora = LocalDateTime.now();

        List<LocalDate> permitidas = new ArrayList<>();
        LocalDate candidata = primeiraDataPadraoDaSerieApos(LocalDate.now(), anchor, obterSaltoSemanas(recorrencia));
        int guarda = 0;
        while (permitidas.size() < limiteOcorrencias && guarda++ < 52) {
            LocalDateTime inicioCandidato = LocalDateTime.of(candidata, horario);
            if (!inicioCandidato.isBefore(agora)) {
                permitidas.add(candidata);
            }
            candidata = candidata.plusWeeks(obterSaltoSemanas(recorrencia));
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
        java.time.Duration duracao = java.time.Duration.between(
                agendamento.getDataHoraInicio(),
                agendamento.getDataHoraFim()
        );
        if (duracao.isZero() || duracao.isNegative()) {
            duracao = java.time.Duration.ofHours(1);
        }
        LocalDateTime novoFim = novoInicio.plus(duracao);
        validarHorario(novoInicio, novoFim, agendamento.getTurnoLocacao());

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

        LocalDate dataAnterior = agendamento.getDataHoraInicio() != null
                ? agendamento.getDataHoraInicio().toLocalDate()
                : null;
        agendamento.setSala(novaSala);
        agendamento.setDataHoraInicio(novoInicio);
        agendamento.setDataHoraFim(novoFim);
        if (dataAnterior != null && !dataAnterior.equals(novoInicio.toLocalDate())) {
            agendamento.setWhatsappLembreteEnviadoEm(null);
        }
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
        if (isRecorrenciaUnica(recorrencia)) {
            return;
        }

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
        int indiceOcorrencia = 1;
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

            LocalDateTime candidatoInicio = avancarInicioSerie(ultimo.getDataHoraInicio(), recorrencia);

            while (repository.existsBySerieFixaIdAndDataHoraInicio(serieFixaId, candidatoInicio)
                    || jaExisteDataNaLista(novos, candidatoInicio)) {
                candidatoInicio = avancarInicioSerie(candidatoInicio, recorrencia);
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
                        indiceOcorrencia
                );
            } catch (RuntimeException ex) {
                break;
            }

            novos.add(criarOcorrenciaDaSerie(ultimo, candidatoInicio, proximoFim));
            ultimo = novos.get(novos.size() - 1);
            indiceOcorrencia++;
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
        if (agendamento.getDataHoraInicio() == null
                || !agendamento.getDataHoraInicio().isAfter(LocalDateTime.now())) {
            return false;
        }
        if (agendamento.getProfissional() == null) {
            return false;
        }
        if (podeGerenciarAgendamentoDeOutros(usuarioLogado)) {
            return true;
        }
        if (!isAgendamentoDoUsuario(agendamento, usuarioLogado)) {
            return false;
        }
        String recorrencia = recorrenciaDoAgendamento(agendamento);
        if (!podeGerenciarAgendamentoDeOutros(usuarioLogado)) {
            if (RECORRENCIA_AVULSO.equals(recorrencia)) {
                return !pagamentoConsultaService.consultaJaFoiPaga(agendamento);
            }
            if (RECORRENCIA_MENSAL.equals(recorrencia)) {
                return false;
            }
        }
        if (pagamentoConsultaService.consultaJaFoiPaga(agendamento)) {
            return false;
        }
        return respeitaAntecedenciaMinimaCancelamento(agendamento);
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
            if (linha.getCelulas() == null) {
                continue;
            }
            for (AgendaGradeCelula celula : linha.getCelulas()) {
                if (celula == null || !celula.isOcupada()) {
                    continue;
                }
                Agendamento agendamento = celula.getAgendamento();
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

    public boolean podeAlterarValorProfissionalSerie(Long agendamentoId, Usuario usuarioLogado) {
        if (agendamentoId == null || usuarioLogado == null) {
            return false;
        }
        return repository.findById(agendamentoId)
                .map(agendamento -> podeAlterarValorProfissionalSerie(agendamento, usuarioLogado))
                .orElse(false);
    }

    public boolean podeAlterarValorProfissionalSerie(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null || !podeVerValoresConsulta(agendamento, usuarioLogado)) {
            return false;
        }
        if (TurnoLocacao.isTurno(agendamento.getTurnoLocacao())) {
            return false;
        }
        if (agendamento.isMensal()) {
            return podeGerenciarAgendamentoDeOutros(usuarioLogado)
                    || isAgendamentoDoUsuario(agendamento, usuarioLogado);
        }
        if (agendamento.getSerieFixaId() == null || agendamento.getSerieFixaId().isBlank()) {
            return false;
        }
        return podeGerenciarAgendamentoDeOutros(usuarioLogado)
                || isAgendamentoDoUsuario(agendamento, usuarioLogado);
    }

    @Transactional
    public int alterarValorProfissionalSerie(Long agendamentoReferenciaId, BigDecimal novoValorProfissional, Usuario usuarioLogado) {
        if (novoValorProfissional == null || novoValorProfissional.signum() <= 0) {
            throw new RuntimeException("Informe quanto o cliente paga ao profissional (valor maior que zero).");
        }
        Agendamento referencia = repository.findById(agendamentoReferenciaId)
                .orElseThrow(() -> new RuntimeException("Agendamento não encontrado."));
        if (!podeAlterarValorProfissionalSerie(referencia, usuarioLogado)) {
            throw new RuntimeException("Sem permissão para alterar o valor desta série.");
        }

        BigDecimal novoValor = novoValorProfissional.setScale(2, RoundingMode.HALF_UP);
        Usuario profissional = referencia.getProfissional();
        List<Agendamento> serie = listarAgendamentosDaSerie(referencia);
        if (serie.isEmpty()) {
            throw new RuntimeException("Nenhum horário encontrado para esta série.");
        }

        int atualizados = 0;
        for (Agendamento agendamento : serie) {
            if (aplicarNovoValorProfissionalNoAgendamento(agendamento, novoValor, profissional)) {
                repository.save(agendamento);
                atualizados++;
            }
        }
        if (atualizados == 0) {
            throw new RuntimeException("Nenhuma consulta pendente para atualizar. Consultas já pagas não são alteradas.");
        }
        return atualizados;
    }

    private List<Agendamento> listarAgendamentosDaSerie(Agendamento referencia) {
        if (referencia.isMensal()) {
            return listarConsultasMensaisDoCliente(referencia);
        }
        if (referencia.getSerieFixaId() != null && !referencia.getSerieFixaId().isBlank()) {
            return repository.findBySerieFixaIdOrderByDataHoraInicioAsc(referencia.getSerieFixaId());
        }
        return List.of(referencia);
    }

    private List<Agendamento> listarConsultasMensaisDoCliente(Agendamento referencia) {
        if (referencia.getProfissional() == null
                || referencia.getProfissional().getId() == null
                || referencia.getNomeCliente() == null
                || referencia.getNomeCliente().isBlank()) {
            return List.of();
        }
        String cliente = referencia.getNomeCliente().trim();
        Long profissionalId = referencia.getProfissional().getId();
        return repository.findByProfissionalIdOrderByDataHoraInicioAsc(profissionalId).stream()
                .filter(Agendamento::isMensal)
                .filter(agendamento -> mesmoNomeCliente(agendamento, cliente))
                .toList();
    }

    private boolean aplicarNovoValorProfissionalNoAgendamento(
            Agendamento agendamento,
            BigDecimal novoValorProfissional,
            Usuario profissional
    ) {
        if (agendamento == null || TurnoLocacao.isTurno(agendamento.getTurnoLocacao())) {
            return false;
        }
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        BigDecimal valorClinica = agendamento.isIndicacaoDona()
                ? valorConsultaService.calcularTarifaClinicaIndicacao(novoValorProfissional, profissional)
                : agendamento.getValorClinicaCobra();
        if (valorClinica == null) {
            valorClinica = BigDecimal.ZERO;
        }
        BigDecimal liquidoNovo = valorConsultaService.calcularLiquido(novoValorProfissional, valorClinica);
        boolean alterado = !mesmoValorMonetario(agendamento.getValorProfissionalRecebe(), novoValorProfissional)
                || !mesmoValorMonetario(agendamento.getValorLiquidoProfissional(), liquidoNovo)
                || (agendamento.isIndicacaoDona()
                && !mesmoValorMonetario(agendamento.getValorClinicaCobra(), valorClinica));
        if (!alterado) {
            return false;
        }
        agendamento.setValorProfissionalRecebe(novoValorProfissional);
        if (agendamento.isIndicacaoDona()) {
            agendamento.setValorClinicaCobra(valorClinica);
        }
        agendamento.setValorLiquidoProfissional(liquidoNovo);
        return true;
    }

    private static boolean mesmoValorMonetario(BigDecimal atual, BigDecimal novo) {
        if (atual == null && novo == null) {
            return true;
        }
        if (atual == null || novo == null) {
            return false;
        }
        return atual.setScale(2, RoundingMode.HALF_UP).compareTo(novo.setScale(2, RoundingMode.HALF_UP)) == 0;
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
        TurnoLocacao turno = TurnoLocacao.fromCodigo(form.getTurnoLocacao());
        if (turno != null) {
            form.setHorarioAtendimento(turno.getInicio());
        } else if (form.getHorarioAtendimento() == null) {
            throw new RuntimeException("Selecione o horário da consulta.");
        }
        if (normalizarRecorrencia(form) == null) {
            throw new RuntimeException("Selecione um tipo de recorrencia valido.");
        }
        if (form.getNomeCliente() == null || form.getNomeCliente().isBlank()) {
            throw new RuntimeException("Informe o nome do cliente.");
        }
        if (form.getTelefoneCliente() != null && !form.getTelefoneCliente().isBlank()
                && normalizarTelefoneCliente(form.getTelefoneCliente()) == null) {
            throw new RuntimeException("WhatsApp do cliente invalido. Use DDD + numero (ex.: 37998550994).");
        }
    }

    private String normalizarTelefoneCliente(String telefoneBruto) {
        if (telefoneBruto == null || telefoneBruto.isBlank()) {
            return null;
        }
        return WhatsAppNumeroUtil.normalizarDestinatario(telefoneBruto).orElse(null);
    }

    private String normalizarRecorrencia(AgendamentoForm form) {
        if (form.getRecorrencia() != null && !form.getRecorrencia().isBlank()) {
            return switch (form.getRecorrencia().toUpperCase()) {
                case RECORRENCIA_AVULSO, RECORRENCIA_SEMANAL, RECORRENCIA_QUINZENAL, RECORRENCIA_MENSAL ->
                        form.getRecorrencia().toUpperCase(Locale.ROOT);
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

    private LocalDateTime calcularInicioOcorrenciaSerie(LocalDateTime inicio, String recorrencia, int indice) {
        return inicio.plusWeeks((long) indice * obterSaltoSemanas(recorrencia));
    }

    private LocalDateTime calcularFimOcorrenciaSerie(LocalDateTime fim, String recorrencia, int indice) {
        return fim.plusWeeks((long) indice * obterSaltoSemanas(recorrencia));
    }

    private LocalDateTime avancarInicioSerie(LocalDateTime inicio, String recorrencia) {
        return inicio.plusWeeks(obterSaltoSemanas(recorrencia));
    }

    private int obterSaltoSemanas(String recorrencia) {
        if (RECORRENCIA_QUINZENAL.equals(recorrencia)) {
            return 2;
        }
        return 1;
    }

    private int obterQuantidadeRepeticoes(String recorrencia) {
        if (isRecorrenciaUnica(recorrencia)) {
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
        if (agendamento.isMensal()) {
            return RECORRENCIA_MENSAL;
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

    private boolean isRecorrenciaUnica(String recorrencia) {
        return RECORRENCIA_AVULSO.equals(recorrencia) || RECORRENCIA_MENSAL.equals(recorrencia);
    }

    private boolean isRecorrenciaComSerie(String recorrencia) {
        return RECORRENCIA_SEMANAL.equals(recorrencia) || RECORRENCIA_QUINZENAL.equals(recorrencia);
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
        novo.setTurnoLocacao(modelo.getTurnoLocacao());
        valorConsultaService.copiarValoresOcorrenciaSerie(novo, modelo, modelo.getSala(), recorrencia);
        return novo;
    }

    private boolean podeGerenciarAgendamentoDeOutros(Usuario usuarioLogado) {
        return authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado);
    }

    private void validarPermissaoCancelamento(Agendamento agendamento, Usuario usuarioLogado) {
        if (podeGerenciarAgendamentoDeOutros(usuarioLogado)) {
            validarAgendamentoParaAcaoGestao(agendamento);
            return;
        }
        if (!isAgendamentoDoUsuario(agendamento, usuarioLogado)) {
            throw new RuntimeException("Você só pode cancelar os seus próprios agendamentos.");
        }
        String recorrencia = recorrenciaDoAgendamento(agendamento);
        if (RECORRENCIA_AVULSO.equals(recorrencia)) {
            if (pagamentoConsultaService.consultaJaFoiPaga(agendamento)) {
                throw new RuntimeException(
                        "Não é possível cancelar um horário já pago. Locação de sala sem reembolso."
                );
            }
            validarAgendamentoParaAcaoGestao(agendamento);
            return;
        }
        if (pagamentoConsultaService.consultaJaFoiPaga(agendamento)) {
            throw new RuntimeException(
                    "Não é possível cancelar um horário já pago. Locação de sala sem reembolso."
            );
        }
        if (!respeitaAntecedenciaMinimaCancelamento(agendamento)) {
            throw new RuntimeException(
                    "Cancelamento permitido somente com mais de 24 horas de antecedência."
            );
        }
        validarAgendamentoParaAcaoGestao(agendamento);
    }

    private boolean respeitaAntecedenciaMinimaCancelamento(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return false;
        }
        LocalDateTime limite = LocalDateTime.now().plusHours(ANTECEDENCIA_MINIMA_CANCELAMENTO_HORAS);
        return agendamento.getDataHoraInicio().isAfter(limite);
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
        validarConflitos(sala, profissional, usuarioLogado, inicio, fim, fixo, indiceSemana, null, List.of());
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
        validarConflitos(sala, profissional, usuarioLogado, inicio, fim, fixo, indiceSemana, ignorarAgendamentoId, List.of());
    }

    private void validarConflitos(
            Sala sala,
            Usuario profissional,
            Usuario usuarioLogado,
            LocalDateTime inicio,
            LocalDateTime fim,
            boolean fixo,
            int indiceSemana,
            Long ignorarAgendamentoId,
            List<Agendamento> pendentesMesmoSalvamento
    ) {
        validarConflitosComPendentes(pendentesMesmoSalvamento, sala, profissional, usuarioLogado, inicio, fim);
        Long idIgnorado = ignorarAgendamentoId != null ? ignorarAgendamentoId : -1L;
        resolverConflitoProfissionalNoHorario(profissional.getId(), inicio, fim, idIgnorado)
                .ifPresent(conflito -> {
                    throw conflitoMensagem(
                            mensagemConflitoProfissional(profissional, conflito, usuarioLogado, sala),
                            inicio,
                            fixo,
                            indiceSemana
                    );
                });
        resolverConflitoSalaNoHorario(sala.getId(), inicio, fim, idIgnorado)
                .ifPresent(conflito -> {
                    throw new RuntimeException(mensagemConflitoSala(sala, inicio, conflito));
                });
    }

    private void validarConflitosComPendentes(
            List<Agendamento> pendentesMesmoSalvamento,
            Sala sala,
            Usuario profissional,
            Usuario usuarioLogado,
            LocalDateTime inicio,
            LocalDateTime fim
    ) {
        if (pendentesMesmoSalvamento == null || pendentesMesmoSalvamento.isEmpty()) {
            return;
        }
        for (Agendamento pendente : pendentesMesmoSalvamento) {
            if (!intervalosSobrepoem(
                    resolverInicioAgendamento(pendente),
                    resolverFimAgendamento(pendente),
                    inicio,
                    fim
            )) {
                continue;
            }
            if (pendente.getProfissional() != null
                    && profissional.getId() != null
                    && profissional.getId().equals(pendente.getProfissional().getId())) {
                throw new RuntimeException(
                        mensagemConflitoProfissional(profissional, pendente, usuarioLogado, sala)
                                + " Conflito com outra data desta mesma serie que voce esta salvando."
                );
            }
            if (pendente.getSala() != null
                    && sala.getId() != null
                    && sala.getId().equals(pendente.getSala().getId())) {
                throw new RuntimeException(mensagemConflitoSala(sala, inicio, pendente));
            }
        }
    }

    private Optional<Agendamento> resolverConflitoProfissionalNoHorario(
            Long profissionalId,
            LocalDateTime inicio,
            LocalDateTime fim,
            Long ignorarAgendamentoId
    ) {
        return repository.findCandidatosConflitoProfissionalNoHorario(
                        profissionalId,
                        inicio,
                        fim,
                        ignorarAgendamentoId != null ? ignorarAgendamentoId : -1L,
                        LocalDateTime.now()
                )
                .stream()
                .filter(candidato -> intervalosSobrepoem(
                        resolverInicioAgendamento(candidato),
                        resolverFimAgendamento(candidato),
                        inicio,
                        fim
                ))
                .filter(pagamentoConsultaService::agendamentoOcupaHorarioParaNovaReserva)
                .findFirst();
    }

    private Optional<Agendamento> resolverConflitoSalaNoHorario(
            Long salaId,
            LocalDateTime inicio,
            LocalDateTime fim,
            Long ignorarAgendamentoId
    ) {
        return repository.findCandidatosConflitoSalaNoHorario(
                        salaId,
                        inicio,
                        fim,
                        ignorarAgendamentoId != null ? ignorarAgendamentoId : -1L,
                        LocalDateTime.now()
                )
                .stream()
                .filter(candidato -> intervalosSobrepoem(
                        resolverInicioAgendamento(candidato),
                        resolverFimAgendamento(candidato),
                        inicio,
                        fim
                ))
                .filter(pagamentoConsultaService::agendamentoOcupaHorarioParaNovaReserva)
                .findFirst();
    }

    private LocalDateTime resolverInicioAgendamento(Agendamento agendamento) {
        return inicioHoraCheia(agendamento.getDataHoraInicio());
    }

    private LocalDateTime resolverFimAgendamento(Agendamento agendamento) {
        if (agendamento.getDataHoraFim() != null) {
            return agendamento.getDataHoraFim();
        }
        return resolverInicioAgendamento(agendamento).plusHours(1);
    }

    private String mensagemConflitoProfissional(
            Usuario profissional,
            Agendamento conflito,
            Usuario usuarioLogado,
            Sala salaDestino
    ) {
        String salaConflito = conflito.getSala() != null && conflito.getSala().getNome() != null
                ? conflito.getSala().getNome()
                : "outra sala";

        boolean agendandoParaSiMesmo = usuarioLogado != null
                && profissional.getId() != null
                && profissional.getId().equals(usuarioLogado.getId());

        String detalhe = detalheAgendamentoConflitante(conflito);
        boolean salaDestinoLivre = salaDestino != null
                && salaDestino.getId() != null
                && conflito.getSala() != null
                && conflito.getSala().getId() != null
                && !salaDestino.getId().equals(conflito.getSala().getId());
        String nomeSalaDestino = salaDestino != null && salaDestino.getNome() != null
                ? salaDestino.getNome()
                : "esta sala";

        if (agendandoParaSiMesmo) {
            if (salaDestinoLivre) {
                return "A " + nomeSalaDestino + " está livre neste horário, mas você já tem consulta na "
                        + salaConflito + "." + detalhe;
            }
            return "Você já tem um agendamento nesse horário na " + salaConflito + "." + detalhe;
        }

        String nomeProfissional = profissional.getNome() != null && !profissional.getNome().isBlank()
                ? profissional.getNome()
                : "Este profissional";
        if (salaDestinoLivre) {
            return nomeProfissional + " já tem consulta na " + salaConflito
                    + " neste horário (a " + nomeSalaDestino + " está livre)." + detalhe;
        }
        return nomeProfissional + " já tem um agendamento nesse horário na " + salaConflito + "." + detalhe;
    }

    private String detalheAgendamentoConflitante(Agendamento conflito) {
        if (conflito == null || conflito.getDataHoraInicio() == null) {
            return "";
        }
        String cliente = conflito.getNomeCliente() != null ? conflito.getNomeCliente().trim() : "—";
        String dataHora = conflito.getDataHoraInicio()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        String status = conflito.getStatusPagamento() != null
                ? conflito.getStatusPagamento().name()
                : "sem status";
        return " Conflito com: " + cliente + " em " + dataHora + " (" + status + ").";
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
        return mensagemConflitoSala(sala, inicio, null);
    }

    private String mensagemConflitoSala(Sala sala, LocalDateTime inicio, Agendamento conflito) {
        String nomeSala = sala != null && sala.getNome() != null && !sala.getNome().isBlank()
                ? sala.getNome()
                : "Sala";
        String periodo = inicio.toLocalTime().equals(TurnoLocacao.TURNO_MANHA.getInicio())
                ? "no turno da manhã (08:00 às 13:00)"
                : inicio.toLocalTime().equals(TurnoLocacao.TURNO_TARDE.getInicio())
                        ? "no turno da tarde (13:00 às 18:00)"
                        : "às " + inicio.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        return "Conflito de agenda: a Sala " + nomeSala
                + " já está ocupada em "
                + inicio.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " " + periodo
                + ". Não é possível salvar este horário."
                + detalheAgendamentoConflitante(conflito);
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
        validarHorario(inicio, fim, null);
    }

    private void validarHorario(LocalDateTime inicio, LocalDateTime fim, String turnoLocacao) {
        DayOfWeek diaSemana = inicio.getDayOfWeek();
        if (diaSemana == DayOfWeek.SUNDAY) {
            throw new RuntimeException("A clínica funciona somente de segunda a sábado.");
        }

        LocalDate data = inicio.toLocalDate();
        if (!fim.toLocalDate().equals(data)) {
            throw new RuntimeException("Cada agendamento deve terminar no mesmo dia.");
        }

        TurnoLocacao turno = TurnoLocacao.fromCodigo(turnoLocacao);
        if (turno != null) {
            if (!inicio.toLocalTime().equals(turno.getInicio()) || !fim.toLocalTime().equals(turno.getFim())) {
                throw new RuntimeException("Turno inválido para locação de sala.");
            }
            return;
        }

        if (inicio.getMinute() != 0 || inicio.getSecond() != 0 || inicio.getNano() != 0) {
            throw new RuntimeException("Os agendamentos precisam iniciar em hora cheia.");
        }

        if (inicio.toLocalTime().isBefore(HORA_ABERTURA) || fim.toLocalTime().isAfter(HORA_FECHAMENTO)) {
            throw new RuntimeException("Os atendimentos devem ficar entre 07:00 e 21:00.");
        }
    }

    private IntervaloAtendimento resolverIntervaloAtendimento(AgendamentoForm form) {
        TurnoLocacao turno = TurnoLocacao.fromCodigo(form.getTurnoLocacao());
        if (turno != null) {
            return new IntervaloAtendimento(turno.getInicio(), turno.getFim(), turno.getCodigo());
        }
        LocalTime hora = form.getHorarioAtendimento()
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        return new IntervaloAtendimento(hora, hora.plusHours(1), null);
    }

    private record IntervaloAtendimento(LocalTime inicio, LocalTime fim, String turnoCodigo) {
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

    private List<Agendamento> carregarAgendamentosProfissionalNaSemana(
            Long profissionalId,
            LocalDateTime inicioSemana,
            LocalDateTime fimSemana
    ) {
        if (profissionalId == null) {
            return List.of();
        }
        return repository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                profissionalId,
                inicioSemana,
                fimSemana
        );
    }

    private AgendaGradeCelula resolverCelulaGrade(
            List<Agendamento> agendamentosSemana,
            List<Agendamento> agendamentosProfissionalSemana,
            LocalDate dia,
            LocalTime horario,
            Long salaIdAtual
    ) {
        Agendamento agendamento = agendamentosSemana.stream()
                .filter(item -> pagamentoConsultaService.ocupaVagaNaGrade(item))
                .filter(item -> {
                    if (item.getDataHoraInicio() == null) {
                        return false;
                    }
                    LocalDateTime inicioCelula = LocalDateTime.of(dia, horario);
                    LocalDateTime fimCelula = inicioCelula.plusHours(1);
                    LocalDateTime inicioAg = inicioHoraCheia(item.getDataHoraInicio());
                    LocalDateTime fimAg = item.getDataHoraFim() != null
                            ? item.getDataHoraFim()
                            : inicioAg.plusHours(1);
                    return intervalosSobrepoem(inicioAg, fimAg, inicioCelula, fimCelula);
                })
                .min(Comparator.comparing(Agendamento::getDataHoraInicio))
                .orElse(null);
        AgendaGradeCelula celula = AgendaGradeCelula.resolver(agendamento, dia, horario);
        if (celula != null || salaIdAtual == null || agendamentosProfissionalSemana.isEmpty()) {
            return celula;
        }
        LocalDateTime inicioCelula = LocalDateTime.of(dia, horario);
        LocalDateTime fimCelula = inicioCelula.plusHours(1);
        return resolverConflitoProfissionalEmMemoria(
                agendamentosProfissionalSemana,
                salaIdAtual,
                inicioCelula,
                fimCelula
        )
                .map(conflito -> AgendaGradeCelula.profissionalOcupadoEmOutraSala(
                        conflito.getSala().getNome()
                ))
                .orElse(null);
    }

    private Optional<Agendamento> resolverConflitoProfissionalEmMemoria(
            List<Agendamento> agendamentosProfissional,
            Long salaIdAtual,
            LocalDateTime inicio,
            LocalDateTime fim
    ) {
        return agendamentosProfissional.stream()
                .filter(pagamentoConsultaService::agendamentoOcupaHorarioParaNovaReserva)
                .filter(candidato -> intervalosSobrepoem(
                        resolverInicioAgendamento(candidato),
                        resolverFimAgendamento(candidato),
                        inicio,
                        fim
                ))
                .filter(candidato -> candidato.getSala() != null
                        && candidato.getSala().getId() != null
                        && !candidato.getSala().getId().equals(salaIdAtual))
                .findFirst();
    }

    private boolean intervalosSobrepoem(
            LocalDateTime inicioA,
            LocalDateTime fimA,
            LocalDateTime inicioB,
            LocalDateTime fimB
    ) {
        if (inicioA == null || fimA == null || inicioB == null || fimB == null) {
            return false;
        }
        return inicioA.isBefore(fimB) && fimA.isAfter(inicioB);
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
