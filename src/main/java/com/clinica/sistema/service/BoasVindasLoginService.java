package com.clinica.sistema.service;

import com.clinica.sistema.dto.AtendimentoClienteHojeView;
import com.clinica.sistema.dto.AtendimentoSalaHojeView;
import com.clinica.sistema.dto.BoasVindasLoginView;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.security.ClinicaAuthenticationSuccessHandler;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BoasVindasLoginService {

    public static final int LIMITE_EXIBICOES_ATENDIMENTOS_HOJE = 4;

    public static final int LIMITE_EXIBICOES_ATENDIMENTOS_AMANHA = 2;

    /** Início do período que exibe atendimentos do dia (5h). */
    public static final int HORA_INICIO_PERIODO_DIA = 5;

    /** Início do período que exibe atendimentos de amanhã (21h). */
    public static final int HORA_INICIO_PERIODO_NOITE = 21;

    private static final ZoneId FUSO_CLINICA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern(
            "EEEE, d 'de' MMMM",
            Locale.forLanguageTag("pt-BR")
    );

    private final AuthService authService;
    private final AgendamentoService agendamentoService;
    private final UsuarioRepository usuarioRepository;

    public BoasVindasLoginService(
            AuthService authService,
            AgendamentoService agendamentoService,
            UsuarioRepository usuarioRepository
    ) {
        this.authService = authService;
        this.agendamentoService = agendamentoService;
        this.usuarioRepository = usuarioRepository;
    }

    public boolean elegivelBoasVindas(Usuario usuario) {
        return usuario != null
                && authService.podeAcessarMeusPacientes(usuario)
                && !authService.isAdmin(usuario);
    }

    public void marcarBoasVindasPendenteNoLogin(HttpSession session, Usuario usuario) {
        if (session == null || !elegivelBoasVindas(usuario)) {
            return;
        }
        Usuario atualizado = sincronizarControleDiario(usuario);
        if (!podeExibirBoasVindasHoje(atualizado)) {
            return;
        }
        session.setAttribute(ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_BOAS_VINDAS, Boolean.TRUE);
    }

    public boolean podeExibirBoasVindasHoje(Usuario usuario) {
        if (usuario == null) {
            return false;
        }
        if (isPrimeiroLoginPendente(usuario)) {
            return true;
        }
        if (!emPeriodoAtivo()) {
            return false;
        }
        if (emPeriodoNoite()) {
            if (Boolean.TRUE.equals(usuario.getBoasVindasOcultoNoite())) {
                return false;
            }
            int exibicoes = usuario.getBoasVindasExibicoesNoite() != null ? usuario.getBoasVindasExibicoesNoite() : 0;
            return exibicoes < LIMITE_EXIBICOES_ATENDIMENTOS_AMANHA;
        }
        if (Boolean.TRUE.equals(usuario.getBoasVindasOcultoHoje())) {
            return false;
        }
        int exibicoes = usuario.getBoasVindasExibicoesHoje() != null ? usuario.getBoasVindasExibicoesHoje() : 0;
        return exibicoes < LIMITE_EXIBICOES_ATENDIMENTOS_HOJE;
    }

    public boolean isPrimeiroLoginPendente(Usuario usuario) {
        return usuario != null && !Boolean.TRUE.equals(usuario.getBoasVindasPrimeiroLoginConcluido());
    }

    public boolean exigeFormaPagamentoPrimeiroAcesso(Usuario usuario) {
        if (usuario == null || !authService.podeEscolherFormaPagamento(usuario)) {
            return false;
        }
        if (Boolean.TRUE.equals(usuario.getBoasVindasApenasApresentacao())) {
            return false;
        }
        return isPrimeiroLoginPendente(usuario);
    }

    /** Período do dia: 5h às 21h — atendimentos de hoje. */
    public boolean emPeriodoDia() {
        LocalTime agora = LocalTime.now(FUSO_CLINICA);
        return !agora.isBefore(LocalTime.of(HORA_INICIO_PERIODO_DIA, 0))
                && agora.isBefore(LocalTime.of(HORA_INICIO_PERIODO_NOITE, 0));
    }

    /** Período da noite: 21h à meia-noite — atendimentos de amanhã. */
    public boolean emPeriodoNoite() {
        return LocalTime.now(FUSO_CLINICA).getHour() >= HORA_INICIO_PERIODO_NOITE;
    }

    public boolean emPeriodoAtivo() {
        return emPeriodoDia() || emPeriodoNoite();
    }

    @Transactional
    public Usuario sincronizarControleDiario(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            return usuario;
        }
        Usuario registro = usuarioRepository.findById(usuario.getId()).orElse(usuario);
        LocalDate hoje = LocalDate.now(FUSO_CLINICA);
        if (hoje.equals(registro.getBoasVindasControleData())) {
            return registro;
        }
        registro.setBoasVindasControleData(hoje);
        registro.setBoasVindasExibicoesHoje(0);
        registro.setBoasVindasOcultoHoje(false);
        registro.setBoasVindasExibicoesNoite(0);
        registro.setBoasVindasOcultoNoite(false);
        return usuarioRepository.save(registro);
    }

    @Transactional
    public void registrarFechamentoBoasVindas(Usuario usuario, boolean naoMostrarMaisHoje) {
        if (usuario == null || usuario.getId() == null) {
            return;
        }
        Usuario registro = sincronizarControleDiario(usuario);
        LocalDate hoje = LocalDate.now(FUSO_CLINICA);
        registro.setBoasVindasControleData(hoje);
        if (isPrimeiroLoginPendente(registro)) {
            if (Boolean.TRUE.equals(registro.getBoasVindasApenasApresentacao())) {
                registro.setBoasVindasApresentacaoExibida(true);
            }
            registro.setBoasVindasPrimeiroLoginConcluido(true);
            registro.setBoasVindasApenasApresentacao(false);
            usuarioRepository.save(registro);
            return;
        }
        if (emPeriodoNoite()) {
            if (naoMostrarMaisHoje) {
                registro.setBoasVindasOcultoNoite(true);
            } else {
                int exibicoes = registro.getBoasVindasExibicoesNoite() != null ? registro.getBoasVindasExibicoesNoite() : 0;
                registro.setBoasVindasExibicoesNoite(exibicoes + 1);
            }
        } else if (emPeriodoDia()) {
            if (naoMostrarMaisHoje) {
                registro.setBoasVindasOcultoHoje(true);
            } else {
                int exibicoes = registro.getBoasVindasExibicoesHoje() != null ? registro.getBoasVindasExibicoesHoje() : 0;
                registro.setBoasVindasExibicoesHoje(exibicoes + 1);
            }
        }
        usuarioRepository.save(registro);
    }

    public boolean exibirBoasVindasLoginEntrada(HttpSession session) {
        if (session == null) {
            return false;
        }
        return Boolean.TRUE.equals(session.getAttribute(ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_BOAS_VINDAS));
    }

    public void dispensarBoasVindasLogin(HttpSession session) {
        if (session != null) {
            session.removeAttribute(ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_BOAS_VINDAS);
        }
    }

    public BoasVindasLoginView montar(Usuario usuario) {
        Usuario registro = usuario != null && usuario.getId() != null
                ? usuarioRepository.findById(usuario.getId()).orElse(usuario)
                : usuario;
        boolean primeiroLogin = isPrimeiroLoginPendente(registro);
        String primeiroNome = extrairPrimeiroNome(registro != null ? registro.getNome() : null);
        String saudacao = resolverSaudacao();
        LocalDate hoje = LocalDate.now(FUSO_CLINICA);

        if (primeiroLogin) {
            boolean apenasApresentacao = Boolean.TRUE.equals(registro.getBoasVindasApenasApresentacao());
            String dataFormatada = capitalizarPrimeiraLetra(hoje.format(FORMATO_DATA));
            return new BoasVindasLoginView(
                    saudacao,
                    primeiroNome,
                    dataFormatada,
                    false,
                    true,
                    apenasApresentacao,
                    List.of(),
                    0
            );
        }

        LocalDate dataReferencia = resolverDataReferenciaAtendimentos(false);
        boolean atendimentosDeAmanha = dataReferencia.isAfter(hoje);
        String dataFormatada = capitalizarPrimeiraLetra(dataReferencia.format(FORMATO_DATA));

        List<Agendamento> agendamentosDoDia = agendamentoService
                .listarAgendamentosDoDia(registro, false, dataReferencia);
        List<AtendimentoSalaHojeView> salasComAtendimentos = agruparAtendimentosPorSala(agendamentosDoDia);

        return new BoasVindasLoginView(
                saudacao,
                primeiroNome,
                dataFormatada,
                atendimentosDeAmanha,
                primeiroLogin,
                false,
                salasComAtendimentos,
                agendamentosDoDia.size()
        );
    }

    LocalDate resolverDataReferenciaAtendimentos(boolean primeiroLogin) {
        LocalDate hoje = LocalDate.now(FUSO_CLINICA);
        if (primeiroLogin) {
            return hoje;
        }
        if (LocalTime.now(FUSO_CLINICA).getHour() >= HORA_INICIO_PERIODO_NOITE) {
            return hoje.plusDays(1);
        }
        return hoje;
    }

    static LocalDate resolverDataReferenciaAtendimentos() {
        LocalDate hoje = LocalDate.now(FUSO_CLINICA);
        if (LocalTime.now(FUSO_CLINICA).getHour() >= HORA_INICIO_PERIODO_NOITE) {
            return hoje.plusDays(1);
        }
        return hoje;
    }

    private List<AtendimentoSalaHojeView> agruparAtendimentosPorSala(List<Agendamento> agendamentosDoDia) {
        Map<Long, SalaGrupo> grupos = new LinkedHashMap<>();
        for (Agendamento agendamento : agendamentosDoDia) {
            long salaId = agendamento.getSala() != null && agendamento.getSala().getId() != null
                    ? agendamento.getSala().getId()
                    : Long.MAX_VALUE;
            String nomeSala = agendamento.getSala() != null && agendamento.getSala().getNome() != null
                    ? agendamento.getSala().getNome().trim()
                    : "Sala não informada";
            SalaGrupo grupo = grupos.computeIfAbsent(salaId, id -> new SalaGrupo(nomeSala));
            grupo.clientes().add(paraLinhaCliente(agendamento));
        }

        return grupos.entrySet().stream()
                .sorted(Comparator.comparingLong(Map.Entry::getKey))
                .map(entry -> new AtendimentoSalaHojeView(
                        entry.getValue().nomeSala(),
                        List.copyOf(entry.getValue().clientes())
                ))
                .toList();
    }

    private AtendimentoClienteHojeView paraLinhaCliente(Agendamento agendamento) {
        String horario = agendamento.getDataHoraInicio() != null
                ? agendamento.getDataHoraInicio().format(FORMATO_HORA)
                : "—";
        if (agendamento.getDataHoraFim() != null) {
            horario = horario + " – " + agendamento.getDataHoraFim().format(FORMATO_HORA);
        }
        String cliente = agendamento.getNomeCliente() != null && !agendamento.getNomeCliente().isBlank()
                ? agendamento.getNomeCliente().trim()
                : "Cliente";
        return new AtendimentoClienteHojeView(horario, cliente);
    }

    private record SalaGrupo(String nomeSala, List<AtendimentoClienteHojeView> clientes) {
        private SalaGrupo(String nomeSala) {
            this(nomeSala, new ArrayList<>());
        }
    }

    static String resolverSaudacao() {
        int hora = LocalTime.now(FUSO_CLINICA).getHour();
        if (hora >= 5 && hora < 12) {
            return "Bom dia";
        }
        if (hora >= 12 && hora < 18) {
            return "Boa tarde";
        }
        return "Boa noite";
    }

    private static String extrairPrimeiroNome(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.isBlank()) {
            return "profissional";
        }
        String limpo = nomeCompleto.trim();
        int espaco = limpo.indexOf(' ');
        if (espaco > 0) {
            return limpo.substring(0, espaco);
        }
        return limpo;
    }

    private static String capitalizarPrimeiraLetra(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        return texto.substring(0, 1).toUpperCase(Locale.ROOT) + texto.substring(1);
    }
}
