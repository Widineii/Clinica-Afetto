package com.clinica.sistema.service;

import com.clinica.sistema.dto.PendenciasDonaContaItemView;
import com.clinica.sistema.dto.PendenciasDonaIndicacaoItemView;
import com.clinica.sistema.dto.PendenciasDonaLoginView;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.security.ClinicaAuthenticationSuccessHandler;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class PendenciasDonaLoginService {

    private static final DateTimeFormatter FORMATO_DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AuthService authService;
    private final IndicacaoReservaService indicacaoReservaService;
    private final UsuarioRepository usuarioRepository;

    public PendenciasDonaLoginService(
            AuthService authService,
            IndicacaoReservaService indicacaoReservaService,
            UsuarioRepository usuarioRepository
    ) {
        this.authService = authService;
        this.indicacaoReservaService = indicacaoReservaService;
        this.usuarioRepository = usuarioRepository;
    }

    public void marcarPendenciasDonaNoLogin(HttpSession session, Usuario usuario) {
        if (session == null || !authService.podeGerenciarEquipe(usuario)) {
            return;
        }
        if (contarPendencias() > 0) {
            session.setAttribute(
                    ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_PENDENCIAS_DONA,
                    Boolean.TRUE
            );
        }
    }

    public boolean exibirModalPendenciasDonaEntrada(HttpSession session, Usuario usuario) {
        if (session == null || !authService.podeGerenciarEquipe(usuario)) {
            return false;
        }
        if (!Boolean.TRUE.equals(session.getAttribute(
                ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_PENDENCIAS_DONA
        ))) {
            return false;
        }
        return contarPendencias() > 0;
    }

    public void dispensarLembretePendenciasDona(HttpSession session) {
        if (session != null) {
            session.removeAttribute(ClinicaAuthenticationSuccessHandler.SESSION_LOGIN_COM_PENDENCIAS_DONA);
        }
    }

    public PendenciasDonaLoginView montar(Usuario usuario) {
        String primeiroNome = extrairPrimeiroNome(usuario);
        if (!authService.podeGerenciarEquipe(usuario)) {
            return PendenciasDonaLoginView.vazio(primeiroNome);
        }

        List<PendenciasDonaIndicacaoItemView> indicacoes = new ArrayList<>();
        for (Agendamento agendamento : indicacaoReservaService.listarAguardandoAprovacao()) {
            String profissional = agendamento.getProfissional() != null
                    ? agendamento.getProfissional().getNome()
                    : "Profissional";
            String dataHora = agendamento.getDataHoraInicio() != null
                    ? agendamento.getDataHoraInicio().format(FORMATO_DATA_HORA)
                    : "—";
            indicacoes.add(new PendenciasDonaIndicacaoItemView(
                    agendamento.getId(),
                    profissional,
                    agendamento.getNomeCliente(),
                    dataHora
            ));
        }

        List<PendenciasDonaContaItemView> contas = new ArrayList<>();
        for (Usuario conta : usuarioRepository.findByContaAprovadaFalseOrderByCadastroSolicitadoEmAsc()) {
            String solicitadoEm = conta.getCadastroSolicitadoEm() != null
                    ? conta.getCadastroSolicitadoEm().format(FORMATO_DATA_HORA)
                    : "—";
            contas.add(new PendenciasDonaContaItemView(
                    conta.getId(),
                    conta.getNome(),
                    conta.getLogin(),
                    solicitadoEm
            ));
        }

        return new PendenciasDonaLoginView(
                primeiroNome,
                indicacoes.size(),
                contas.size(),
                List.copyOf(indicacoes),
                List.copyOf(contas)
        );
    }

    private int contarPendencias() {
        return indicacaoReservaService.contarAguardandoAprovacao()
                + usuarioRepository.findByContaAprovadaFalseOrderByCadastroSolicitadoEmAsc().size();
    }

    private String extrairPrimeiroNome(Usuario usuario) {
        if (usuario == null || usuario.getNome() == null || usuario.getNome().isBlank()) {
            return "Polyana";
        }
        String nome = usuario.getNome().trim();
        int espaco = nome.indexOf(' ');
        return espaco > 0 ? nome.substring(0, espaco) : nome;
    }
}
