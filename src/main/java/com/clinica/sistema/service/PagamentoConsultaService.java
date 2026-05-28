package com.clinica.sistema.service;

import com.clinica.sistema.config.InfinitePayProperties;
import com.clinica.sistema.config.PagamentoProperties;
import com.clinica.sistema.dto.LinkPagamentoGerado;
import com.clinica.sistema.dto.ProfissionalBloqueioPagamentoView;
import com.clinica.sistema.exception.HorarioJaReservadoPorOutroProfissionalException;
import com.clinica.sistema.exception.PagamentoWebhookNaoAutorizadoException;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    public void configurarPagamentosAoSalvar(List<Agendamento> novosAgendamentos, Usuario profissional) {
        if (novosAgendamentos == null || novosAgendamentos.isEmpty()) {
            return;
        }
        if (authService.profissionalIgnoraValoresEPagamento(profissional)) {
            for (Agendamento agendamento : novosAgendamentos) {
                agendamento.setStatusPagamento(PagamentoStatus.PAGO);
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

    public void configurarPagamentoNovaOcorrenciaSerie(Agendamento agendamento) {
        if (agendamento.getStatusPagamento() != null) {
            return;
        }
        if (agendamento.getProfissional() != null
                && authService.profissionalIgnoraValoresEPagamento(agendamento.getProfissional())) {
            agendamento.setStatusPagamento(PagamentoStatus.PAGO);
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
            throw new RuntimeException("Esta consulta ja esta paga.");
        }
        if (vagaPreenchidaPorOutroProfissional(agendamento)) {
            throw new HorarioJaReservadoPorOutroProfissionalException(formatarDetalheHorario(agendamento));
        }
        validarRecuperacaoPagamento(agendamento);
        if (!podePagarAgora(agendamento) && !agendamento.possuiQrPagamentoAtivo()) {
            throw new RuntimeException("Esta consulta nao esta disponivel para pagamento.");
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
            throw new RuntimeException("Pedido de pagamento invalido.");
        }
        List<Agendamento> agendamentos = repository.findAllByPagamentoOrderNsuOrderByDataHoraInicioAsc(orderNsu);
        if (agendamentos.isEmpty()) {
            throw new RuntimeException("Pedido de pagamento nao encontrado.");
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

    public List<Agendamento> listarConsultasAdiantamentoSemanaAtual(Usuario usuarioLogado) {
        if (usuarioLogado == null
                || authService.isAdmin(usuarioLogado)
                || authService.isDonaClinica(usuarioLogado)
                || authService.profissionalIgnoraValoresEPagamento(usuarioLogado)) {
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
        List<Agendamento> consultas = listarConsultasAdiantamentoSemanaAtual(usuarioLogado);
        if (consultas.isEmpty()) {
            throw new RuntimeException("Nao ha consultas da semana disponiveis para adiantar pagamento.");
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

    public List<Agendamento> listarAgendamentosPorOrderNsu(String orderNsu, Usuario usuarioLogado) {
        if (orderNsu == null || orderNsu.isBlank()) {
            throw new RuntimeException("Pedido de pagamento invalido.");
        }
        List<Agendamento> agendamentos = repository.findAllByPagamentoOrderNsuOrderByDataHoraInicioAsc(orderNsu);
        if (agendamentos.isEmpty()) {
            throw new RuntimeException("Pedido de pagamento nao encontrado.");
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

    public boolean isPedidoPagamentoLote(String orderNsu) {
        return isPedidoPagamentoSemana(orderNsu) || isPedidoPagamentoDia(orderNsu);
    }

    public LocalDate dataProximoDiaPagamentoPendente() {
        return LocalDate.now().plusDays(1);
    }

    public String rotuloProximoDiaPagamentoPendente() {
        return dataProximoDiaPagamentoPendente().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    public List<Agendamento> listarPagamentosPendentesProximoDia(Usuario usuarioLogado) {
        LocalDate hoje = LocalDate.now();
        LocalDate amanha = dataProximoDiaPagamentoPendente();
        return listarPendenciasObrigatoriasParaBloqueio(usuarioLogado).stream()
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
                throw new RuntimeException("Consulta invalida ou nao disponivel para pagamento do proximo dia.");
            }
            Agendamento consulta = buscarComPermissao(agendamentoId, usuarioLogado);
            if (consultaJaFoiPaga(consulta)) {
                throw new RuntimeException("Uma das consultas selecionadas ja esta paga.");
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

        return repository.findByProfissionalIdOrderByDataHoraInicioAsc(usuarioLogado.getId()).stream()
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !consultaJaFoiPaga(agendamento))
                .filter(this::aindaExigeBloqueioPorPagamento)
                .toList();
    }

    /**
     * Bloqueio alinhado com o que ainda pode ser quitado (mesma regra da aba Pagamentos pendentes + PIX ativo).
     */
    private boolean aindaExigeBloqueioPorPagamento(Agendamento agendamento) {
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
        List<Agendamento> pendencias = listarPendenciasObrigatoriasParaBloqueio(usuarioLogado);
        if (pendencias.isEmpty()) {
            return "Voce tem pagamento pendente. Quite o pagamento para voltar a usar a sala e marcar novos agendamentos.";
        }
        Agendamento proximaPendencia = pendencias.get(0);
        String data = proximaPendencia.getDataHoraInicio() != null
                ? proximaPendencia.getDataHoraInicio().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM"))
                : null;
        if (data == null) {
            return "Voce tem pagamento pendente. Quite o pagamento para voltar a usar a sala e marcar novos agendamentos.";
        }
        return "Voce precisa pagar o agendamento do dia " + data
                + " (no dia anterior) para voltar a usar sala e fazer novo agendamento.";
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
        PagamentoStatus status = agendamento.getStatusPagamento();
        if (status == null) {
            return "Sem pagamento";
        }
        return switch (status) {
            case PAGO -> "Pago";
            case ESPERANDO_CONFIRMACAO -> agendamento.possuiQrPagamentoAtivo()
                    ? "Esperando confirmacao (" + agendamento.getTempoRestantePagamentoFormatado() + ")"
                    : "Confirmacao expirada";
            case AGUARDANDO_PAGAMENTO -> bloqueadoPorPagamento(agendamento)
                    ? "Nao pago - sala bloqueada"
                    : "Aguardando pagamento";
            case PAGAMENTO_FUTURO -> "Pagamento em " + formatarDiaPagamento(agendamento);
            case LIBERADO_FALTA_PAGAMENTO -> vagaPreenchidaPorOutroProfissional(agendamento)
                    ? "Vaga preenchida por outro profissional"
                    : "Vaga liberada — voce ainda pode pagar para recuperar";
        };
    }

    public boolean deveExibirPillPendenteNaGrade(Agendamento agendamento) {
        return agendamento != null && agendamento.isReservaPendenteNaGrade();
    }

    public String rotuloEsperandoNaGrade(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null) {
            return "";
        }
        return agendamento.rotuloPendenteNaGrade(podeVerPagamento(agendamento, usuarioLogado));
    }

    public String rotuloPagoNaGrade(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null) {
            return "";
        }
        return agendamento.rotuloPagoNaGrade(podeVerPagamento(agendamento, usuarioLogado));
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

    private String formatarDiaPagamento(Agendamento agendamento) {
        if (agendamento.getDataHoraInicio() == null) {
            return "—";
        }
        return agendamento.getDataHoraInicio().toLocalDate().minusDays(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
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
            throw new RuntimeException("Prazo final para pagar este horario foi encerrado.");
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
            throw new RuntimeException("Pedido de pagamento nao confere com a consulta.");
        }
        if (modoTeste) {
            if (vagaPreenchidaPorOutroProfissional(agendamento)) {
                throw new HorarioJaReservadoPorOutroProfissionalException(formatarDetalheHorario(agendamento));
            }
            return;
        }
        if (!PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException("Pagamento nao esta aguardando confirmacao.");
        }
        if (!agendamento.possuiQrPagamentoAtivo()) {
            throw new RuntimeException("Pagamento expirado ou invalido.");
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
            throw new RuntimeException("Valor da taxa da clinica invalido para pagamento.");
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
            throw new RuntimeException("Voce so pode pagar os seus proprios agendamentos.");
        }
        return agendamento;
    }
}
