package com.clinica.sistema.service;

import com.clinica.sistema.config.InfinitePayProperties;
import com.clinica.sistema.config.PagamentoProperties;
import com.clinica.sistema.dto.LinkPagamentoGerado;
import com.clinica.sistema.dto.PagamentoProfissionalNotificacaoView;
import com.clinica.sistema.dto.ProfissionalBloqueioPagamentoView;
import com.clinica.sistema.exception.HorarioJaReservadoPorOutroProfissionalException;
import com.clinica.sistema.exception.PagamentoWebhookNaoAutorizadoException;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class PagamentoConsultaService {

    private static final String CARGO_PROFISSIONAL = "ROLE_PROFISSIONAL";
    private static final List<PagamentoStatus> STATUS_CANDIDATOS_LIBERACAO = List.of(
            PagamentoStatus.PAGAMENTO_FUTURO,
            PagamentoStatus.AGUARDANDO_PAGAMENTO,
            PagamentoStatus.ESPERANDO_CONFIRMACAO
    );
    private static final DateTimeFormatter FORMATO_DATA_COMPLETA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String URL_MEUS_PAGAMENTOS_DIA = "/agendamentos/meus-pagamentos#pagamentos-pendentes";
    private static final String URL_MEUS_PAGAMENTOS_SEMANA = "/agendamentos/meus-pagamentos#pagamentos-semana";
    private static final String URL_MEUS_PAGAMENTOS_MES = "/agendamentos/meus-pagamentos#pagamentos-mes";

    private final AgendamentoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final InfinitePayService infinitePayService;
    private final AuthService authService;
    private final PagamentoProperties pagamentoProperties;
    private final InfinitePayProperties infinitePayProperties;

    public PagamentoConsultaService(
            AgendamentoRepository repository,
            UsuarioRepository usuarioRepository,
            InfinitePayService infinitePayService,
            AuthService authService,
            PagamentoProperties pagamentoProperties,
            InfinitePayProperties infinitePayProperties
    ) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.infinitePayService = infinitePayService;
        this.authService = authService;
        this.pagamentoProperties = pagamentoProperties;
        this.infinitePayProperties = infinitePayProperties;
    }

    public void configurarPagamentosAoSalvar(
            List<Agendamento> novosAgendamentos,
            Usuario profissional,
            Usuario usuarioLogado
    ) {
        if (novosAgendamentos == null || novosAgendamentos.isEmpty()) {
            return;
        }
        for (Agendamento agendamento : novosAgendamentos) {
            definirReferenciasCobrancaSeNecessario(agendamento);
        }
        if (authService.profissionalIgnoraValoresEPagamento(profissional)) {
            for (Agendamento agendamento : novosAgendamentos) {
                agendamento.setStatusPagamento(PagamentoStatus.PAGO);
            }
            return;
        }
        if (isAgendadoPorGestorParaOutroProfissional(profissional, usuarioLogado)) {
            for (int i = 0; i < novosAgendamentos.size(); i++) {
                Agendamento agendamento = novosAgendamentos.get(i);
                if (i == 0) {
                    marcarComoPagoPorAcordoGestor(agendamento);
                } else {
                    aplicarStatusPagamentoSerie(agendamento, profissional);
                }
            }
            return;
        }
        if (resolverPeriodicidade(profissional) != PeriodicidadePagamento.DIARIO) {
            for (Agendamento agendamento : novosAgendamentos) {
                agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
            }
            return;
        }
        for (int i = 0; i < novosAgendamentos.size(); i++) {
            Agendamento agendamento = novosAgendamentos.get(i);
            if (i == 0) {
                iniciarConfirmacaoPagamento(agendamento);
            } else {
                agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
            }
        }
    }

    public boolean isAgendadoPorGestorParaOutroProfissional(Usuario profissional, Usuario usuarioLogado) {
        if (profissional == null || usuarioLogado == null) {
            return false;
        }
        if (profissional.getId() == null || usuarioLogado.getId() == null) {
            return false;
        }
        if (profissional.getId().equals(usuarioLogado.getId())) {
            return false;
        }
        return authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado);
    }

    private void marcarComoPagoPorAcordoGestor(Agendamento agendamento) {
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        agendamento.setDataPagamento(LocalDateTime.now());
        limparDadosPagamentoEmAberto(agendamento);
        agendamento.setLiberadoEm(null);
    }

    private void aplicarStatusPagamentoSerie(Agendamento agendamento, Usuario profissional) {
        if (authService.profissionalIgnoraValoresEPagamento(profissional)) {
            agendamento.setStatusPagamento(PagamentoStatus.PAGO);
            return;
        }
        if (resolverPeriodicidade(profissional) != PeriodicidadePagamento.DIARIO) {
            agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
            return;
        }
        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
    }

    public void configurarPagamentoNovaOcorrenciaSerie(Agendamento agendamento) {
        if (agendamento.getStatusPagamento() != null) {
            return;
        }
        definirReferenciasCobrancaSeNecessario(agendamento);
        if (agendamento.getProfissional() != null
                && authService.profissionalIgnoraValoresEPagamento(agendamento.getProfissional())) {
            agendamento.setStatusPagamento(PagamentoStatus.PAGO);
            return;
        }
        if (agendamento.getProfissional() != null
                && resolverPeriodicidade(agendamento.getProfissional()) != PeriodicidadePagamento.DIARIO) {
            agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
            return;
        }
        if (deveAbrirPagamentoAgora(agendamento)) {
            agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        } else {
            agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        }
    }

    @Transactional
    public void processarPagamentosPendentes() {
        expirarPagamentosVencidos();
        abrirJanelasPagamentoVespera();
        liberarVagasPorFaltaPagamento();
    }

    @Transactional
    public int abrirJanelasPagamentoVespera() {
        LocalDateTime desde = LocalDate.now().minusDays(1).atStartOfDay();
        List<Agendamento> candidatos = repository.findByStatusPagamentoInAndDataHoraInicioGreaterThanEqual(
                List.of(PagamentoStatus.PAGAMENTO_FUTURO),
                desde
        );
        int atualizados = 0;
        for (Agendamento agendamento : candidatos) {
            if (!profissionalUsaPagamentoDiario(agendamento)) {
                continue;
            }
            if (!deveAbrirPagamentoAgora(agendamento)) {
                continue;
            }
            if (agendamento.getProfissional() != null
                    && authService.profissionalIgnoraValoresEPagamento(agendamento.getProfissional())) {
                continue;
            }
            agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
            repository.save(agendamento);
            atualizados++;
        }
        return atualizados;
    }

    @Transactional
    public int liberarVagasPorFaltaPagamento() {
        LocalDateTime desde = LocalDate.now().minusDays(1).atStartOfDay();
        List<Agendamento> candidatos = repository.findByStatusPagamentoInAndDataHoraInicioGreaterThanEqual(
                STATUS_CANDIDATOS_LIBERACAO,
                desde
        );
        int liberados = 0;
        for (Agendamento agendamento : candidatos) {
            if (!profissionalUsaPagamentoDiario(agendamento)) {
                continue;
            }
            if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
                continue;
            }
            if (PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(agendamento.getStatusPagamento())) {
                continue;
            }
            if (agendamento.getProfissional() != null
                    && authService.profissionalIgnoraValoresEPagamento(agendamento.getProfissional())) {
                continue;
            }
            if (!passouPrazoPagamentoVespera(agendamento)) {
                continue;
            }
            liberarPorFaltaPagamento(agendamento);
            liberados++;
        }
        return liberados;
    }

    @Transactional
    public int expirarPagamentosVencidos() {
        LocalDateTime agora = LocalDateTime.now();
        List<Agendamento> expirados = repository.findByStatusPagamentoAndPagamentoExpiraEmBefore(
                PagamentoStatus.ESPERANDO_CONFIRMACAO,
                agora
        );
        int removidos = 0;
        for (Agendamento agendamento : expirados) {
            removidos += removerAgendamentoExpirado(agendamento);
        }
        return removidos;
    }

    @Transactional
    public Agendamento gerarLinkPagamento(Long agendamentoId, Usuario usuarioLogado) {
        return pagarAgora(agendamentoId, usuarioLogado);
    }

    @Transactional
    public Agendamento pagarAgora(Long agendamentoId, Usuario usuarioLogado) {
        Agendamento agendamento = buscarComPermissao(agendamentoId, usuarioLogado);
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException("Esta consulta já está paga.");
        }
        if (vagaPreenchidaPorOutroProfissional(agendamento)) {
            throw new HorarioJaReservadoPorOutroProfissionalException(formatarDetalheHorario(agendamento));
        }
        validarRecuperacaoPagamento(agendamento);
        if (!podePagarAgora(agendamento) && !agendamento.possuiQrPagamentoAtivo()) {
            throw new RuntimeException("Esta consulta não está disponível para pagamento.");
        }
        if (agendamento.possuiQrPagamentoAtivo()) {
            return agendamento;
        }
        iniciarConfirmacaoPagamento(agendamento);
        return repository.save(agendamento);
    }

    public void validarAutenticacaoWebhook(String secretRecebido) {
        String secretConfigurado = pagamentoProperties.getWebhookSecret();
        if (secretConfigurado == null || secretConfigurado.isBlank()) {
            if (infinitePayProperties.isModoTeste()) {
                return;
            }
            throw new PagamentoWebhookNaoAutorizadoException();
        }
        if (secretRecebido == null || !secretConfigurado.equals(secretRecebido.trim())) {
            throw new PagamentoWebhookNaoAutorizadoException();
        }
    }

    @Transactional
    public Agendamento confirmarPagamentoPorOrderNsu(String orderNsu) {
        return confirmarPagamentoPorOrderNsu(orderNsu, false);
    }

    @Transactional
    public Agendamento confirmarPagamentoPorOrderNsuModoTeste(String orderNsu) {
        return confirmarPagamentoPorOrderNsu(orderNsu, true);
    }

    @Transactional
    private Agendamento confirmarPagamentoPorOrderNsu(String orderNsu, boolean modoTeste) {
        if (orderNsu == null || orderNsu.isBlank()) {
            throw new RuntimeException("Pedido de pagamento inválido.");
        }
        List<Agendamento> agendamentos = repository.findAllByPagamentoOrderNsuOrderByDataHoraInicioAsc(orderNsu);
        if (agendamentos.isEmpty()) {
            throw new RuntimeException("Pedido de pagamento não encontrado.");
        }
        Agendamento ultimoPago = null;
        for (Agendamento agendamento : agendamentos) {
            if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
                ultimoPago = agendamento;
                continue;
            }
            validarAntesConfirmarPagamento(agendamento, orderNsu, modoTeste);
            ultimoPago = marcarComoPago(agendamento);
        }
        if (ultimoPago == null) {
            throw new RuntimeException("Nenhuma consulta pendente para confirmar neste pedido.");
        }
        return ultimoPago;
    }

    @Transactional
    public Agendamento simularPagamento(Long agendamentoId, Usuario usuarioLogado) {
        Agendamento agendamento = buscarComPermissao(agendamentoId, usuarioLogado);
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return agendamento;
        }
        if (agendamento.getPagamentoOrderNsu() == null || agendamento.getPagamentoOrderNsu().isBlank()) {
            iniciarConfirmacaoPagamento(agendamento);
        }
        return marcarComoPago(agendamento);
    }

    public List<Agendamento> listarAguardandoConfirmacao(Usuario usuarioLogado, boolean verTodos) {
        LocalDateTime agora = LocalDateTime.now();
        if (verTodos && (authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado))) {
            return repository.findByStatusPagamentoAndPagamentoExpiraEmAfterOrderByPagamentoExpiraEmAsc(
                    PagamentoStatus.ESPERANDO_CONFIRMACAO,
                    agora
            );
        }
        return repository.findByProfissionalIdAndStatusPagamentoAndPagamentoExpiraEmAfterOrderByPagamentoExpiraEmAsc(
                usuarioLogado.getId(),
                PagamentoStatus.ESPERANDO_CONFIRMACAO,
                agora
        );
    }

    public boolean deveAbrirPagamentoAgora(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return false;
        }
        LocalDate consulta = agendamento.getDataHoraInicio().toLocalDate();
        LocalDate diaLimitePagamento = consulta.minusDays(1);
        return !LocalDate.now().isBefore(diaLimitePagamento);
    }

    public List<Agendamento> listarDisponiveisParaPagarAntecipado(Usuario usuarioLogado, boolean verTodos) {
        LocalDateTime agora = LocalDateTime.now();
        List<Agendamento> candidatos;
        if (verTodos && (authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado))) {
            candidatos = repository.findByDataHoraInicioGreaterThanOrderByDataHoraInicioAsc(agora);
        } else {
            candidatos = repository.findByProfissionalIdAndDataHoraInicioGreaterThanOrderByDataHoraInicioAsc(
                    usuarioLogado.getId(),
                    agora
            );
        }
        return candidatos.stream()
                .filter(this::podePagarAgora)
                .limit(16)
                .toList();
    }

    public PeriodicidadePagamento resolverPeriodicidade(Usuario usuario) {
        if (usuario == null || usuario.getPeriodicidadePagamento() == null) {
            return PeriodicidadePagamento.DIARIO;
        }
        return usuario.getPeriodicidadePagamento();
    }

    @Transactional
    public int migrarAgendamentosAoAlterarPeriodicidade(
            Usuario profissional,
            PeriodicidadePagamento periodicidadeAnterior,
            PeriodicidadePagamento periodicidadeNova
    ) {
        if (profissional == null || profissional.getId() == null || periodicidadeNova == null) {
            return 0;
        }
        if (periodicidadeNova == periodicidadeAnterior) {
            return 0;
        }
        if (authService.profissionalIgnoraValoresEPagamento(profissional)) {
            return 0;
        }

        LocalDateTime agora = LocalDateTime.now();
        List<Agendamento> agendamentos = repository.findByProfissionalIdAndDataHoraInicioGreaterThanOrderByDataHoraInicioAsc(
                profissional.getId(),
                agora
        );

        int migrados = 0;
        for (Agendamento agendamento : agendamentos) {
            if (!deveMigrarAoAlterarPeriodicidade(agendamento)) {
                continue;
            }
            sincronizarPeriodicidadeProfissionalNoAgendamento(agendamento, profissional, periodicidadeNova);
            aplicarPeriodicidadeNoAgendamento(agendamento, periodicidadeNova);
            repository.save(agendamento);
            migrados++;
        }
        return migrados;
    }

    public String rotuloPeriodicidade(PeriodicidadePagamento periodicidade) {
        if (periodicidade == null) {
            return PeriodicidadePagamento.DIARIO.getRotulo();
        }
        return periodicidade.getRotulo();
    }

    public boolean estaEmJanelaPagamentoSemanal() {
        DayOfWeek dia = LocalDate.now().getDayOfWeek();
        return dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
    }

    public List<Agendamento> listarConsultasAdiantamentoSemanaAtual(Usuario usuarioLogado) {
        if (usuarioLogado == null
                || authService.isAdmin(usuarioLogado)
                || authService.isDonaClinica(usuarioLogado)
                || authService.profissionalIgnoraValoresEPagamento(usuarioLogado)) {
            return Collections.emptyList();
        }

        PeriodicidadePagamento periodicidade = resolverPeriodicidade(usuarioLogado);
        if (periodicidade == PeriodicidadePagamento.SEMANAL) {
            if (!estaEmJanelaPagamentoSemanal()) {
                return Collections.emptyList();
            }
            return listarConsultasNaoPagasNoPeriodo(
                    usuarioLogado,
                    resolverSemanaCorrenteParaCobranca(LocalDate.now())
            );
        }
        if (periodicidade == PeriodicidadePagamento.MENSAL) {
            return Collections.emptyList();
        }

        PeriodoSemanaPagamento periodo = resolverPeriodoSemanaPagamento(LocalDate.now());
        LocalDate inicioSemana = periodo.inicio();
        LocalDate fimSemana = periodo.fim();

        return repository.findByProfissionalIdOrderByDataHoraInicioAsc(usuarioLogado.getId()).stream()
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !consultaJaFoiPaga(agendamento))
                .filter(agendamento -> {
                    LocalDate dataConsulta = agendamento.getDataHoraInicio().toLocalDate();
                    return !dataConsulta.isBefore(inicioSemana) && !dataConsulta.isAfter(fimSemana);
                })
                .filter(this::podeAdiantarPagamentoSemana)
                .toList();
    }

    public List<Agendamento> listarConsultasPagamentoMensal(Usuario usuarioLogado) {
        if (usuarioLogado == null
                || authService.isAdmin(usuarioLogado)
                || authService.isDonaClinica(usuarioLogado)
                || authService.profissionalIgnoraValoresEPagamento(usuarioLogado)
                || resolverPeriodicidade(usuarioLogado) != PeriodicidadePagamento.MENSAL) {
            return Collections.emptyList();
        }
        return listarConsultasNaoPagasNoMes(usuarioLogado, YearMonth.from(LocalDate.now()).minusMonths(1));
    }

    public String rotuloMesPagamentoPendente() {
        YearMonth mesReferencia = YearMonth.from(LocalDate.now()).minusMonths(1);
        return mesReferencia.format(DateTimeFormatter.ofPattern("MM/yyyy"));
    }

    public boolean exibePagamentoMensalAgora(Usuario usuarioLogado) {
        return resolverPeriodicidade(usuarioLogado) == PeriodicidadePagamento.MENSAL;
    }

    public boolean estaEmJanelaPagamentoMensal() {
        int dia = LocalDate.now().getDayOfMonth();
        return dia >= 1 && dia <= 10;
    }

    public String rotuloJanelaPagamentoMensalAtual() {
        return "01 ao 10/" + LocalDate.now().format(DateTimeFormatter.ofPattern("MM"));
    }

    public BigDecimal calcularTotalTaxaPix(List<Agendamento> agendamentos) {
        if (agendamentos == null || agendamentos.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return agendamentos.stream()
                .map(infinitePayService::resolverValorTaxaClinica)
                .filter(valor -> valor != null && valor.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String formatarTotalTaxaPix(List<Agendamento> agendamentos) {
        return com.clinica.sistema.util.MoedaBrasilUtil.formatar(calcularTotalTaxaPix(agendamentos));
    }

    public String rotuloPeriodoSemanaAtual() {
        PeriodoSemanaPagamento periodo = resolverPeriodoSemanaPagamento(LocalDate.now());
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM");
        String intervalo = periodo.inicio().format(formato) + " a " + periodo.fim().format(formato);
        if (adiantamentoSemanaEhProxima()) {
            return "Proxima semana: " + intervalo;
        }
        return intervalo;
    }

    public boolean adiantamentoSemanaEhProxima() {
        return LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    PeriodoSemanaPagamento resolverPeriodoSemanaPagamento(LocalDate referencia) {
        LocalDate inicio;
        if (referencia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            inicio = referencia.plusDays(1);
        } else {
            inicio = referencia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        return new PeriodoSemanaPagamento(inicio, inicio.plusDays(6));
    }

    record PeriodoSemanaPagamento(LocalDate inicio, LocalDate fim) {
    }

    @Transactional
    public String gerarPagamentoUnicoSemanaAtual(Usuario usuarioLogado) {
        PeriodicidadePagamento periodicidade = resolverPeriodicidade(usuarioLogado);
        if (periodicidade == PeriodicidadePagamento.SEMANAL && !estaEmJanelaPagamentoSemanal()) {
            throw new RuntimeException("Pagamento semanal disponível apenas sábado e domingo.");
        }
        List<Agendamento> consultas = listarConsultasAdiantamentoSemanaAtual(usuarioLogado);
        if (consultas.isEmpty()) {
            throw new RuntimeException("Não há consultas da semana disponíveis para pagamento.");
        }
        for (Agendamento consulta : consultas) {
            if (vagaPreenchidaPorOutroProfissional(consulta)) {
                throw new HorarioJaReservadoPorOutroProfissionalException(formatarDetalheHorario(consulta));
            }
        }
        LinkPagamentoGerado link = infinitePayService.gerarLinkPagamentoSemana(consultas);
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime expiraEm = agora.plusMinutes(pagamentoProperties.getPrazoConfirmacaoMinutos());
        for (Agendamento consulta : consultas) {
            aplicarValoresTaxaAntesPagamento(consulta);
            consulta.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
            consulta.setPagamentoOrderNsu(link.getOrderNsu());
            consulta.setPagamentoLink(link.getLinkPagamento());
            consulta.setPagamentoSlug(link.getSlug());
            consulta.setPagamentoIniciadoEm(agora);
            consulta.setPagamentoExpiraEm(expiraEm);
            repository.save(consulta);
        }
        return link.getOrderNsu();
    }

    @Transactional
    public String gerarPagamentoUnicoMesAnterior(Usuario usuarioLogado) {
        if (resolverPeriodicidade(usuarioLogado) != PeriodicidadePagamento.MENSAL) {
            throw new RuntimeException("Pagamento mensal não se aplica a este profissional.");
        }
        List<Agendamento> consultas = listarConsultasPagamentoMensal(usuarioLogado);
        if (consultas.isEmpty()) {
            throw new RuntimeException("Não há consultas do mês anterior pendentes de pagamento.");
        }
        for (Agendamento consulta : consultas) {
            if (vagaPreenchidaPorOutroProfissional(consulta)) {
                throw new HorarioJaReservadoPorOutroProfissionalException(formatarDetalheHorario(consulta));
            }
        }
        LinkPagamentoGerado link = infinitePayService.gerarLinkPagamentoMes(consultas);
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime expiraEm = agora.plusMinutes(pagamentoProperties.getPrazoConfirmacaoMinutos());
        for (Agendamento consulta : consultas) {
            aplicarValoresTaxaAntesPagamento(consulta);
            consulta.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
            consulta.setPagamentoOrderNsu(link.getOrderNsu());
            consulta.setPagamentoLink(link.getLinkPagamento());
            consulta.setPagamentoSlug(link.getSlug());
            consulta.setPagamentoIniciadoEm(agora);
            consulta.setPagamentoExpiraEm(expiraEm);
            repository.save(consulta);
        }
        return link.getOrderNsu();
    }

    public List<Agendamento> listarAgendamentosPorOrderNsu(String orderNsu, Usuario usuarioLogado) {
        if (orderNsu == null || orderNsu.isBlank()) {
            throw new RuntimeException("Pedido de pagamento inválido.");
        }
        List<Agendamento> agendamentos = repository.findAllByPagamentoOrderNsuOrderByDataHoraInicioAsc(orderNsu);
        if (agendamentos.isEmpty()) {
            throw new RuntimeException("Pedido de pagamento não encontrado.");
        }
        for (Agendamento agendamento : agendamentos) {
            validarAcessoPagamento(agendamento, usuarioLogado);
        }
        return agendamentos;
    }

    public boolean isPedidoPagamentoSemana(String orderNsu) {
        return orderNsu != null && orderNsu.startsWith("sem-");
    }

    public boolean isPedidoPagamentoDia(String orderNsu) {
        return orderNsu != null && orderNsu.startsWith("dia-");
    }

    public boolean isPedidoPagamentoMes(String orderNsu) {
        return orderNsu != null && orderNsu.startsWith("mes-");
    }

    public boolean isPedidoPagamentoLote(String orderNsu) {
        return isPedidoPagamentoSemana(orderNsu)
                || isPedidoPagamentoDia(orderNsu)
                || isPedidoPagamentoMes(orderNsu);
    }

    public LocalDate dataProximoDiaPagamentoPendente() {
        return LocalDate.now().plusDays(1);
    }

    public String rotuloProximoDiaPagamentoPendente() {
        return dataProximoDiaPagamentoPendente().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    public List<Agendamento> listarPagamentosPendentesProximoDia(Usuario usuarioLogado) {
        if (resolverPeriodicidade(usuarioLogado) != PeriodicidadePagamento.DIARIO) {
            return Collections.emptyList();
        }
        LocalDate hoje = LocalDate.now();
        LocalDate amanha = dataProximoDiaPagamentoPendente();
        return listarPendenciasDiariasParaBloqueio(usuarioLogado).stream()
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> exibirNaListaPagamentosPendentes(agendamento, hoje, amanha))
                .sorted(java.util.Comparator.comparing(Agendamento::getDataHoraInicio))
                .toList();
    }

    private boolean exibirNaListaPagamentosPendentes(Agendamento agendamento, LocalDate hoje, LocalDate amanha) {
        LocalDate dataConsulta = agendamento.getDataHoraInicio().toLocalDate();
        if (dataConsulta.equals(amanha)) {
            return true;
        }
        return dataConsulta.equals(hoje) && aindaPodePagarNaListaPendentes(agendamento);
    }

    /**
     * Consultas de hoje permanecem na aba de pendentes ate o horario de inicio,
     * enquanto a vaga nao for ocupada por outro profissional.
     */
    private boolean aindaPodePagarNaListaPendentes(Agendamento agendamento) {
        if (!dentroJanelaRecuperacao(agendamento)) {
            return false;
        }
        if (PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(agendamento.getStatusPagamento())) {
            return !outroProfissionalOcupouVaga(agendamento);
        }
        return true;
    }

    @Transactional
    public String gerarPagamentoUnicoPendentesSelecionados(Usuario usuarioLogado, List<Long> agendamentoIds) {
        if (agendamentoIds == null || agendamentoIds.isEmpty()) {
            throw new RuntimeException("Selecione ao menos uma consulta para pagar.");
        }
        List<Agendamento> permitidas = listarPagamentosPendentesProximoDia(usuarioLogado);
        java.util.Set<Long> idsPermitidos = permitidas.stream()
                .map(Agendamento::getId)
                .collect(java.util.stream.Collectors.toSet());

        List<Agendamento> selecionadas = new java.util.ArrayList<>();
        for (Long agendamentoId : agendamentoIds) {
            if (agendamentoId == null || !idsPermitidos.contains(agendamentoId)) {
                throw new RuntimeException("Consulta inválida ou não disponível para pagamento do próximo dia.");
            }
            Agendamento consulta = buscarComPermissao(agendamentoId, usuarioLogado);
            if (consultaJaFoiPaga(consulta)) {
                throw new RuntimeException("Uma das consultas selecionadas já está paga.");
            }
            selecionadas.add(consulta);
        }

        for (Agendamento consulta : selecionadas) {
            if (vagaPreenchidaPorOutroProfissional(consulta)) {
                throw new HorarioJaReservadoPorOutroProfissionalException(formatarDetalheHorario(consulta));
            }
        }

        LinkPagamentoGerado link = infinitePayService.gerarLinkPagamentoDia(selecionadas);
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime expiraEm = agora.plusMinutes(pagamentoProperties.getPrazoConfirmacaoMinutos());
        for (Agendamento consulta : selecionadas) {
            aplicarValoresTaxaAntesPagamento(consulta);
            consulta.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
            consulta.setPagamentoOrderNsu(link.getOrderNsu());
            consulta.setPagamentoLink(link.getLinkPagamento());
            consulta.setPagamentoSlug(link.getSlug());
            consulta.setPagamentoIniciadoEm(agora);
            consulta.setPagamentoExpiraEm(expiraEm);
            repository.save(consulta);
        }
        return link.getOrderNsu();
    }

    public List<Agendamento> listarPendenciasObrigatoriasParaBloqueio(Usuario usuarioLogado) {
        if (usuarioLogado == null
                || authService.isAdmin(usuarioLogado)
                || authService.isDonaClinica(usuarioLogado)
                || authService.profissionalIgnoraValoresEPagamento(usuarioLogado)) {
            return Collections.emptyList();
        }

        if (temQrPagamentoAtivo(usuarioLogado)) {
            return listarConsultasComQrAtivo(usuarioLogado);
        }

        return switch (resolverPeriodicidade(usuarioLogado)) {
            case DIARIO -> listarPendenciasDiariasParaBloqueio(usuarioLogado);
            case SEMANAL -> listarPendenciasSemanaisParaBloqueio(usuarioLogado);
            case MENSAL -> listarPendenciasMensaisParaBloqueio(usuarioLogado);
        };
    }

    private List<Agendamento> listarPendenciasDiariasParaBloqueio(Usuario usuarioLogado) {
        return repository.findByProfissionalIdOrderByDataHoraInicioAsc(usuarioLogado.getId()).stream()
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !consultaJaFoiPaga(agendamento))
                .filter(this::aindaExigeBloqueioPorPagamentoDiario)
                .toList();
    }

    private List<Agendamento> listarPendenciasSemanaisParaBloqueio(Usuario usuarioLogado) {
        if (!bloqueadoPorPagamentoSemanal(usuarioLogado)) {
            return Collections.emptyList();
        }
        PeriodoSemanaPagamento semanaAnterior = resolverSemanaAnteriorEncerrada(LocalDate.now());
        if (semanaAnterior == null) {
            return Collections.emptyList();
        }
        return listarConsultasNaoPagasNoPeriodo(usuarioLogado, semanaAnterior);
    }

    private List<Agendamento> listarPendenciasMensaisParaBloqueio(Usuario usuarioLogado) {
        if (!bloqueadoPorPagamentoMensal(usuarioLogado)) {
            return Collections.emptyList();
        }
        return listarConsultasNaoPagasNoMes(
                usuarioLogado,
                YearMonth.from(LocalDate.now()).minusMonths(1)
        );
    }

    /**
     * Bloqueio alinhado com o que ainda pode ser quitado (mesma regra da aba Pagamentos pendentes + PIX ativo).
     */
    private boolean aindaExigeBloqueioPorPagamentoDiario(Agendamento agendamento) {
        if (consultaJaFoiPaga(agendamento)) {
            return false;
        }
        if (agendamento.possuiQrPagamentoAtivo()) {
            return true;
        }
        LocalDate hoje = LocalDate.now();
        LocalDate amanha = hoje.plusDays(1);
        return exibirNaListaPagamentosPendentes(agendamento, hoje, amanha);
    }

    public boolean profissionalBloqueadoPorPendenciaPagamento(Usuario usuarioLogado) {
        return !listarPendenciasObrigatoriasParaBloqueio(usuarioLogado).isEmpty();
    }

    public String mensagemBloqueioPagamento(Usuario usuarioLogado) {
        if (temQrPagamentoAtivo(usuarioLogado)) {
            return "Você tem um PIX aguardando confirmação. Quite o pagamento para voltar a usar a sala.";
        }

        return switch (resolverPeriodicidade(usuarioLogado)) {
            case SEMANAL -> "Você precisa quitar a semana anterior (pagamento sábado ou domingo) "
                    + "para voltar a usar a sala e fazer novo agendamento.";
            case MENSAL -> "Você passou do dia 10 sem pagar o mês anterior. "
                    + "Quite o pagamento para voltar a usar a sala e fazer novo agendamento.";
            case DIARIO -> mensagemBloqueioPagamentoDiario(usuarioLogado);
        };
    }

    private String mensagemBloqueioPagamentoDiario(Usuario usuarioLogado) {
        List<Agendamento> pendencias = listarPendenciasDiariasParaBloqueio(usuarioLogado);
        if (pendencias.isEmpty()) {
            return "Você tem pagamento pendente. Quite o pagamento para voltar a usar a sala e marcar novos agendamentos.";
        }
        Agendamento proximaPendencia = pendencias.get(0);
        String data = proximaPendencia.getDataHoraInicio() != null
                ? proximaPendencia.getDataHoraInicio().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM"))
                : null;
        if (data == null) {
            return "Você tem pagamento pendente. Quite o pagamento para voltar a usar a sala e marcar novos agendamentos.";
        }
        return "Você precisa pagar o agendamento do dia " + data
                + " (no dia anterior) para voltar a usar a sala e fazer novo agendamento.";
    }

    public Optional<PagamentoProfissionalNotificacaoView> avaliarNotificacaoPagamentoProfissional(Usuario usuarioLogado) {
        if (usuarioLogado == null
                || authService.isAdmin(usuarioLogado)
                || authService.isDonaClinica(usuarioLogado)
                || authService.profissionalIgnoraValoresEPagamento(usuarioLogado)) {
            return Optional.empty();
        }

        if (temQrPagamentoAtivo(usuarioLogado)) {
            return Optional.of(new PagamentoProfissionalNotificacaoView(
                    "PIX aguardando confirmação",
                    "Você tem pagamento PIX em aberto. Quite para evitar bloqueio da agenda.",
                    LocalDate.now().format(FORMATO_DATA_COMPLETA),
                    URL_MEUS_PAGAMENTOS_DIA
            ));
        }

        Optional<PagamentoProfissionalNotificacaoView> lembrete = switch (resolverPeriodicidade(usuarioLogado)) {
            case DIARIO -> montarNotificacaoPagamentoDiario(usuarioLogado);
            case SEMANAL -> montarNotificacaoPagamentoSemanal(usuarioLogado);
            case MENSAL -> montarNotificacaoPagamentoMensal(usuarioLogado);
        };
        if (lembrete.isPresent()) {
            return lembrete;
        }

        if (profissionalBloqueadoPorPendenciaPagamento(usuarioLogado)) {
            return montarNotificacaoPagamentoBloqueado(usuarioLogado);
        }

        return Optional.empty();
    }

    public void adicionarNotificacaoPagamentoAoModelSeAplicavel(Model model, Usuario usuarioLogado) {
        Optional<PagamentoProfissionalNotificacaoView> notificacao =
                avaliarNotificacaoPagamentoProfissional(usuarioLogado);
        boolean exibirBolinha = notificacao.isPresent();
        model.addAttribute("notificacaoPagamentoProfissional", exibirBolinha ? notificacao.orElse(null) : null);
        model.addAttribute("exibirBolinhaNotificacaoPagamento", exibirBolinha);
    }

    private Optional<PagamentoProfissionalNotificacaoView> montarNotificacaoPagamentoDiario(Usuario usuarioLogado) {
        List<Agendamento> pendentes = listarPagamentosPendentesProximoDia(usuarioLogado);
        if (pendentes.isEmpty()) {
            return Optional.empty();
        }
        Agendamento referencia = pendentes.get(0);
        String data = formatarDataConsulta(referencia, rotuloProximoDiaPagamentoPendente());
        return Optional.of(new PagamentoProfissionalNotificacaoView(
                "Pagamento do agendamento",
                "Não esqueça de pagar seu agendamento do dia " + data + " para evitar bloqueio.",
                data,
                URL_MEUS_PAGAMENTOS_DIA
        ));
    }

    private Optional<PagamentoProfissionalNotificacaoView> montarNotificacaoPagamentoSemanal(Usuario usuarioLogado) {
        if (!estaEmJanelaPagamentoSemanal()) {
            return Optional.empty();
        }
        List<Agendamento> consultas = listarConsultasAdiantamentoSemanaAtual(usuarioLogado);
        if (consultas.isEmpty()) {
            return Optional.empty();
        }
        String periodo = rotuloPeriodoSemanaAtual();
        return Optional.of(new PagamentoProfissionalNotificacaoView(
                "Pagamento da semana",
                "Não esqueça de pagar a semana " + periodo + " (sábado ou domingo) para evitar bloqueio na segunda.",
                periodo,
                URL_MEUS_PAGAMENTOS_SEMANA
        ));
    }

    private Optional<PagamentoProfissionalNotificacaoView> montarNotificacaoPagamentoMensal(Usuario usuarioLogado) {
        if (!estaEmJanelaPagamentoMensal()) {
            return Optional.empty();
        }
        List<Agendamento> consultas = listarConsultasPagamentoMensal(usuarioLogado);
        if (consultas.isEmpty()) {
            return Optional.empty();
        }
        String mesReferencia = rotuloMesPagamentoPendente();
        return Optional.of(new PagamentoProfissionalNotificacaoView(
                "Pagamento mensal",
                "Não esqueça de pagar o mês " + mesReferencia + " até o dia 10 para evitar bloqueio.",
                mesReferencia,
                URL_MEUS_PAGAMENTOS_MES
        ));
    }

    private Optional<PagamentoProfissionalNotificacaoView> montarNotificacaoPagamentoBloqueado(Usuario usuarioLogado) {
        PeriodicidadePagamento periodicidade = resolverPeriodicidade(usuarioLogado);
        List<Agendamento> pendencias = listarPendenciasObrigatoriasParaBloqueio(usuarioLogado);
        String rotuloData = resolverRotuloDataNotificacaoPagamento(periodicidade, pendencias);
        return Optional.of(new PagamentoProfissionalNotificacaoView(
                "Agenda bloqueada por pagamento",
                mensagemBloqueioPagamento(usuarioLogado),
                rotuloData,
                urlMeusPagamentosPorPeriodicidade(periodicidade)
        ));
    }

    private String resolverRotuloDataNotificacaoPagamento(
            PeriodicidadePagamento periodicidade,
            List<Agendamento> pendencias
    ) {
        return switch (periodicidade) {
            case DIARIO -> pendencias.stream()
                    .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                    .findFirst()
                    .map(agendamento -> agendamento.getDataHoraInicio().toLocalDate().format(FORMATO_DATA_COMPLETA))
                    .orElse(rotuloProximoDiaPagamentoPendente());
            case SEMANAL -> {
                PeriodoSemanaPagamento semanaAnterior = resolverSemanaAnteriorEncerrada(LocalDate.now());
                if (semanaAnterior != null) {
                    DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM");
                    yield semanaAnterior.inicio().format(formato) + " a " + semanaAnterior.fim().format(formato);
                }
                yield rotuloPeriodoSemanaAtual();
            }
            case MENSAL -> rotuloMesPagamentoPendente();
        };
    }

    private String urlMeusPagamentosPorPeriodicidade(PeriodicidadePagamento periodicidade) {
        return switch (periodicidade) {
            case DIARIO -> URL_MEUS_PAGAMENTOS_DIA;
            case SEMANAL -> URL_MEUS_PAGAMENTOS_SEMANA;
            case MENSAL -> URL_MEUS_PAGAMENTOS_MES;
        };
    }

    private String formatarDataConsulta(Agendamento agendamento, String fallback) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return fallback;
        }
        return agendamento.getDataHoraInicio().toLocalDate().format(FORMATO_DATA_COMPLETA);
    }

    public List<ProfissionalBloqueioPagamentoView> listarProfissionaisBloqueadosPorPagamento() {
        return usuarioRepository.findByCargoOrderByNomeAsc(CARGO_PROFISSIONAL).stream()
                .filter(profissional -> !authService.profissionalIgnoraValoresEPagamento(profissional))
                .filter(this::profissionalBloqueadoPorPendenciaPagamento)
                .map(profissional -> new ProfissionalBloqueioPagamentoView(
                        profissional.getId(),
                        profissional.getNome(),
                        profissional.getLogin(),
                        listarPendenciasObrigatoriasParaBloqueio(profissional).size()
                ))
                .toList();
    }

    public boolean profissionalEstaBloqueadoPorPagamento(Long profissionalId) {
        if (profissionalId == null) {
            return false;
        }
        return usuarioRepository.findById(profissionalId)
                .filter(profissional -> !authService.profissionalIgnoraValoresEPagamento(profissional))
                .map(this::profissionalBloqueadoPorPendenciaPagamento)
                .orElse(false);
    }

    public boolean podePagarAgora(Agendamento agendamento) {
        if (agendamento == null || PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        if (agendamento.getProfissional() != null
                && authService.profissionalIgnoraValoresEPagamento(agendamento.getProfissional())) {
            return false;
        }
        if (profissionalUsaPagamentoSemanal(agendamento) || profissionalUsaPagamentoMensal(agendamento)) {
            return false;
        }
        if (agendamento.possuiQrPagamentoAtivo()) {
            return false;
        }
        if (agendamento.getDataHoraInicio() == null) {
            return false;
        }
        if (PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(agendamento.getStatusPagamento())) {
            return dentroJanelaRecuperacao(agendamento) && !outroProfissionalOcupouVaga(agendamento);
        }
        if (!agendamento.getDataHoraInicio().isAfter(LocalDateTime.now())) {
            return false;
        }
        PagamentoStatus status = agendamento.getStatusPagamento();
        return status == null
                || status == PagamentoStatus.PAGAMENTO_FUTURO
                || status == PagamentoStatus.AGUARDANDO_PAGAMENTO;
    }

    public boolean outroProfissionalOcupouVaga(Agendamento agendamento) {
        if (agendamento == null || agendamento.getSala() == null || agendamento.getSala().getId() == null) {
            return false;
        }
        if (agendamento.getDataHoraInicio() == null || agendamento.getDataHoraFim() == null) {
            return false;
        }
        Long agendamentoId = agendamento.getId() != null ? agendamento.getId() : -1L;
        return repository.findFirstOcupacaoAtivaNoHorarioExceto(
                agendamento.getSala().getId(),
                agendamento.getDataHoraInicio(),
                agendamento.getDataHoraFim(),
                agendamentoId
        ).isPresent();
    }

    public boolean passouPrazoPagamentoVespera(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return false;
        }
        return !LocalDateTime.now().isBefore(calcularLimitePagamentoVespera(agendamento));
    }

    public boolean vagaPreenchidaPorOutroProfissional(Agendamento agendamento) {
        if (agendamento == null || !PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        return outroProfissionalOcupouVaga(agendamento);
    }

    public boolean podeRecuperarVagaComPagamento(Agendamento agendamento) {
        return PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(agendamento.getStatusPagamento())
                && podePagarAgora(agendamento);
    }

    public boolean ocupaVagaNaGrade(Agendamento agendamento) {
        if (agendamento == null) {
            return false;
        }
        if (PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        return exibirNaGradeComoReservado(agendamento);
    }

    public boolean podeVerPagamento(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null) {
            return false;
        }
        if (authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado)) {
            return true;
        }
        return agendamento.getProfissional() != null
                && agendamento.getProfissional().getId().equals(usuarioLogado.getId());
    }

    public boolean exibirBotaoPagar(Agendamento agendamento) {
        return podePagarAgora(agendamento);
    }

    public String formatarValorTaxaPix(Agendamento agendamento) {
        BigDecimal valor = infinitePayService.resolverValorTaxaClinica(agendamento);
        if (valor == null || valor.signum() <= 0) {
            return "—";
        }
        return com.clinica.sistema.util.MoedaBrasilUtil.formatar(valor);
    }

    public String rotuloStatusPagamento(Agendamento agendamento) {
        return rotuloStatusPagamento(agendamento, null);
    }

    public String rotuloStatusPagamento(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null) {
            return "Sem pagamento";
        }
        if (usuarioLogado != null && gestorVisualizandoAgendamentoDeOutro(agendamento, usuarioLogado)) {
            if (agendamento.isPagamentoPago()) {
                return "Confirmado — acerto com a profissional";
            }
            if (agendamento.isReservaPendenteNaGrade()) {
                return rotuloEsperandoNaGradeParaGestor(agendamento);
            }
        }
        PagamentoStatus status = agendamento.getStatusPagamento();
        if (status == null) {
            return "Sem pagamento";
        }
        return switch (status) {
            case PAGO -> "Pago";
            case ESPERANDO_CONFIRMACAO -> agendamento.possuiQrPagamentoAtivo()
                    ? "Esperando confirmação (" + agendamento.getTempoRestantePagamentoFormatado() + ")"
                    : "Confirmação expirada";
            case AGUARDANDO_PAGAMENTO -> bloqueadoPorPagamento(agendamento)
                    ? "Não pago — sala bloqueada"
                    : "Aguardando pagamento";
            case PAGAMENTO_FUTURO -> rotuloPagamentoFuturo(agendamento);
            case LIBERADO_FALTA_PAGAMENTO -> vagaPreenchidaPorOutroProfissional(agendamento)
                    ? "Vaga preenchida por outro profissional"
                    : "Vaga liberada — você ainda pode pagar para recuperar";
        };
    }

    public boolean deveExibirPillPendenteNaGrade(Agendamento agendamento) {
        return agendamento != null && agendamento.isReservaPendenteNaGrade();
    }

    public String rotuloEsperandoNaGrade(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || !agendamento.isReservaPendenteNaGrade()) {
            return "";
        }
        if (gestorVisualizandoAgendamentoDeOutro(agendamento, usuarioLogado)) {
            return rotuloEsperandoNaGradeParaGestor(agendamento);
        }
        if (!podeVerPagamento(agendamento, usuarioLogado)) {
            if (reservaConfirmadaParaVisaoPublica(agendamento)) {
                return "Sala confirmada";
            }
            return "Aguardando confirmações";
        }
        PagamentoStatus status = agendamento.getStatusPagamento();
        if (status == PagamentoStatus.ESPERANDO_CONFIRMACAO) {
            return "Esperando pagamento";
        }
        if (status == PagamentoStatus.AGUARDANDO_PAGAMENTO) {
            return "Aguardando pagamento";
        }
        if (status == PagamentoStatus.PAGAMENTO_FUTURO) {
            return rotuloPagamentoFuturo(agendamento);
        }
        if (status == PagamentoStatus.LIBERADO_FALTA_PAGAMENTO) {
            return "Vaga liberada — pague para recuperar";
        }
        return "";
    }

    public boolean exibirSalaConfirmadaNaGrade(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || agendamento.isPagamentoPago()) {
            return false;
        }
        if (podeVerPagamento(agendamento, usuarioLogado)) {
            return false;
        }
        return reservaConfirmadaParaVisaoPublica(agendamento);
    }

    public boolean exibirEstiloAguardandoPagamentoNaGrade(Agendamento agendamento, Usuario usuarioLogado) {
        return agendamento != null
                && agendamento.isReservaPendenteNaGrade()
                && !exibirSalaConfirmadaNaGrade(agendamento, usuarioLogado);
    }

    private boolean reservaConfirmadaParaVisaoPublica(Agendamento agendamento) {
        if (agendamento == null || agendamento.getProfissional() == null) {
            return false;
        }
        PeriodicidadePagamento periodicidade = resolverPeriodicidade(agendamento.getProfissional());
        if (periodicidade != PeriodicidadePagamento.SEMANAL
                && periodicidade != PeriodicidadePagamento.MENSAL) {
            return false;
        }
        PagamentoStatus status = agendamento.getStatusPagamento();
        if (PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(status)) {
            return false;
        }
        return PagamentoStatus.PAGAMENTO_FUTURO.equals(status)
                || PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(status);
    }

    public String rotuloPagoNaGrade(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null) {
            return "";
        }
        if (gestorVisualizandoAgendamentoDeOutro(agendamento, usuarioLogado)) {
            return "Confirmado — acerto com a profissional";
        }
        return agendamento.rotuloPagoNaGrade(podeVerPagamento(agendamento, usuarioLogado));
    }

    private boolean gestorVisualizandoAgendamentoDeOutro(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null) {
            return false;
        }
        if (!authService.isAdmin(usuarioLogado) && !authService.isDonaClinica(usuarioLogado)) {
            return false;
        }
        if (agendamento.getProfissional() == null || agendamento.getProfissional().getId() == null) {
            return false;
        }
        return !agendamento.getProfissional().getId().equals(usuarioLogado.getId());
    }

    private String rotuloEsperandoNaGradeParaGestor(Agendamento agendamento) {
        String nomeProfissional = resolverNomeProfissionalAgendamento(agendamento);
        PagamentoStatus status = agendamento.getStatusPagamento();
        if (status == PagamentoStatus.PAGAMENTO_FUTURO) {
            if (profissionalUsaPagamentoMensal(agendamento)) {
                if (cobrancaMensalVencendoNaData(agendamento, LocalDate.now())) {
                    return nomeProfissional + ": pagamento do dia 01 ao 10";
                }
                YearMonth mesReferencia = resolverMesReferenciaCobranca(agendamento);
                if (mesReferencia != null) {
                    YearMonth mesPagamento = mesReferencia.plusMonths(1);
                    return nomeProfissional + ": pagamento do dia 01 ao 10/"
                            + mesPagamento.format(DateTimeFormatter.ofPattern("MM"));
                }
            }
            if (profissionalUsaPagamentoSemanal(agendamento)) {
                return nomeProfissional + ": pagamento no dia " + formatarDiaPagamentoSemanal(agendamento);
            }
            return nomeProfissional + ": pagamento na véspera";
        }
        if (status == PagamentoStatus.ESPERANDO_CONFIRMACAO
                || status == PagamentoStatus.AGUARDANDO_PAGAMENTO) {
            return nomeProfissional + ": aguardando pagamento";
        }
        if (status == PagamentoStatus.LIBERADO_FALTA_PAGAMENTO) {
            return nomeProfissional + ": vaga liberada";
        }
        return "";
    }

    private String resolverNomeProfissionalAgendamento(Agendamento agendamento) {
        if (agendamento.getProfissional() == null) {
            return "Profissional";
        }
        String nome = agendamento.getProfissional().getNome();
        if (nome == null || nome.isBlank()) {
            return "Profissional";
        }
        return nome.trim();
    }

    private boolean ePendenciaObrigatoriaParaDesbloqueio(Agendamento agendamento) {
        if (agendamento.possuiQrPagamentoAtivo()) {
            return true;
        }
        if (PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(agendamento.getStatusPagamento())) {
            return dentroJanelaRecuperacao(agendamento) && !outroProfissionalOcupouVaga(agendamento);
        }
        if (bloqueadoPorPagamento(agendamento)) {
            return true;
        }
        return deveAbrirPagamentoAgora(agendamento)
                && statusPendenteDePagamento(agendamento)
                && !PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(agendamento.getStatusPagamento());
    }

    private boolean statusPendenteDePagamento(Agendamento agendamento) {
        PagamentoStatus status = agendamento.getStatusPagamento();
        return status == null
                || status == PagamentoStatus.PAGAMENTO_FUTURO
                || status == PagamentoStatus.AGUARDANDO_PAGAMENTO
                || status == PagamentoStatus.LIBERADO_FALTA_PAGAMENTO;
    }

    public boolean bloqueadoPorPagamento(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return false;
        }
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        if (PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        LocalDate consulta = agendamento.getDataHoraInicio().toLocalDate();
        return !LocalDate.now().isBefore(consulta);
    }

    public boolean exibirNaGradeComoReservado(Agendamento agendamento) {
        if (agendamento == null) {
            return false;
        }
        return PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())
                || PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())
                || PagamentoStatus.AGUARDANDO_PAGAMENTO.equals(agendamento.getStatusPagamento())
                || PagamentoStatus.PAGAMENTO_FUTURO.equals(agendamento.getStatusPagamento());
    }

    private void definirReferenciasCobrancaSeNecessario(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return;
        }
        LocalDate dataAgendamento = agendamento.getDataHoraInicio().toLocalDate();
        if (agendamento.getDataReferenciaSemanaPagamento() == null) {
            agendamento.setDataReferenciaSemanaPagamento(dataAgendamento);
        }
        if (agendamento.getDataReferenciaMesPagamento() == null) {
            agendamento.setDataReferenciaMesPagamento(dataAgendamento.withDayOfMonth(1));
        }
    }

    private boolean deveMigrarAoAlterarPeriodicidade(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return false;
        }
        PagamentoStatus status = agendamento.getStatusPagamento();
        if (status == null) {
            return true;
        }
        return status != PagamentoStatus.PAGO
                && status != PagamentoStatus.LIBERADO_FALTA_PAGAMENTO;
    }

    private void sincronizarPeriodicidadeProfissionalNoAgendamento(
            Agendamento agendamento,
            Usuario profissional,
            PeriodicidadePagamento periodicidadeNova
    ) {
        if (agendamento.getProfissional() != null) {
            agendamento.getProfissional().setPeriodicidadePagamento(periodicidadeNova);
            return;
        }
        agendamento.setProfissional(profissional);
    }

    private void aplicarPeriodicidadeNoAgendamento(
            Agendamento agendamento,
            PeriodicidadePagamento periodicidadeNova
    ) {
        limparDadosPagamentoEmAberto(agendamento);
        agendamento.setLiberadoEm(null);
        recalcularReferenciasCobranca(agendamento, periodicidadeNova);

        if (periodicidadeNova == PeriodicidadePagamento.DIARIO) {
            if (deveAbrirPagamentoAgora(agendamento)) {
                agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
            } else {
                agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
            }
            return;
        }

        agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
    }

    private void recalcularReferenciasCobranca(
            Agendamento agendamento,
            PeriodicidadePagamento periodicidade
    ) {
        if (agendamento.getDataHoraInicio() == null) {
            return;
        }
        LocalDate dataAgendamento = agendamento.getDataHoraInicio().toLocalDate();
        if (periodicidade == PeriodicidadePagamento.MENSAL) {
            agendamento.setDataReferenciaMesPagamento(dataAgendamento.withDayOfMonth(1));
            agendamento.setDataReferenciaSemanaPagamento(null);
            return;
        }
        if (periodicidade == PeriodicidadePagamento.SEMANAL) {
            agendamento.setDataReferenciaSemanaPagamento(dataAgendamento);
            agendamento.setDataReferenciaMesPagamento(null);
            return;
        }
        agendamento.setDataReferenciaSemanaPagamento(null);
        agendamento.setDataReferenciaMesPagamento(null);
    }

    private LocalDate resolverDataReferenciaMesCobranca(Agendamento agendamento) {
        if (agendamento.getDataReferenciaMesPagamento() != null) {
            return agendamento.getDataReferenciaMesPagamento();
        }
        if (agendamento.getDataHoraInicio() != null) {
            return agendamento.getDataHoraInicio().toLocalDate().withDayOfMonth(1);
        }
        return null;
    }

    YearMonth resolverMesReferenciaCobranca(Agendamento agendamento) {
        LocalDate referencia = resolverDataReferenciaMesCobranca(agendamento);
        return referencia != null ? YearMonth.from(referencia) : null;
    }

    private String rotuloPagamentoFuturo(Agendamento agendamento) {
        if (profissionalUsaPagamentoMensal(agendamento)) {
            return rotuloPagamentoFuturoMensal(agendamento, LocalDate.now());
        }
        if (profissionalUsaPagamentoSemanal(agendamento)) {
            return "Você vai pagar no dia " + formatarDiaPagamentoSemanal(agendamento);
        }
        return "Pagamento em " + formatarDiaPagamentoDiario(agendamento);
    }

    String rotuloPagamentoFuturoMensal(Agendamento agendamento, LocalDate hoje) {
        if (cobrancaMensalVencendoNaData(agendamento, hoje)) {
            return "Você tem do dia 01 ao 10 para pagar";
        }
        YearMonth mesReferencia = resolverMesReferenciaCobranca(agendamento);
        if (mesReferencia == null) {
            return "—";
        }
        YearMonth mesPagamento = mesReferencia.plusMonths(1);
        return "Você vai pagar do dia 01 ao 10/"
                + mesPagamento.format(DateTimeFormatter.ofPattern("MM"));
    }

    private boolean cobrancaMensalVencendoNaData(Agendamento agendamento, LocalDate hoje) {
        if (!profissionalUsaPagamentoMensal(agendamento)) {
            return false;
        }
        int dia = hoje.getDayOfMonth();
        if (dia < 1 || dia > 10) {
            return false;
        }
        YearMonth mesReferencia = resolverMesReferenciaCobranca(agendamento);
        if (mesReferencia == null) {
            return false;
        }
        return mesReferencia.equals(YearMonth.from(hoje).minusMonths(1));
    }

    private LocalDate resolverDataReferenciaCobranca(Agendamento agendamento) {
        if (agendamento.getDataReferenciaSemanaPagamento() != null) {
            return agendamento.getDataReferenciaSemanaPagamento();
        }
        if (agendamento.getDataHoraInicio() != null) {
            return agendamento.getDataHoraInicio().toLocalDate();
        }
        return null;
    }

    PeriodoSemanaPagamento resolverSemanaPorDataReferencia(LocalDate dataReferencia) {
        if (dataReferencia == null) {
            return null;
        }
        if (dataReferencia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return new PeriodoSemanaPagamento(dataReferencia.minusDays(6), dataReferencia);
        }
        LocalDate inicio = dataReferencia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new PeriodoSemanaPagamento(inicio, inicio.plusDays(6));
    }

    private LocalDate resolverDomingoPagamentoSemanal(Agendamento agendamento) {
        LocalDate referencia = resolverDataReferenciaCobranca(agendamento);
        PeriodoSemanaPagamento semana = resolverSemanaPorDataReferencia(referencia);
        if (semana == null) {
            return null;
        }
        return semana.fim();
    }

    private String formatarDiaPagamentoDiario(Agendamento agendamento) {
        if (agendamento.getDataHoraInicio() == null) {
            return "—";
        }
        return agendamento.getDataHoraInicio().toLocalDate().minusDays(1)
                .format(DateTimeFormatter.ofPattern("dd/MM"));
    }

    public String rotuloDiaPagamentoSemanalNaGrade(Agendamento agendamento) {
        return formatarDiaPagamentoSemanal(agendamento);
    }

    public String rotuloDiaPagamentoMensalNaGrade(Agendamento agendamento) {
        return formatarJanelaPagamentoMensal(agendamento);
    }

    public String formatarJanelaPagamentoMensal(Agendamento agendamento) {
        YearMonth mesReferencia = resolverMesReferenciaCobranca(agendamento);
        if (mesReferencia == null) {
            return "—";
        }
        YearMonth mesPagamento = mesReferencia.plusMonths(1);
        return "01 ao 10/" + mesPagamento.format(DateTimeFormatter.ofPattern("MM"));
    }

    public String rotuloMesCobranca(Agendamento agendamento) {
        YearMonth mes = resolverMesReferenciaCobranca(agendamento);
        if (mes == null) {
            return null;
        }
        return mes.format(DateTimeFormatter.ofPattern("MM/yyyy"));
    }

    public String rotuloIntervaloSemanaCobranca(Agendamento agendamento) {
        if (!profissionalUsaPagamentoSemanal(agendamento)) {
            return null;
        }
        LocalDate referencia = resolverDataReferenciaCobranca(agendamento);
        PeriodoSemanaPagamento semana = resolverSemanaPorDataReferencia(referencia);
        if (semana == null) {
            return null;
        }
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM");
        return semana.inicio().format(formato) + " a " + semana.fim().format(formato);
    }

    public boolean realocacaoSemanalComCobrancaPendente(Agendamento agendamento) {
        return agendamento != null
                && PagamentoStatus.PAGAMENTO_FUTURO.equals(agendamento.getStatusPagamento())
                && profissionalUsaPagamentoSemanal(agendamento);
    }

    public boolean realocacaoMensalComCobrancaPendente(Agendamento agendamento) {
        return agendamento != null
                && PagamentoStatus.PAGAMENTO_FUTURO.equals(agendamento.getStatusPagamento())
                && profissionalUsaPagamentoMensal(agendamento);
    }

    private String formatarDiaPagamentoSemanal(Agendamento agendamento) {
        LocalDate domingo = resolverDomingoPagamentoSemanal(agendamento);
        if (domingo == null) {
            return "—";
        }
        return domingo.format(DateTimeFormatter.ofPattern("dd/MM"));
    }

    private Agendamento marcarComoPago(Agendamento agendamento) {
        validarRecuperacaoPagamento(agendamento);
        agendamento.setStatusPagamento(PagamentoStatus.PAGO);
        agendamento.setDataPagamento(LocalDateTime.now());
        agendamento.setPagamentoExpiraEm(null);
        agendamento.setLiberadoEm(null);
        return repository.save(agendamento);
    }

    private void validarRecuperacaoPagamento(Agendamento agendamento) {
        if (!PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(agendamento.getStatusPagamento())) {
            return;
        }
        if (!dentroJanelaRecuperacao(agendamento)) {
            throw new RuntimeException("Prazo final para pagar este horário foi encerrado.");
        }
        if (outroProfissionalOcupouVaga(agendamento)) {
            String detalhe = formatarDetalheHorario(agendamento);
            throw new HorarioJaReservadoPorOutroProfissionalException(detalhe);
        }
    }

    private boolean dentroJanelaRecuperacao(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return false;
        }
        return !LocalDateTime.now().isAfter(agendamento.getDataHoraInicio());
    }

    public boolean consultaJaFoiPaga(Agendamento agendamento) {
        return agendamento != null && agendamento.isPagamentoPago();
    }

    private boolean podeAdiantarPagamentoSemana(Agendamento agendamento) {
        if (agendamento == null || consultaJaFoiPaga(agendamento)) {
            return false;
        }
        if (agendamento.getProfissional() != null
                && authService.profissionalIgnoraValoresEPagamento(agendamento.getProfissional())) {
            return false;
        }
        if (agendamento.possuiQrPagamentoAtivo()) {
            return false;
        }
        if (agendamento.getDataHoraInicio() == null
                || !agendamento.getDataHoraInicio().isAfter(LocalDateTime.now())) {
            return false;
        }
        if (ePendenciaObrigatoriaParaDesbloqueio(agendamento)) {
            return false;
        }
        return PagamentoStatus.PAGAMENTO_FUTURO.equals(agendamento.getStatusPagamento());
    }

    private String formatarDetalheHorario(Agendamento agendamento) {
        if (agendamento.getDataHoraInicio() == null) {
            return "";
        }
        String sala = agendamento.getSala() != null && agendamento.getSala().getNome() != null
                ? agendamento.getSala().getNome()
                : "sala";
        String dataHora = agendamento.getDataHoraInicio()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm"));
        return "(" + sala + ", " + dataHora + ")";
    }

    private void liberarPorFaltaPagamento(Agendamento agendamento) {
        limparDadosPagamentoEmAberto(agendamento);
        agendamento.setStatusPagamento(PagamentoStatus.LIBERADO_FALTA_PAGAMENTO);
        agendamento.setLiberadoEm(LocalDateTime.now());
        repository.save(agendamento);
    }

    private void limparDadosPagamentoEmAberto(Agendamento agendamento) {
        agendamento.setPagamentoOrderNsu(null);
        agendamento.setPagamentoLink(null);
        agendamento.setPagamentoSlug(null);
        agendamento.setPagamentoIniciadoEm(null);
        agendamento.setPagamentoExpiraEm(null);
    }

    /**
     * Fim da vespera (dia anterior a consulta) na hora configurada (padrao 23:59).
     * Apos isso a vaga pode liberar na grade; titular ainda pode recuperar ate o horario da consulta.
     */
    private LocalDateTime calcularLimitePagamentoVespera(Agendamento agendamento) {
        LocalDate diaAnterior = agendamento.getDataHoraInicio().toLocalDate().minusDays(1);
        return LocalDateTime.of(diaAnterior, resolverHoraLimiteVespera());
    }

    private void validarAntesConfirmarPagamento(Agendamento agendamento, String orderNsu, boolean modoTeste) {
        if (agendamento.getPagamentoOrderNsu() == null
                || !agendamento.getPagamentoOrderNsu().equals(orderNsu)) {
            throw new RuntimeException("Pedido de pagamento não confere com a consulta.");
        }
        if (modoTeste) {
            if (vagaPreenchidaPorOutroProfissional(agendamento)) {
                throw new HorarioJaReservadoPorOutroProfissionalException(formatarDetalheHorario(agendamento));
            }
            return;
        }
        if (!PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException("Pagamento não está aguardando confirmação.");
        }
        if (!agendamento.possuiQrPagamentoAtivo()) {
            throw new RuntimeException("Pagamento expirado ou inválido.");
        }
    }

    private LocalTime resolverHoraLimiteVespera() {
        String configurado = pagamentoProperties.getHoraLimitePagamentoVespera();
        if (configurado == null || configurado.isBlank()) {
            return LocalTime.of(23, 59, 59);
        }
        try {
            if (configurado.length() == 5) {
                return LocalTime.parse(configurado, DateTimeFormatter.ofPattern("HH:mm"))
                        .withSecond(59);
            }
            return LocalTime.parse(configurado, DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (DateTimeParseException ex) {
            return LocalTime.of(23, 59, 59);
        }
    }

    private void iniciarConfirmacaoPagamento(Agendamento agendamento) {
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return;
        }
        aplicarValoresTaxaAntesPagamento(agendamento);
        LinkPagamentoGerado link = infinitePayService.gerarLinkPagamento(agendamento);
        LocalDateTime agora = LocalDateTime.now();
        agendamento.setStatusPagamento(PagamentoStatus.ESPERANDO_CONFIRMACAO);
        agendamento.setPagamentoOrderNsu(link.getOrderNsu());
        agendamento.setPagamentoLink(link.getLinkPagamento());
        agendamento.setPagamentoSlug(link.getSlug());
        agendamento.setPagamentoIniciadoEm(agora);
        agendamento.setPagamentoExpiraEm(agora.plusMinutes(pagamentoProperties.getPrazoConfirmacaoMinutos()));
    }

    private void aplicarValoresTaxaAntesPagamento(Agendamento agendamento) {
        BigDecimal taxaPix = infinitePayService.resolverValorTaxaClinica(agendamento);
        if (taxaPix == null || taxaPix.signum() <= 0) {
            throw new RuntimeException("Valor da taxa da clínica inválido para pagamento.");
        }
        if (agendamento.getValorClinicaCobra() == null || agendamento.getValorClinicaCobra().signum() <= 0) {
            agendamento.setValorClinicaCobra(taxaPix);
            if (agendamento.getValorProfissionalRecebe() != null) {
                agendamento.setValorLiquidoProfissional(
                        agendamento.getValorProfissionalRecebe().subtract(taxaPix).max(BigDecimal.ZERO)
                                .setScale(2, java.math.RoundingMode.HALF_UP)
                );
            }
        }
        agendamento.setValorPagamento(taxaPix);
    }

    private void validarAcessoPagamento(Agendamento agendamento, Usuario usuarioLogado) {
        if (authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado)) {
            return;
        }
        if (agendamento.getProfissional() == null
                || !agendamento.getProfissional().getId().equals(usuarioLogado.getId())) {
            throw new RuntimeException("Acesso negado.");
        }
    }

    private int removerAgendamentoExpirado(Agendamento agendamento) {
        if (agendamento == null || agendamento.getId() == null) {
            return 0;
        }

        if (deveExcluirSerieInteiraAoExpirarPrimeiroQr(agendamento)) {
            return repository.deleteBySerieFixaIdAndStatusPagamentoNot(
                    agendamento.getSerieFixaId(),
                    PagamentoStatus.PAGO
            );
        }

        if (deveExcluirApenasAgendamentoExpirado(agendamento)) {
            repository.deleteById(agendamento.getId());
            return 1;
        }

        limparDadosPagamentoEmAberto(agendamento);
        if (deveAbrirPagamentoAgora(agendamento)) {
            agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_PAGAMENTO);
        } else {
            agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
        }
        repository.save(agendamento);
        return 1;
    }

    private boolean deveExcluirApenasAgendamentoExpirado(Agendamento agendamento) {
        return agendamento.getSerieFixaId() == null || agendamento.getSerieFixaId().isBlank();
    }

    private boolean deveExcluirSerieInteiraAoExpirarPrimeiroQr(Agendamento agendamento) {
        if (agendamento.getSerieFixaId() == null || agendamento.getSerieFixaId().isBlank()) {
            return false;
        }
        return isPrimeiraOcorrenciaDaSerie(agendamento);
    }

    private boolean isPrimeiraOcorrenciaDaSerie(Agendamento agendamento) {
        Optional<Agendamento> primeiraOcorrencia = repository.findFirstBySerieFixaIdOrderByDataHoraInicioAsc(
                agendamento.getSerieFixaId()
        );
        if (primeiraOcorrencia.isEmpty() || primeiraOcorrencia.get().getId() == null) {
            return false;
        }
        return primeiraOcorrencia.get().getId().equals(agendamento.getId());
    }

    private Agendamento buscarComPermissao(Long agendamentoId, Usuario usuarioLogado) {
        Agendamento agendamento = repository.findById(agendamentoId)
                .orElseThrow(() -> new RuntimeException("Agendamento nao encontrado."));
        if (authService.isAdmin(usuarioLogado) || authService.isDonaClinica(usuarioLogado)) {
            return agendamento;
        }
        if (agendamento.getProfissional() == null
                || !agendamento.getProfissional().getId().equals(usuarioLogado.getId())) {
            throw new RuntimeException("Você só pode pagar os seus próprios agendamentos.");
        }
        return agendamento;
    }

    private boolean profissionalUsaPagamentoDiario(Agendamento agendamento) {
        Usuario profissional = agendamento.getProfissional();
        return profissional != null
                && resolverPeriodicidade(profissional) == PeriodicidadePagamento.DIARIO;
    }

    private boolean profissionalUsaPagamentoSemanal(Agendamento agendamento) {
        Usuario profissional = agendamento.getProfissional();
        return profissional != null
                && resolverPeriodicidade(profissional) == PeriodicidadePagamento.SEMANAL;
    }

    private boolean profissionalUsaPagamentoMensal(Agendamento agendamento) {
        Usuario profissional = agendamento.getProfissional();
        return profissional != null
                && resolverPeriodicidade(profissional) == PeriodicidadePagamento.MENSAL;
    }

    private boolean temQrPagamentoAtivo(Usuario profissional) {
        return !listarConsultasComQrAtivo(profissional).isEmpty();
    }

    private List<Agendamento> listarConsultasComQrAtivo(Usuario profissional) {
        return repository.findByProfissionalIdOrderByDataHoraInicioAsc(profissional.getId()).stream()
                .filter(Agendamento::possuiQrPagamentoAtivo)
                .toList();
    }

    private boolean bloqueadoPorPagamentoSemanal(Usuario profissional) {
        LocalDate hoje = LocalDate.now();
        if (hoje.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        PeriodoSemanaPagamento semanaAnterior = resolverSemanaAnteriorEncerrada(hoje);
        if (semanaAnterior == null) {
            return false;
        }
        List<Agendamento> consultas = listarConsultasNaoPagasNoPeriodo(profissional, semanaAnterior);
        return !consultas.isEmpty();
    }

    private boolean bloqueadoPorPagamentoMensal(Usuario profissional) {
        LocalDate hoje = LocalDate.now();
        if (hoje.getDayOfMonth() <= 10) {
            return false;
        }
        YearMonth mesAnterior = YearMonth.from(hoje).minusMonths(1);
        return !listarConsultasNaoPagasNoMes(profissional, mesAnterior).isEmpty();
    }

    private PeriodoSemanaPagamento resolverSemanaAnteriorEncerrada(LocalDate referencia) {
        if (referencia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return null;
        }
        LocalDate ultimoDomingo = referencia.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        return new PeriodoSemanaPagamento(ultimoDomingo.minusDays(6), ultimoDomingo);
    }

    private PeriodoSemanaPagamento resolverSemanaCorrenteParaCobranca(LocalDate referencia) {
        if (referencia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return new PeriodoSemanaPagamento(referencia.minusDays(6), referencia);
        }
        return resolverPeriodoSemanaPagamento(referencia);
    }

    private List<Agendamento> listarConsultasNaoPagasNoPeriodo(Usuario profissional, PeriodoSemanaPagamento periodo) {
        boolean usaReferenciaSemanal = resolverPeriodicidade(profissional) == PeriodicidadePagamento.SEMANAL;
        return repository.findByProfissionalIdOrderByDataHoraInicioAsc(profissional.getId()).stream()
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !consultaJaFoiPaga(agendamento))
                .filter(agendamento -> {
                    LocalDate dataCobranca = usaReferenciaSemanal
                            ? resolverDataReferenciaCobranca(agendamento)
                            : agendamento.getDataHoraInicio().toLocalDate();
                    if (dataCobranca == null) {
                        return false;
                    }
                    return !dataCobranca.isBefore(periodo.inicio()) && !dataCobranca.isAfter(periodo.fim());
                })
                .toList();
    }

    private List<Agendamento> listarConsultasNaoPagasNoMes(Usuario profissional, YearMonth mesReferencia) {
        boolean usaReferenciaMensal = resolverPeriodicidade(profissional) == PeriodicidadePagamento.MENSAL;
        return repository.findByProfissionalIdOrderByDataHoraInicioAsc(profissional.getId()).stream()
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !consultaJaFoiPaga(agendamento))
                .filter(agendamento -> {
                    YearMonth mesCobranca = usaReferenciaMensal
                            ? resolverMesReferenciaCobranca(agendamento)
                            : (agendamento.getDataHoraInicio() != null
                            ? YearMonth.from(agendamento.getDataHoraInicio())
                            : null);
                    return mesReferencia.equals(mesCobranca);
                })
                .toList();
    }
}
