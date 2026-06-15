package com.clinica.sistema.service;

import com.clinica.sistema.config.InfinitePayProperties;
import com.clinica.sistema.config.PagamentoProperties;
import com.clinica.sistema.dto.LinkPagamentoGerado;
import com.clinica.sistema.dto.PagamentoProfissionalNotificacaoView;
import com.clinica.sistema.dto.PendenciasPagamentoWhatsappAgrupadasView;
import com.clinica.sistema.dto.ProfissionalBloqueioPagamentoView;
import com.clinica.sistema.dto.ProfissionalPendenciaPagamentoWhatsappView;
import com.clinica.sistema.dto.ResumoPendenciasPagamentoView;
import com.clinica.sistema.exception.HorarioJaReservadoPorOutroProfissionalException;
import com.clinica.sistema.exception.PagamentoWebhookNaoAutorizadoException;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.security.ClinicaAuthenticationSuccessHandler;
import com.clinica.sistema.util.WhatsAppNumeroUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import jakarta.servlet.http.HttpSession;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Service
public class PagamentoConsultaService {

    private static final String CARGO_PROFISSIONAL = "ROLE_PROFISSIONAL";
    private static final List<PagamentoStatus> STATUS_CANDIDATOS_LIBERACAO = List.of(
            PagamentoStatus.PAGAMENTO_FUTURO,
            PagamentoStatus.AGUARDANDO_PAGAMENTO
    );
    private static final DateTimeFormatter FORMATO_DATA_COMPLETA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String URL_MEUS_PAGAMENTOS_DIA = "/agendamentos/meus-pagamentos#pagamentos-pendentes";
    private static final String URL_MEUS_PAGAMENTOS_SEMANA = "/agendamentos/meus-pagamentos#pagamentos-semana";
    private static final String URL_MEUS_PAGAMENTOS_MES = "/agendamentos/meus-pagamentos#pagamentos-mes";
    /** Evita SELECT de todo o historico do profissional em cada tela. */
    private static final int MESES_PASSADO_JANELA_PAGAMENTO = 4;
    private static final int MESES_FUTURO_JANELA_PAGAMENTO = 3;

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
            for (int i = 0; i < novosAgendamentos.size(); i++) {
                Agendamento agendamento = novosAgendamentos.get(i);
                if (i == 0 && agendamento.isIndicacaoDona()) {
                    iniciarAguardandoAprovacaoIndicacao(agendamento);
                } else {
                    agendamento.setStatusPagamento(PagamentoStatus.PAGO);
                }
            }
            return;
        }
        if (isAgendadoPorGestorParaOutroProfissional(profissional, usuarioLogado)) {
            for (int i = 0; i < novosAgendamentos.size(); i++) {
                Agendamento agendamento = novosAgendamentos.get(i);
                if (i == 0) {
                    if (agendamento.isIndicacaoDona()) {
                        iniciarAguardandoAprovacaoIndicacao(agendamento);
                    } else {
                        marcarComoPagoPorAcordoGestor(agendamento);
                    }
                } else {
                    aplicarStatusPagamentoSerie(agendamento, profissional);
                }
            }
            return;
        }
        if (resolverPeriodicidade(profissional) != PeriodicidadePagamento.DIARIO) {
            for (int i = 0; i < novosAgendamentos.size(); i++) {
                Agendamento agendamento = novosAgendamentos.get(i);
                if (i == 0 && agendamento.isIndicacaoDona()) {
                    iniciarAguardandoAprovacaoIndicacao(agendamento);
                } else {
                    agendamento.setStatusPagamento(PagamentoStatus.PAGAMENTO_FUTURO);
                }
            }
            return;
        }
        for (int i = 0; i < novosAgendamentos.size(); i++) {
            Agendamento agendamento = novosAgendamentos.get(i);
            if (i == 0) {
                if (agendamento.isIndicacaoDona()) {
                    iniciarAguardandoAprovacaoIndicacao(agendamento);
                } else {
                    iniciarConfirmacaoPagamento(agendamento);
                }
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
            if (PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())) {
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
    public Agendamento prepararEscolhaFormaPagamento(Long agendamentoId, Usuario usuarioLogado) {
        Agendamento agendamento = buscarComPermissao(agendamentoId, usuarioLogado);
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException("Esta consulta já está paga.");
        }
        if (vagaPreenchidaPorOutroProfissional(agendamento)) {
            throw new HorarioJaReservadoPorOutroProfissionalException(formatarDetalheHorario(agendamento));
        }
        validarRecuperacaoPagamento(agendamento);
        if (PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException(
                    "Esta indicação aguarda aprovação da Polyana antes de gerar o pagamento PIX."
            );
        }
        if (!podePagarAgora(agendamento)
                && !agendamento.possuiQrPagamentoAtivo()
                && !PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException("Esta consulta não está disponível para pagamento.");
        }
        return agendamento;
    }

    @Transactional
    public Agendamento pagarAgora(Long agendamentoId, Usuario usuarioLogado) {
        Agendamento agendamento = prepararEscolhaFormaPagamento(agendamentoId, usuarioLogado);
        if (agendamento.possuiQrPagamentoAtivo()) {
            return repository.save(agendamento);
        }
        if (PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())) {
            return repository.save(agendamento);
        }
        iniciarConfirmacaoPagamento(agendamento);
        return repository.save(agendamento);
    }

    public void validarAutenticacaoWebhook(String secretRecebido) {
        String secretConfigurado = pagamentoProperties.getWebhookSecret();
        if (secretConfigurado == null || secretConfigurado.isBlank()) {
            return;
        }
        if (secretRecebido == null || secretRecebido.isBlank()) {
            // InfinitePay nao envia header customizado; confirmacao via payment_check.
            return;
        }
        if (!secretConfigurado.equals(secretRecebido.trim())) {
            throw new PagamentoWebhookNaoAutorizadoException();
        }
    }

    public void processarNotificacaoInfinitePay(Map<String, Object> payload, String webhookSecret) {
        validarAutenticacaoWebhook(webhookSecret);
        String orderNsu = extrairCampoTexto(payload, "order_nsu", "orderNSU", "orderNsu");
        if (orderNsu == null) {
            throw new RuntimeException("order_nsu obrigatorio no webhook InfinitePay.");
        }
        if (!infinitePayProperties.isModoTeste()) {
            String slugPayload = extrairCampoTexto(payload, "slug", "invoice_slug", "invoiceSlug");
            String transactionNsu = extrairCampoTexto(payload, "transaction_nsu", "transactionNsu");
            String slugBanco = buscarSlugPagamentoPorOrderNsu(orderNsu);
            String slug = slugPayload != null ? slugPayload : slugBanco;
            String link = buscarLinkPagamentoPorOrderNsu(orderNsu);
            boolean confirmado = infinitePayService.consultarPagamentoConfirmado(
                    orderNsu,
                    slug,
                    link,
                    transactionNsu
            );
            if (!confirmado && !payloadIndicaPagamentoAprovado(payload)) {
                throw new RuntimeException("Pagamento ainda nao confirmado na InfinitePay.");
            }
        }
        confirmarPagamentoPorWebhook(orderNsu);
    }

    @Transactional
    public Agendamento sincronizarPagamentoComInfinitePay(Long agendamentoId, Usuario usuarioLogado) {
        Agendamento agendamento = buscarComPermissao(agendamentoId, usuarioLogado);
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return agendamento;
        }
        if (!PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException("Nao ha pagamento aguardando confirmacao.");
        }
        String orderNsu = agendamento.getPagamentoOrderNsu();
        if (orderNsu == null || orderNsu.isBlank()) {
            throw new RuntimeException("Pedido de pagamento nao encontrado.");
        }
        if (infinitePayProperties.isModoTeste()) {
            throw new RuntimeException("Use \"Simular pagamento\" no modo teste local.");
        }
        String slugResolvido = infinitePayService.resolverSlugPagamento(
                agendamento.getPagamentoSlug(),
                agendamento.getPagamentoLink()
        );
        if (slugResolvido != null && !slugResolvido.equals(agendamento.getPagamentoSlug())) {
            agendamento.setPagamentoSlug(slugResolvido);
            repository.save(agendamento);
        }
        if (!infinitePayService.consultarPagamentoConfirmado(
                orderNsu,
                slugResolvido,
                agendamento.getPagamentoLink()
        )) {
            throw new RuntimeException(
                    "InfinitePay ainda nao confirmou este pagamento. "
                            + "Confira se o PIX caiu na conta certa e aguarde alguns segundos antes de tentar de novo."
            );
        }
        return confirmarPagamentoPorWebhook(orderNsu);
    }

    @Transactional
    public Agendamento confirmarPagamentoPorOrderNsu(String orderNsu) {
        return confirmarPagamentoPorOrderNsu(orderNsu, false, false);
    }

    @Transactional
    public Agendamento confirmarPagamentoPorOrderNsuModoTeste(String orderNsu) {
        return confirmarPagamentoPorOrderNsu(orderNsu, true, false);
    }

    @Transactional
    public Agendamento confirmarPagamentoPorWebhook(String orderNsu) {
        return confirmarPagamentoPorOrderNsu(orderNsu, false, true);
    }

    @Transactional
    private Agendamento confirmarPagamentoPorOrderNsu(String orderNsu, boolean modoTeste, boolean viaWebhook) {
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
            validarAntesConfirmarPagamento(agendamento, orderNsu, modoTeste, viaWebhook);
            ultimoPago = marcarComoPago(agendamento);
        }
        if (ultimoPago == null) {
            throw new RuntimeException("Nenhuma consulta pendente para confirmar neste pedido.");
        }
        return ultimoPago;
    }

    private String extrairCampoTexto(Map<String, Object> payload, String... chaves) {
        if (payload == null || chaves == null) {
            return null;
        }
        for (String chave : chaves) {
            Object valor = payload.get(chave);
            if (valor != null && !valor.toString().isBlank()) {
                return valor.toString().trim();
            }
        }
        return null;
    }

    private boolean payloadIndicaPagamentoAprovado(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        if (extrairCampoTexto(payload, "receipt_url", "receiptUrl") != null) {
            return true;
        }
        Object paidAmount = payload.get("paid_amount");
        if (paidAmount == null) {
            paidAmount = payload.get("paidAmount");
        }
        if (paidAmount == null) {
            return false;
        }
        String captureMethod = extrairCampoTexto(payload, "capture_method", "captureMethod");
        return captureMethod != null && !captureMethod.isBlank();
    }

    private String buscarSlugPagamentoPorOrderNsu(String orderNsu) {
        return repository.findAllByPagamentoOrderNsuOrderByDataHoraInicioAsc(orderNsu).stream()
                .map(Agendamento::getPagamentoSlug)
                .filter(slug -> slug != null && !slug.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String buscarLinkPagamentoPorOrderNsu(String orderNsu) {
        return repository.findAllByPagamentoOrderNsuOrderByDataHoraInicioAsc(orderNsu).stream()
                .map(Agendamento::getPagamentoLink)
                .filter(link -> link != null && !link.isBlank())
                .findFirst()
                .orElse(null);
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
        return LocalDate.now().getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /**
     * Semanal: há consultas da semana (ou da semana anterior em atraso) disponíveis para PIX em Meus pagamentos.
     */
    public boolean estaEmJanelaPagamentoSemanal(Usuario usuarioLogado) {
        if (usuarioLogado == null
                || authService.isAdmin(usuarioLogado)
                || authService.isDonaClinica(usuarioLogado)
                || authService.profissionalIgnoraValoresEPagamento(usuarioLogado)
                || resolverPeriodicidade(usuarioLogado) != PeriodicidadePagamento.SEMANAL) {
            return false;
        }
        return !listarConsultasPagamentoSemanalDisponiveis(usuarioLogado).isEmpty();
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
            return listarConsultasPagamentoSemanalDisponiveis(usuarioLogado);
        }
        if (periodicidade == PeriodicidadePagamento.MENSAL) {
            return Collections.emptyList();
        }

        PeriodoSemanaPagamento periodo = resolverPeriodoSemanaPagamento(LocalDate.now());
        LocalDate inicioSemana = periodo.inicio();
        LocalDate fimSemana = periodo.fim();

        return agendamentosDoProfissionalNaJanelaPagamento(usuarioLogado.getId()).stream()
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
        return listarConsultasPagamentoMensalPendentes(usuarioLogado);
    }

    public String rotuloMesPagamentoPendente() {
        return mesVigentePagamento().format(DateTimeFormatter.ofPattern("MM/yyyy"));
    }

    public boolean exibePagamentoMensalAgora(Usuario usuarioLogado) {
        return resolverPeriodicidade(usuarioLogado) == PeriodicidadePagamento.MENSAL;
    }

    public boolean estaEmJanelaPagamentoMensal() {
        return estaDentroJanelaPagamentoMensal(LocalDate.now());
    }

    /** Mensal: cobrança do mês vigente, dias 01 ao limite (padrão 10). */
    public boolean estaEmJanelaPagamentoMensal(Usuario usuarioLogado) {
        return estaEmJanelaPagamentoMensal();
    }

    public String rotuloJanelaPagamentoMensalAtual() {
        return "01 ao " + diaLimitePagamentoMensal() + "/"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("MM"));
    }

    public int getDiaLimitePagamentoMensal() {
        return diaLimitePagamentoMensal();
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
    public String gerarPagamentoUnicoMesVigente(Usuario usuarioLogado) {
        if (resolverPeriodicidade(usuarioLogado) != PeriodicidadePagamento.MENSAL) {
            throw new RuntimeException("Pagamento mensal não se aplica a este profissional.");
        }
        List<Agendamento> consultas = listarConsultasPagamentoMensal(usuarioLogado);
        if (consultas.isEmpty()) {
            throw new RuntimeException("Não há consultas do mês vigente pendentes de pagamento.");
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
        return agendamentosDoProfissionalNaJanelaPagamento(usuarioLogado.getId()).stream()
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !consultaJaFoiPaga(agendamento))
                .filter(agendamento -> exibirNaListaPagamentosPendentes(agendamento, hoje, amanha))
                .sorted(java.util.Comparator.comparing(Agendamento::getDataHoraInicio))
                .toList();
    }

    private boolean exibirNaListaPagamentosPendentes(Agendamento agendamento, LocalDate hoje, LocalDate amanha) {
        if (indicacaoComTaxaPendente(agendamento)) {
            return atendimentoIndicacaoJaIniciou(agendamento);
        }
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
        return agendamentosDoProfissionalNaJanelaPagamento(usuarioLogado.getId()).stream()
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
        return listarConsultasPagamentoMensalPendentes(usuarioLogado).stream()
                .filter(agendamento -> mensalidadeVencida(agendamento, LocalDate.now()))
                .toList();
    }

    /**
     * Bloqueio alinhado com o que ainda pode ser quitado (mesma regra da aba Pagamentos pendentes + PIX ativo).
     */
    private boolean aindaExigeBloqueioPorPagamentoDiario(Agendamento agendamento) {
        if (consultaJaFoiPaga(agendamento)) {
            return false;
        }
        if (aguardandoConfirmacaoDinheiroVencida(agendamento)) {
            return true;
        }
        if (bloqueiaAgendaPorIndicacaoNaoPaga(agendamento)) {
            return true;
        }
        if (agendamento.possuiQrPagamentoAtivo()) {
            return true;
        }
        if (indicacaoComTaxaPendente(agendamento)) {
            return false;
        }
        LocalDate hoje = LocalDate.now();
        LocalDate amanha = hoje.plusDays(1);
        return exibirNaListaPagamentosPendentes(agendamento, hoje, amanha);
    }

    private boolean aguardandoConfirmacaoDinheiroVencida(Agendamento agendamento) {
        return PagamentoStatus.AGUARDANDO_CONFIRMACAO_DINHEIRO.equals(agendamento.getStatusPagamento())
                && agendamento.confirmacaoDinheiroVencida();
    }

    public boolean profissionalBloqueadoPorPendenciaPagamento(Usuario usuarioLogado) {
        return !listarPendenciasObrigatoriasParaBloqueio(usuarioLogado).isEmpty();
    }

    public String mensagemBloqueioPagamento(Usuario usuarioLogado) {
        if (temQrPagamentoAtivo(usuarioLogado)) {
            return "Você tem um PIX aguardando confirmação. Quite o pagamento para voltar a usar a sala.";
        }

        return switch (resolverPeriodicidade(usuarioLogado)) {
            case SEMANAL -> "Você precisa quitar a semana anterior (prazo era domingo) "
                    + "para voltar a usar a sala e fazer novo agendamento.";
            case MENSAL -> "Você passou do dia " + diaLimitePagamentoMensal() + " sem pagar o mês vigente. "
                    + "Quite o pagamento para voltar a usar a sala e fazer novo agendamento.";
            case DIARIO -> mensagemBloqueioPagamentoDiario(usuarioLogado);
        };
    }

    private String mensagemBloqueioPagamentoDiario(Usuario usuarioLogado) {
        List<Agendamento> pendencias = listarPendenciasDiariasParaBloqueio(usuarioLogado);
        if (pendencias.isEmpty()) {
            return "Você tem pagamento pendente. Quite o pagamento para voltar a usar a sala e marcar novos agendamentos.";
        }
        String resumoValores = montarResumoPendenciasComValor(usuarioLogado, pendencias);
        if (!resumoValores.isBlank()) {
            return "Você tem pagamento(s) pendente(s): " + resumoValores
                    + ". Quite para voltar a usar a sala e fazer novo agendamento.";
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

    private String montarResumoPendenciasComValor(Usuario usuarioLogado, List<Agendamento> pendencias) {
        return pendencias.stream()
                .map(agendamento -> {
                    String data = agendamento.getDataHoraInicio() != null
                            ? agendamento.getDataHoraInicio().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM"))
                            : "—";
                    return data + " = " + formatarValorTaxaPix(agendamento);
                })
                .collect(Collectors.joining(" · "));
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

    public void adicionarResumoPendenciasPagamentoAoModel(Model model, Usuario usuarioLogado, HttpSession session) {
        try {
            ResumoPendenciasPagamentoView resumo = montarResumoPendenciasPagamento(usuarioLogado);
            model.addAttribute("resumoPendenciasPagamento", resumo);
            boolean exibirModal = resumo.quantidade() > 0
                    && !temQrPagamentoAtivo(usuarioLogado)
                    && exibirModalPendenciasPagamentoEntrada(session);
            model.addAttribute("exibirModalPendenciasPagamento", exibirModal);
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(PagamentoConsultaService.class)
                    .warn("Nao foi possivel montar resumo de pendencias: {}", ex.getMessage());
            model.addAttribute("resumoPendenciasPagamento", ResumoPendenciasPagamentoView.vazio());
            model.addAttribute("exibirModalPendenciasPagamento", false);
        }
    }

    public void marcarLembretePendenciasPagamentoNoLogin(HttpSession session, Usuario usuarioLogado) {
        if (session == null || usuarioLogado == null) {
            return;
        }
        ResumoPendenciasPagamentoView resumo = montarResumoPendenciasPagamento(usuarioLogado);
        if (resumo.quantidade() > 0 && !temQrPagamentoAtivo(usuarioLogado)) {
            session.setAttribute(
                    ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_PENDENCIAS_PAGAMENTO,
                    Boolean.TRUE
            );
        }
    }

    public boolean exibirModalPendenciasPagamentoEntrada(HttpSession session) {
        if (session == null) {
            return false;
        }
        return Boolean.TRUE.equals(session.getAttribute(
                ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_PENDENCIAS_PAGAMENTO
        ));
    }

    public void dispensarLembretePendenciasPagamento(HttpSession session) {
        if (session != null) {
            session.removeAttribute(ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_PENDENCIAS_PAGAMENTO);
        }
    }

    @Transactional(readOnly = true)
    public ResumoPendenciasPagamentoView montarResumoPendenciasPagamento(Usuario usuarioLogado) {
        if (usuarioLogado == null
                || authService.isAdmin(usuarioLogado)
                || authService.isDonaClinica(usuarioLogado)
                || authService.profissionalIgnoraValoresEPagamento(usuarioLogado)) {
            return ResumoPendenciasPagamentoView.vazio();
        }
        List<Agendamento> consultas = listarConsultasPagamentoPendenteParaLembrete(usuarioLogado);
        if (consultas.isEmpty()) {
            return ResumoPendenciasPagamentoView.vazio();
        }
        PeriodicidadePagamento periodicidade = resolverPeriodicidade(usuarioLogado);
        int quantidade = consultas.size();
        String total = formatarTotalTaxaPix(consultas);
        String rotulo = rotuloPeriodoPendencias(periodicidade);
        String url = urlMeusPagamentosPorPeriodicidade(periodicidade);
        String mensagemResumo = montarMensagemResumoPendencias(periodicidade, quantidade, rotulo);
        String mensagemConvite = montarMensagemConvitePendencias(
                usuarioLogado,
                periodicidade,
                quantidade,
                total,
                rotulo
        );
        return new ResumoPendenciasPagamentoView(
                quantidade,
                total,
                "Pendências de pagamento",
                mensagemResumo,
                mensagemConvite,
                rotulo,
                url
        );
    }

    /**
     * Lista exibida na aba Pagamentos pendentes (diario): inclui PIX aguardando confirmacao,
     * alinhada ao contador do resumo de pendencias.
     */
    @Transactional(readOnly = true)
    public List<Agendamento> listarPagamentosPendentesExibicaoMeusPagamentos(Usuario usuarioLogado) {
        if (usuarioLogado == null
                || authService.isAdmin(usuarioLogado)
                || authService.isDonaClinica(usuarioLogado)
                || authService.profissionalIgnoraValoresEPagamento(usuarioLogado)
                || resolverPeriodicidade(usuarioLogado) != PeriodicidadePagamento.DIARIO) {
            return Collections.emptyList();
        }
        return listarConsultasPagamentoPendenteParaLembrete(usuarioLogado);
    }

    private List<Agendamento> listarConsultasPagamentoPendenteParaLembrete(Usuario usuarioLogado) {
        PeriodicidadePagamento periodicidade = resolverPeriodicidade(usuarioLogado);
        List<Agendamento> abertas = new ArrayList<>(switch (periodicidade) {
            case DIARIO -> listarPagamentosPendentesProximoDia(usuarioLogado);
            case SEMANAL -> listarConsultasAdiantamentoSemanaAtual(usuarioLogado);
            case MENSAL -> listarConsultasPagamentoMensal(usuarioLogado);
        });
        LinkedHashMap<Long, Agendamento> porId = new LinkedHashMap<>();
        for (Agendamento agendamento : abertas) {
            if (agendamento.getId() != null) {
                porId.put(agendamento.getId(), agendamento);
            }
        }
        for (Agendamento agendamento : listarPendenciasObrigatoriasParaBloqueio(usuarioLogado)) {
            if (agendamento.getId() != null) {
                porId.putIfAbsent(agendamento.getId(), agendamento);
            }
        }
        return porId.values().stream()
                .sorted(Comparator.comparing(
                        Agendamento::getDataHoraInicio,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();
    }

    private String rotuloPeriodoPendencias(PeriodicidadePagamento periodicidade) {
        return switch (periodicidade) {
            case DIARIO -> rotuloProximoDiaPagamentoPendente();
            case SEMANAL -> rotuloPeriodoSemanaAtual();
            case MENSAL -> rotuloMesPagamentoPendente();
        };
    }

    private String montarMensagemResumoPendencias(
            PeriodicidadePagamento periodicidade,
            int quantidade,
            String rotuloPeriodo
    ) {
        String consultas = quantidade == 1 ? "1 item pendente" : quantidade + " itens pendentes";
        return switch (periodicidade) {
            case DIARIO -> "Você tem " + consultas + " de taxa de sala (próximo dia: " + rotuloPeriodo + ").";
            case SEMANAL -> "Você tem " + consultas + " da semana " + rotuloPeriodo + " ainda em aberto.";
            case MENSAL -> "Você tem " + consultas + " do mês vigente (" + rotuloPeriodo + ") ainda em aberto.";
        };
    }

    private String montarMensagemConvitePendencias(
            Usuario usuarioLogado,
            PeriodicidadePagamento periodicidade,
            int quantidade,
            String totalFormatado,
            String rotuloPeriodo
    ) {
        if (temQrPagamentoAtivo(usuarioLogado)) {
            return "Você tem um PIX aguardando confirmação. Total em aberto: "
                    + totalFormatado
                    + ". Confirme o pagamento para liberar a agenda.";
        }
        StringBuilder mensagem = new StringBuilder(
                montarMensagemResumoPendencias(periodicidade, quantidade, rotuloPeriodo)
        );
        mensagem.append(" Total: ").append(totalFormatado).append('.');
        if (profissionalBloqueadoPorPendenciaPagamento(usuarioLogado)) {
            mensagem.append(" No momento, novos agendamentos estão bloqueados até quitar.");
        }
        return mensagem.toString();
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
        if (!estaEmJanelaPagamentoSemanal(usuarioLogado)) {
            return Optional.empty();
        }
        List<Agendamento> consultas = listarConsultasAdiantamentoSemanaAtual(usuarioLogado);
        if (consultas.isEmpty()) {
            return Optional.empty();
        }
        String periodo = rotuloPeriodoSemanaAtual();
        return Optional.of(new PagamentoProfissionalNotificacaoView(
                "Pagamento da semana",
                "Não esqueça de pagar a semana " + periodo + " até domingo para evitar bloqueio na segunda.",
                periodo,
                URL_MEUS_PAGAMENTOS_SEMANA
        ));
    }

    private Optional<PagamentoProfissionalNotificacaoView> montarNotificacaoPagamentoMensal(Usuario usuarioLogado) {
        if (!estaEmJanelaPagamentoMensal(usuarioLogado)) {
            return Optional.empty();
        }
        List<Agendamento> consultas = listarConsultasPagamentoMensal(usuarioLogado);
        if (consultas.isEmpty()) {
            return Optional.empty();
        }
        String mesReferencia = rotuloMesPagamentoPendente();
        return Optional.of(new PagamentoProfissionalNotificacaoView(
                "Pagamento mensal",
                "Não esqueça de pagar o mês " + mesReferencia + " até o dia "
                        + diaLimitePagamentoMensal() + " para evitar bloqueio.",
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

    @Transactional(readOnly = true)
    public List<ProfissionalPendenciaPagamentoWhatsappView> listarProfissionaisComPendenciaPagamentoWhatsapp() {
        return usuarioRepository.findByCargoOrderByNomeAsc(CARGO_PROFISSIONAL).stream()
                .filter(profissional -> !authService.profissionalIgnoraValoresEPagamento(profissional))
                .map(this::montarLinhaPendenciaPagamentoWhatsapp)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(
                        ProfissionalPendenciaPagamentoWhatsappView::nome,
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PendenciasPagamentoWhatsappAgrupadasView agruparProfissionaisComPendenciaPagamentoWhatsapp() {
        return agruparProfissionaisMensagemWhatsappPorPeriodicidade();
    }

    @Transactional(readOnly = true)
    public PendenciasPagamentoWhatsappAgrupadasView agruparProfissionaisMensagemWhatsappPorPeriodicidade() {
        return new PendenciasPagamentoWhatsappAgrupadasView(
                listarProfissionaisMensagemWhatsappPorPeriodicidade(PeriodicidadePagamento.DIARIO),
                listarProfissionaisMensagemWhatsappPorPeriodicidade(PeriodicidadePagamento.SEMANAL),
                listarProfissionaisMensagemWhatsappPorPeriodicidade(PeriodicidadePagamento.MENSAL)
        );
    }

    @Transactional(readOnly = true)
    public List<ProfissionalPendenciaPagamentoWhatsappView> listarProfissionaisMensagemWhatsappPorPeriodicidade(
            PeriodicidadePagamento periodicidade
    ) {
        return usuarioRepository.findByCargoOrderByNomeAsc(CARGO_PROFISSIONAL).stream()
                .filter(profissional -> !authService.profissionalIgnoraValoresEPagamento(profissional))
                .filter(profissional -> resolverPeriodicidade(profissional) == periodicidade)
                .map(this::montarLinhaProfissionalMensagemWhatsapp)
                .sorted(Comparator.comparing(
                        ProfissionalPendenciaPagamentoWhatsappView::nome,
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();
    }

    public int contarProfissionaisComPendencia(PendenciasPagamentoWhatsappAgrupadasView agrupadas) {
        if (agrupadas == null) {
            return 0;
        }
        return (int) (agrupadas.diario().stream().filter(l -> l.quantidadePendencias() > 0).count()
                + agrupadas.semanal().stream().filter(l -> l.quantidadePendencias() > 0).count()
                + agrupadas.mensal().stream().filter(l -> l.quantidadePendencias() > 0).count());
    }

    private List<ProfissionalPendenciaPagamentoWhatsappView> filtrarPendenciasPorPeriodicidade(
            List<ProfissionalPendenciaPagamentoWhatsappView> lista,
            PeriodicidadePagamento periodicidade
    ) {
        return lista.stream()
                .filter(linha -> linha.periodicidade() == periodicidade)
                .toList();
    }

    @Transactional(readOnly = true)
    public Usuario buscarProfissionalParaAvisoWhatsapp(Long profissionalId) {
        return usuarioRepository.findById(profissionalId)
                .filter(profissional -> CARGO_PROFISSIONAL.equals(profissional.getCargo()))
                .filter(profissional -> !authService.profissionalIgnoraValoresEPagamento(profissional))
                .orElseThrow(() -> new RuntimeException("Profissional nao encontrado."));
    }

    @Transactional(readOnly = true)
    public String montarTextoWhatsappPendenciaPagamento(Usuario profissional) {
        ResumoPendenciasPagamentoView resumo = montarResumoPendenciasPagamento(profissional);
        if (!resumo.temPendencias()) {
            throw new RuntimeException("Este profissional nao tem pendencias de pagamento no momento.");
        }
        return montarTextoWhatsappPendenciaPagamento(profissional, resumo);
    }

    public String montarTextoWhatsappPendenciaPagamentoTeste(
            String nomeProfissional,
            PeriodicidadePagamento periodicidade
    ) {
        return aplicarVariaveisFraseWhatsapp(
                frasePadraoWhatsappPendencia(periodicidade),
                nomeProfissional,
                "R$ 50,00"
        );
    }

    public String frasePadraoWhatsappGeral() {
        return "Olá {nome}, você tem pendência(s) de taxa de sala na Agenda Afetto. "
                + "Total: {total}. Acesse Meus pagamentos para regularizar.";
    }

    public String frasePadraoWhatsappPendencia(PeriodicidadePagamento periodicidade) {
        PeriodicidadePagamento forma = periodicidade != null
                ? periodicidade
                : PeriodicidadePagamento.DIARIO;
        String rotulo = rotuloPeriodoPendencias(forma);
        return "Olá {nome}, "
                + montarMensagemResumoPendencias(forma, 2, rotulo)
                + " Total: {total}. Acesse Meus pagamentos na Agenda Afetto.";
    }

    public String aplicarVariaveisFraseWhatsapp(String template, String nome, String total) {
        if (template == null || template.isBlank()) {
            throw new RuntimeException("Informe o texto da mensagem.");
        }
        String nomeAplicado = nome == null || nome.isBlank() ? "Profissional" : nome.trim();
        String totalAplicado = total == null || total.isBlank() ? "R$ 0,00" : total.trim();
        return template
                .replace("{nome}", nomeAplicado)
                .replace("{total}", totalAplicado);
    }

    private Optional<ProfissionalPendenciaPagamentoWhatsappView> montarLinhaPendenciaPagamentoWhatsapp(
            Usuario profissional
    ) {
        ResumoPendenciasPagamentoView resumo = montarResumoPendenciasPagamento(profissional);
        if (!resumo.temPendencias()) {
            return Optional.empty();
        }
        return Optional.of(montarLinhaProfissionalMensagemWhatsapp(profissional));
    }

    private ProfissionalPendenciaPagamentoWhatsappView montarLinhaProfissionalMensagemWhatsapp(
            Usuario profissional
    ) {
        ResumoPendenciasPagamentoView resumo = montarResumoPendenciasPagamento(profissional);
        String telefone = profissional.getTelefoneWhatsappFormulario();
        boolean temWhatsapp = telefone != null && !telefone.isBlank();
        PeriodicidadePagamento periodicidade = resolverPeriodicidade(profissional);
        String mensagem = resumo.temPendencias()
                ? montarTextoWhatsappPendenciaPagamento(profissional, resumo)
                : aplicarVariaveisFraseWhatsapp(
                        frasePadraoWhatsappGeral(),
                        profissional.getNome(),
                        "R$ 0,00"
                );
        String urlWhatsapp = WhatsAppNumeroUtil.normalizarDestinatario(telefone)
                .map(numero -> "https://wa.me/"
                        + numero
                        + "?text="
                        + URLEncoder.encode(mensagem, StandardCharsets.UTF_8))
                .orElse(null);
        return new ProfissionalPendenciaPagamentoWhatsappView(
                profissional.getId(),
                profissional.getNome(),
                profissional.getLogin(),
                telefone,
                resumo.quantidade(),
                resumo.temPendencias() ? resumo.valorTotalFormatado() : "Em dia",
                mensagem,
                urlWhatsapp,
                periodicidade,
                periodicidade.getRotulo(),
                profissionalBloqueadoPorPendenciaPagamento(profissional),
                temWhatsapp
        );
    }

    private String montarTextoWhatsappPendenciaPagamento(
            Usuario profissional,
            ResumoPendenciasPagamentoView resumo
    ) {
        String detalhe = avaliarNotificacaoPagamentoProfissional(profissional)
                .map(PagamentoProfissionalNotificacaoView::getMensagemPainel)
                .filter(mensagem -> mensagem != null && !mensagem.isBlank())
                .orElse(resumo.mensagemConvite());
        if (detalhe == null || detalhe.isBlank()) {
            detalhe = resumo.mensagemResumo();
        }
        return "Olá "
                + profissional.getNome().trim()
                + ", "
                + detalhe
                + " Acesse Meus pagamentos na Agenda Afetto.";
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
            if (indicacaoComTaxaPendente(agendamento)) {
                return atendimentoIndicacaoJaIniciou(agendamento);
            }
            return dentroJanelaRecuperacao(agendamento) && !outroProfissionalOcupouVaga(agendamento);
        }
        if (indicacaoComTaxaPendente(agendamento)) {
            return atendimentoIndicacaoJaIniciou(agendamento);
        }
        if (PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        if (!agendamento.getDataHoraInicio().isAfter(LocalDateTime.now())) {
            return false;
        }
        PagamentoStatus status = agendamento.getStatusPagamento();
        if (status == PagamentoStatus.ESPERANDO_CONFIRMACAO && !agendamento.possuiQrPagamentoAtivo()) {
            return agendamento.getDataHoraInicio().isAfter(LocalDateTime.now());
        }
        return status == null
                || status == PagamentoStatus.PAGAMENTO_FUTURO
                || status == PagamentoStatus.AGUARDANDO_PAGAMENTO;
    }

    public boolean isConsultaIndicacao(Agendamento agendamento) {
        return agendamento != null && agendamento.isIndicacaoDona();
    }

    public String mensagemIndicacaoMeusPagamentos(Agendamento agendamento) {
        if (!isConsultaIndicacao(agendamento)) {
            return "";
        }
        if (PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO.equals(agendamento.getStatusPagamento())) {
            return "Aguardando a Polyana aprovar a indicação.";
        }
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return "";
        }
        if (indicacaoComTaxaPendente(agendamento) && !atendimentoIndicacaoJaIniciou(agendamento)) {
            return "Após o atendimento, você terá 2 dias para pagar a indicação em PIX.";
        }
        if (indicacaoComTaxaPendente(agendamento) && podePagarAgora(agendamento) && dentroJanelaPagamentoIndicacao(agendamento)) {
            return "Prazo aberto: pague a indicação em PIX.";
        }
        if (bloqueiaAgendaPorIndicacaoNaoPaga(agendamento)) {
            return "Prazo para pagar a indicação expirou. Quite para voltar a agendar.";
        }
        return "Você tem prazo para pagar a indicação: 2 dias após o atendimento.";
    }

    public boolean exibirMensagemIndicacaoEmMeusPagamentos(Agendamento agendamento) {
        if (!isConsultaIndicacao(agendamento)) {
            return false;
        }
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        if (agendamento.possuiQrPagamentoAtivo()) {
            return false;
        }
        return !podePagarAgora(agendamento);
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
        return agendamentoOcupaHorarioParaNovaReserva(agendamento);
    }

    /** Mesma regra da grade: horarios liberados, QR expirado ou serie encerrada nao bloqueiam nova reserva. */
    public boolean agendamentoOcupaHorarioParaNovaReserva(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return false;
        }
        if (agendamento.getSerieEncerradaEm() != null) {
            return false;
        }
        if (PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        if (PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())
                && !agendamento.possuiQrPagamentoAtivo()) {
            return false;
        }
        if (PagamentoStatus.AGUARDANDO_CONFIRMACAO_DINHEIRO.equals(agendamento.getStatusPagamento())
                && agendamento.confirmacaoDinheiroVencida()) {
            return false;
        }
        return exibirNaGradeComoReservado(agendamento);
    }

    public boolean podeVerPagamento(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null) {
            return false;
        }
        if (authService.podeVerPagamentoDeTodos(usuarioLogado)) {
            return true;
        }
        return agendamento.getProfissional() != null
                && agendamento.getProfissional().getId().equals(usuarioLogado.getId());
    }

    /** Status pago / aguardando pagamento na grade — somente ADM e dona da clinica. */
    public boolean podeVerStatusPagamentoReserva(Usuario usuarioLogado) {
        return authService.podeVerPagamentoDeTodos(usuarioLogado);
    }

    public boolean exibirNaoPagoNaGrade(Agendamento agendamento, Usuario usuarioLogado) {
        return bloqueadoPorPagamento(agendamento) && podeVerStatusPagamentoReserva(usuarioLogado);
    }

    /** Indicadores de pagamento (pago, nao pago, aguardando PIX) na grade — somente ADM e dona. */
    public boolean exibirIndicadoresPagamentoNaGrade(Agendamento agendamento, Usuario usuarioLogado) {
        return podeVerStatusPagamentoReserva(usuarioLogado);
    }

    public boolean modoTestePagamento() {
        return infinitePayProperties.isModoTeste();
    }

    public int prazoConfirmacaoMinutos() {
        return pagamentoProperties.getPrazoConfirmacaoMinutos();
    }

    public boolean abrirCheckoutMesmaAba() {
        return true;
    }

    public String rotuloBotaoCopiarPagamento(Agendamento agendamento) {
        if (modoTestePagamento()) {
            return "Copiar link da demonstração";
        }
        return exibirQrPixEmbutido(agendamento) ? "Copiar PIX copia e cola" : "Copiar link de pagamento";
    }

    public boolean exibirCheckoutInfinitePay(Agendamento agendamento) {
        if (agendamento == null || agendamento.getPagamentoLink() == null) {
            return false;
        }
        return exibirCheckoutInfinitePay(agendamento.getPagamentoLink());
    }

    public boolean exibirCheckoutInfinitePay(String pagamentoLink) {
        if (pagamentoLink == null || pagamentoLink.isBlank()) {
            return false;
        }
        return !ehPixCopiaCola(pagamentoLink.trim());
    }

    public boolean exibirQrPixEmbutido(Agendamento agendamento) {
        return agendamento != null && ehPixCopiaCola(agendamento.getPagamentoLink());
    }

    private boolean ehPixCopiaCola(String link) {
        return link != null && link.trim().startsWith("000201");
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
        if (usuarioLogado != null && !podeVerPagamento(agendamento, usuarioLogado)) {
            return rotuloStatusPagamentoVisaoColega(agendamento);
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
            case AGUARDANDO_CONFIRMACAO_DINHEIRO -> "Aguardando confirmação (PIX)";
            case AGUARDANDO_APROVACAO_INDICACAO -> "Aguardando aprovação (indicação)";
            case AGUARDANDO_PAGAMENTO -> bloqueadoPorPagamento(agendamento)
                    ? "Não pago — sala bloqueada"
                    : "Aguardando pagamento";
            case PAGAMENTO_FUTURO -> rotuloPagamentoFuturo(agendamento);
            case LIBERADO_FALTA_PAGAMENTO -> rotuloLiberadoFaltaPagamento(agendamento);
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
        if (status == PagamentoStatus.AGUARDANDO_CONFIRMACAO_DINHEIRO) {
            return "Esperando pagamento";
        }
        if (status == PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO) {
            return "Indicação — aguard. aprovação";
        }
        if (status == PagamentoStatus.ESPERANDO_CONFIRMACAO) {
            return "Esperando pagamento";
        }
        if (status == PagamentoStatus.AGUARDANDO_PAGAMENTO) {
            if (isIndicacaoAguardandoPix(agendamento)) {
                return dentroJanelaPagamentoIndicacao(agendamento)
                        ? "Indicação — aguard. PIX"
                        : "Indicação — prazo PIX expirado";
            }
            return "Aguardando pagamento";
        }
        if (status == PagamentoStatus.PAGAMENTO_FUTURO) {
            return rotuloPagamentoFuturo(agendamento);
        }
        if (status == PagamentoStatus.LIBERADO_FALTA_PAGAMENTO) {
            if (indicacaoComTaxaPendente(agendamento)) {
                return "Indicação — prazo PIX expirado";
            }
            return podeRecuperarVagaComPagamento(agendamento)
                    ? "Vaga liberada — pague para recuperar"
                    : "Pagamento expirado";
        }
        return "";
    }

    public boolean exibirSalaConfirmadaNaGrade(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || agendamento.isPagamentoPago()) {
            return false;
        }
        if (podeVerStatusPagamentoReserva(usuarioLogado)) {
            return false;
        }
        return reservaConfirmadaParaVisaoPublica(agendamento);
    }

    public boolean exibirEstiloAguardandoPagamentoNaGrade(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null) {
            return false;
        }
        if (!podeVerStatusPagamentoReserva(usuarioLogado)) {
            return false;
        }
        if (PagamentoStatus.AGUARDANDO_CONFIRMACAO_DINHEIRO.equals(agendamento.getStatusPagamento())
                && !agendamento.confirmacaoDinheiroVencida()) {
            return true;
        }
        if (PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO.equals(agendamento.getStatusPagamento())) {
            return true;
        }
        return agendamento.isReservaPendenteNaGrade()
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

    public String rotuloPagoNaGradeResumido(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null) {
            return "";
        }
        if (gestorVisualizandoAgendamentoDeOutro(agendamento, usuarioLogado)) {
            return "Confirmado";
        }
        return rotuloPagoNaGrade(agendamento, usuarioLogado);
    }

    public String rotuloEsperandoNaGradeResumido(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || !agendamento.isReservaPendenteNaGrade()) {
            return "";
        }
        if (gestorVisualizandoAgendamentoDeOutro(agendamento, usuarioLogado)) {
            String nome = resolverNomeProfissionalAgendamento(agendamento);
            PagamentoStatus status = agendamento.getStatusPagamento();
            if (status == PagamentoStatus.PAGAMENTO_FUTURO) {
                return nome + ": aguard. pagto";
            }
            if (status == PagamentoStatus.ESPERANDO_CONFIRMACAO
                    || status == PagamentoStatus.AGUARDANDO_PAGAMENTO) {
                return nome + ": aguard. PIX";
            }
            if (status == PagamentoStatus.LIBERADO_FALTA_PAGAMENTO) {
                return nome + ": vaga livre";
            }
            return nome + ": pendente";
        }
        String completo = rotuloEsperandoNaGrade(agendamento, usuarioLogado);
        if ("Vaga liberada — pague para recuperar".equals(completo)) {
            return "Vaga liberada";
        }
        return completo;
    }

    public String rotuloStatusPagamentoResumidoNaGrade(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null) {
            return "";
        }
        if (podeVerStatusPagamentoReserva(usuarioLogado)) {
            return rotuloStatusPagamentoResumido(agendamento, usuarioLogado);
        }
        return rotuloStatusPagamentoVisaoColega(agendamento);
    }

    public String rotuloStatusPagamentoResumido(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null) {
            return "Sem pagamento";
        }
        if (usuarioLogado != null && gestorVisualizandoAgendamentoDeOutro(agendamento, usuarioLogado)) {
            if (agendamento.isPagamentoPago()) {
                return "Confirmado";
            }
            if (agendamento.isReservaPendenteNaGrade()) {
                return rotuloEsperandoNaGradeResumido(agendamento, usuarioLogado);
            }
        }
        String completo = rotuloStatusPagamento(agendamento, usuarioLogado);
        if (completo != null && completo.startsWith("Esperando confirmação (")) {
            return "Esperando PIX";
        }
        if ("Não pago — sala bloqueada".equals(completo)) {
            return "Não pago";
        }
        if (completo != null && completo.startsWith("Vaga liberada —")) {
            return "Vaga liberada";
        }
        if ("Vaga preenchida por outro profissional".equals(completo)) {
            return "Vaga ocupada";
        }
        return completo;
    }

    private String rotuloStatusPagamentoVisaoColega(Agendamento agendamento) {
        if (agendamento.isPagamentoPago()) {
            return "Sala confirmada";
        }
        if (reservaConfirmadaParaVisaoPublica(agendamento)) {
            return "Sala confirmada";
        }
        if (agendamento.isReservaPendenteNaGrade()) {
            return "Aguardando confirmações";
        }
        return "";
    }

    private boolean gestorVisualizandoAgendamentoDeOutro(Agendamento agendamento, Usuario usuarioLogado) {
        if (agendamento == null || usuarioLogado == null) {
            return false;
        }
        if (!authService.podeVerPagamentoDeTodos(usuarioLogado)) {
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
                    return nomeProfissional + ": pagamento do dia 01 ao " + diaLimitePagamentoMensal();
                }
                YearMonth mesReferencia = resolverMesReferenciaCobranca(agendamento);
                if (mesReferencia != null) {
                    return nomeProfissional + ": pagamento do dia 01 ao " + diaLimitePagamentoMensal() + "/"
                            + mesReferencia.format(DateTimeFormatter.ofPattern("MM"));
                }
            }
            if (profissionalUsaPagamentoSemanal(agendamento)) {
                return nomeProfissional + ": semanal até " + formatarDomingoPagamentoSemanal(agendamento);
            }
            return nomeProfissional + ": pagamento na véspera";
        }
        if (status == PagamentoStatus.AGUARDANDO_CONFIRMACAO_DINHEIRO) {
            return nomeProfissional + ": aguard. PIX";
        }
        if (status == PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO) {
            return nomeProfissional + ": indicação — aguard. aprovação";
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

    private String rotuloLiberadoFaltaPagamento(Agendamento agendamento) {
        if (vagaPreenchidaPorOutroProfissional(agendamento)) {
            return "Vaga preenchida por outro profissional";
        }
        if (podeRecuperarVagaComPagamento(agendamento)) {
            return "Vaga liberada — você ainda pode pagar para recuperar";
        }
        return "Pagamento expirado";
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
        if (PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        if (PagamentoStatus.AGUARDANDO_CONFIRMACAO_DINHEIRO.equals(agendamento.getStatusPagamento())) {
            return agendamento.confirmacaoDinheiroVencida();
        }
        if (indicacaoComTaxaPendente(agendamento)) {
            return bloqueiaAgendaPorIndicacaoNaoPaga(agendamento);
        }
        LocalDate consulta = agendamento.getDataHoraInicio().toLocalDate();
        return !LocalDate.now().isBefore(consulta);
    }

    public boolean exibirNaGradeComoReservado(Agendamento agendamento) {
        if (agendamento == null) {
            return false;
        }
        return PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())
                || PagamentoStatus.AGUARDANDO_CONFIRMACAO_DINHEIRO.equals(agendamento.getStatusPagamento())
                || PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO.equals(agendamento.getStatusPagamento())
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
            return rotuloPagamentoFuturoSemanal(agendamento);
        }
        return "Pagamento em " + formatarDiaPagamentoDiario(agendamento);
    }

    String rotuloPagamentoFuturoMensal(Agendamento agendamento, LocalDate hoje) {
        if (cobrancaMensalVencendoNaData(agendamento, hoje)) {
            return "Você tem do dia 01 ao " + diaLimitePagamentoMensal() + " para pagar";
        }
        YearMonth mesReferencia = resolverMesReferenciaCobranca(agendamento);
        if (mesReferencia == null) {
            return "—";
        }
        return "Você vai pagar do dia 01 ao " + diaLimitePagamentoMensal() + "/"
                + mesReferencia.format(DateTimeFormatter.ofPattern("MM"));
    }

    private boolean cobrancaMensalVencendoNaData(Agendamento agendamento, LocalDate hoje) {
        if (!profissionalUsaPagamentoMensal(agendamento)) {
            return false;
        }
        if (!estaDentroJanelaPagamentoMensal(hoje)) {
            return false;
        }
        YearMonth mesReferencia = resolverMesReferenciaCobranca(agendamento);
        if (mesReferencia == null) {
            return false;
        }
        return mesReferencia.equals(YearMonth.from(hoje));
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
        return "até " + formatarDomingoPagamentoSemanal(agendamento);
    }

    public String rotuloDiaPagamentoMensalNaGrade(Agendamento agendamento) {
        return formatarJanelaPagamentoMensal(agendamento);
    }

    public String formatarJanelaPagamentoMensal(Agendamento agendamento) {
        YearMonth mesReferencia = resolverMesReferenciaCobranca(agendamento);
        if (mesReferencia == null) {
            return "—";
        }
        return "01 ao " + diaLimitePagamentoMensal() + "/"
                + mesReferencia.format(DateTimeFormatter.ofPattern("MM"));
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

    private String rotuloPagamentoFuturoSemanal(Agendamento agendamento) {
        return "Pague em Meus pagamentos (até " + formatarDomingoPagamentoSemanal(agendamento) + ")";
    }

    private String formatarDomingoPagamentoSemanal(Agendamento agendamento) {
        LocalDate domingo = resolverDomingoPagamentoSemanal(agendamento);
        if (domingo == null) {
            return "domingo";
        }
        return domingo.format(DateTimeFormatter.ofPattern("dd/MM"));
    }

    /**
     * Semanal: semana corrente sempre; inclui semana anterior em atraso (segunda a sábado) para quitar e desbloquear.
     */
    private List<Agendamento> listarConsultasPagamentoSemanalDisponiveis(Usuario profissional) {
        if (profissional == null || profissional.getId() == null) {
            return Collections.emptyList();
        }
        java.util.LinkedHashMap<Long, Agendamento> porId = new java.util.LinkedHashMap<>();
        PeriodoSemanaPagamento semanaAtual = resolverSemanaCorrenteParaCobranca(LocalDate.now());
        for (Agendamento agendamento : listarConsultasNaoPagasNoPeriodo(profissional, semanaAtual)) {
            if (agendamento.getId() != null) {
                porId.put(agendamento.getId(), agendamento);
            }
        }
        PeriodoSemanaPagamento semanaAnterior = resolverSemanaAnteriorEncerrada(LocalDate.now());
        if (semanaAnterior != null) {
            for (Agendamento agendamento : listarConsultasNaoPagasNoPeriodo(profissional, semanaAnterior)) {
                if (agendamento.getId() != null) {
                    porId.putIfAbsent(agendamento.getId(), agendamento);
                }
            }
        }
        return porId.values().stream()
                .sorted(java.util.Comparator.comparing(
                        Agendamento::getDataHoraInicio,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())
                ))
                .toList();
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

    private void validarAntesConfirmarPagamento(
            Agendamento agendamento,
            String orderNsu,
            boolean modoTeste,
            boolean viaWebhook
    ) {
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
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return;
        }
        if (!PagamentoStatus.ESPERANDO_CONFIRMACAO.equals(agendamento.getStatusPagamento())) {
            throw new RuntimeException("Pagamento não está aguardando confirmação.");
        }
        if (!viaWebhook && !agendamento.possuiQrPagamentoAtivo()) {
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

    private void iniciarAguardandoAprovacaoIndicacao(Agendamento agendamento) {
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return;
        }
        aplicarValoresTaxaAntesPagamento(agendamento);
        limparDadosPagamentoEmAberto(agendamento);
        agendamento.setIndicacaoAprovadaEm(null);
        agendamento.setStatusPagamento(PagamentoStatus.AGUARDANDO_APROVACAO_INDICACAO);
    }

    private boolean isIndicacaoAguardandoPix(Agendamento agendamento) {
        return agendamento != null
                && agendamento.isIndicacaoDona()
                && agendamento.isIndicacaoAprovadaPelaDona()
                && PagamentoStatus.AGUARDANDO_PAGAMENTO.equals(agendamento.getStatusPagamento());
    }

    private boolean indicacaoComTaxaPendente(Agendamento agendamento) {
        if (agendamento == null || !agendamento.isIndicacaoDona() || !agendamento.isIndicacaoAprovadaPelaDona()) {
            return false;
        }
        PagamentoStatus status = agendamento.getStatusPagamento();
        return PagamentoStatus.AGUARDANDO_PAGAMENTO.equals(status)
                || PagamentoStatus.LIBERADO_FALTA_PAGAMENTO.equals(status);
    }

    private boolean atendimentoIndicacaoJaIniciou(Agendamento agendamento) {
        return agendamento != null
                && agendamento.getDataHoraInicio() != null
                && !LocalDateTime.now().isBefore(agendamento.getDataHoraInicio());
    }

    private LocalDateTime limitePagamentoIndicacao(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return null;
        }
        int diasLimite = Math.max(1, pagamentoProperties.getIndicacaoDiasLimitePosAtendimento());
        return agendamento.getDataHoraInicio().toLocalDate()
                .plusDays(diasLimite)
                .atTime(LocalTime.of(23, 59, 59));
    }

    private boolean prazoIndicacaoExpirado(Agendamento agendamento) {
        LocalDateTime limite = limitePagamentoIndicacao(agendamento);
        return limite != null && LocalDateTime.now().isAfter(limite);
    }

    private boolean bloqueiaAgendaPorIndicacaoNaoPaga(Agendamento agendamento) {
        return indicacaoComTaxaPendente(agendamento)
                && atendimentoIndicacaoJaIniciou(agendamento)
                && prazoIndicacaoExpirado(agendamento);
    }

    private boolean dentroJanelaPagamentoIndicacao(Agendamento agendamento) {
        if (agendamento == null || agendamento.getDataHoraInicio() == null) {
            return false;
        }
        LocalDateTime limite = limitePagamentoIndicacao(agendamento);
        LocalDateTime agora = LocalDateTime.now();
        return !agora.isBefore(agendamento.getDataHoraInicio()) && !agora.isAfter(limite);
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
        return repository.findByProfissionalIdAndStatusPagamentoAndPagamentoExpiraEmAfterOrderByPagamentoExpiraEmAsc(
                profissional.getId(),
                PagamentoStatus.ESPERANDO_CONFIRMACAO,
                LocalDateTime.now()
        );
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
        return listarConsultasPagamentoMensalPendentes(profissional).stream()
                .anyMatch(agendamento -> mensalidadeVencida(agendamento, LocalDate.now()));
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
        return agendamentosDoProfissionalNaJanelaPagamento(profissional.getId()).stream()
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
        return agendamentosDoProfissionalNaJanelaPagamento(profissional.getId()).stream()
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

    private List<Agendamento> agendamentosDoProfissionalNaJanelaPagamento(Long profissionalId) {
        LocalDateTime desde = LocalDate.now().minusMonths(MESES_PASSADO_JANELA_PAGAMENTO).atStartOfDay();
        LocalDateTime ate = LocalDate.now().plusMonths(MESES_FUTURO_JANELA_PAGAMENTO).plusDays(1).atStartOfDay();
        return repository.findByProfissionalIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                profissionalId,
                desde,
                ate
        );
    }

    private int diaLimitePagamentoMensal() {
        int diaLimite = pagamentoProperties.getMensalDiaLimite();
        return diaLimite > 0 ? diaLimite : 10;
    }

    private YearMonth mesVigentePagamento() {
        return YearMonth.from(LocalDate.now());
    }

    private boolean estaDentroJanelaPagamentoMensal(LocalDate data) {
        int dia = data.getDayOfMonth();
        return dia >= 1 && dia <= diaLimitePagamentoMensal();
    }

    private List<Agendamento> listarConsultasPagamentoMensalPendentes(Usuario profissional) {
        if (profissional == null || profissional.getId() == null) {
            return Collections.emptyList();
        }
        YearMonth mesAtual = mesVigentePagamento();
        return agendamentosDoProfissionalNaJanelaPagamento(profissional.getId()).stream()
                .filter(agendamento -> agendamento.getDataHoraInicio() != null)
                .filter(agendamento -> !consultaJaFoiPaga(agendamento))
                .filter(agendamento -> {
                    YearMonth mesCobranca = resolverMesReferenciaCobranca(agendamento);
                    return mesCobranca != null && !mesCobranca.isAfter(mesAtual);
                })
                .toList();
    }

    private boolean mensalidadeVencida(Agendamento agendamento, LocalDate hoje) {
        if (!profissionalUsaPagamentoMensal(agendamento) || consultaJaFoiPaga(agendamento)) {
            return false;
        }
        YearMonth mesCobranca = resolverMesReferenciaCobranca(agendamento);
        if (mesCobranca == null) {
            return false;
        }
        YearMonth mesAtual = YearMonth.from(hoje);
        if (mesCobranca.isBefore(mesAtual)) {
            return true;
        }
        return mesCobranca.equals(mesAtual) && hoje.getDayOfMonth() > diaLimitePagamentoMensal();
    }

    /**
     * Converte agendamentos criados na fase experimental de "pagamento em dinheiro" para o fluxo PIX atual.
     */
    @Transactional
    public int migrarPagamentosDinheiroLegadosParaPix() {
        List<Agendamento> legados = repository.findByStatusPagamentoOrderByDataHoraInicioAsc(
                PagamentoStatus.AGUARDANDO_CONFIRMACAO_DINHEIRO
        );
        if (legados.isEmpty()) {
            return 0;
        }
        int migrados = 0;
        for (Agendamento agendamento : legados) {
            if (agendamento.confirmacaoDinheiroVencida()) {
                continue;
            }
            agendamento.setConfirmacaoDinheiroLimiteEm(null);
            try {
                iniciarConfirmacaoPagamento(agendamento);
                migrados++;
            } catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(PagamentoConsultaService.class)
                        .warn(
                                "Agendamento {}: nao foi possivel migrar dinheiro legado para PIX — {}",
                                agendamento.getId(),
                                ex.getMessage()
                        );
            }
        }
        if (migrados > 0) {
            repository.saveAll(legados);
        }
        return migrados;
    }

    @Transactional
    public int processarPagamentosDinheiroLegadosVencidos() {
        List<Agendamento> legados = repository.findByStatusPagamentoOrderByDataHoraInicioAsc(
                PagamentoStatus.AGUARDANDO_CONFIRMACAO_DINHEIRO
        );
        int processados = 0;
        for (Agendamento agendamento : legados) {
            if (!agendamento.confirmacaoDinheiroVencida()) {
                continue;
            }
            agendamento.setConfirmacaoDinheiroLimiteEm(null);
            liberarPorFaltaPagamento(agendamento);
            processados++;
        }
        return processados;
    }

    public String formatarDetalheHorarioPublico(Agendamento agendamento) {
        return formatarDetalheHorario(agendamento);
    }

    public List<Agendamento> listarConsultasAdiantamentoSemanaAtualPublico(Usuario usuarioLogado) {
        return listarConsultasAdiantamentoSemanaAtual(usuarioLogado);
    }

    public List<Agendamento> listarConsultasPagamentoMensalPublico(Usuario usuarioLogado) {
        return listarConsultasPagamentoMensal(usuarioLogado);
    }

    public List<Agendamento> resolverConsultasPagamentoDiaSelecionadas(
            Usuario usuarioLogado,
            List<Long> agendamentoIds
    ) {
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
        return selecionadas;
    }
}
