package com.clinica.sistema.service;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.AuditoriaEvento;
import com.clinica.sistema.model.EncerramentoSerieRegistro;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AuditoriaEventoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class AuditoriaService {

    public static final String TIPO_AGENDAMENTO_CRIADO = "AGENDAMENTO_CRIADO";
    public static final String TIPO_AGENDAMENTO_CANCELADO = "AGENDAMENTO_CANCELADO";
    public static final String TIPO_AGENDAMENTO_REALOCADO = "AGENDAMENTO_REALOCADO";
    public static final String TIPO_SENHA_ALTERADA = "SENHA_ALTERADA";
    public static final String TIPO_CONTA_APROVADA = "CONTA_APROVADA";
    public static final String TIPO_CONTA_RECUSADA = "CONTA_RECUSADA";
    public static final String TIPO_SERIE_ENCERRADA = "SERIE_ENCERRADA";
    public static final String TIPO_PROFISSIONAL_EDITADO = "PROFISSIONAL_EDITADO";
    public static final String TIPO_PROFISSIONAL_EXCLUIDO = "PROFISSIONAL_EXCLUIDO";
    public static final String TIPO_PERIODICIDADE_ALTERADA = "PERIODICIDADE_ALTERADA";
    public static final String TIPO_VALORES_CONSULTA_ALTERADOS = "VALORES_CONSULTA_ALTERADOS";
    public static final String TIPO_TAXA_INDICACAO_ALTERADA = "TAXA_INDICACAO_ALTERADA";

    private static final DateTimeFormatter FORMATO_DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm", new Locale("pt", "BR"));

    private final AuditoriaEventoRepository auditoriaEventoRepository;
    private final AuthService authService;
    private final int mesesRetencao;

    public AuditoriaService(
            AuditoriaEventoRepository auditoriaEventoRepository,
            AuthService authService,
            @Value("${app.auditoria.meses-retencao:3}") int mesesRetencao
    ) {
        this.auditoriaEventoRepository = auditoriaEventoRepository;
        this.authService = authService;
        this.mesesRetencao = Math.max(1, mesesRetencao);
    }

    public boolean podeVerAuditoria(Usuario usuario) {
        return authService.podeVerRelatorioUsoSite(usuario);
    }

    public List<AuditoriaEvento> listarRecentes() {
        return listarPorDia(LocalDate.now());
    }

    public List<AuditoriaEvento> listarPorDia(LocalDate dia) {
        LocalDate data = dia != null ? dia : LocalDate.now();
        LocalDateTime inicio = data.atStartOfDay();
        LocalDateTime fimExclusivo = data.plusDays(1).atStartOfDay();
        return auditoriaEventoRepository
                .findByCriadoEmGreaterThanEqualAndCriadoEmLessThanOrderByCriadoEmDesc(inicio, fimExclusivo);
    }

    @Transactional
    public int expurgarRegistrosAntigos() {
        LocalDateTime limite = calcularLimiteRetencao();
        return auditoriaEventoRepository.deleteByCriadoEmBefore(limite);
    }

    LocalDateTime calcularLimiteRetencao() {
        YearMonth mesAtual = YearMonth.now();
        YearMonth mesMaisAntigoPermitido = mesAtual.minusMonths(mesesRetencao - 1L);
        return mesMaisAntigoPermitido.atDay(1).atStartOfDay();
    }

    public int getMesesRetencao() {
        return mesesRetencao;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAgendamentoCriado(Usuario autor, Agendamento agendamento) {
        if (autor == null || agendamento == null) {
            return;
        }
        Usuario profissional = agendamento.getProfissional();
        String cliente = textoCliente(agendamento.getNomeCliente());
        String sala = nomeSala(agendamento.getSala());
        String quando = formatarDataHora(agendamento.getDataHoraInicio());
        String descricao;
        if (profissional != null && !mesmoUsuario(autor, profissional)) {
            descricao = nomeCurto(autor) + " agendou para " + nomeCurto(profissional)
                    + " na " + sala + " em " + quando + cliente;
        } else {
            descricao = nomeCurto(autor) + " criou agendamento na " + sala + " em " + quando + cliente;
        }
        registrar(autor, TIPO_AGENDAMENTO_CRIADO, descricao);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAgendamentoCancelado(Usuario autor, Agendamento agendamento) {
        if (autor == null || agendamento == null) {
            return;
        }
        Usuario profissional = agendamento.getProfissional();
        String cliente = textoCliente(agendamento.getNomeCliente());
        String sala = nomeSala(agendamento.getSala());
        String quando = formatarDataHora(agendamento.getDataHoraInicio());
        String descricao;
        if (profissional != null && !mesmoUsuario(autor, profissional)) {
            descricao = nomeCurto(autor) + " cancelou o agendamento de " + nomeCurto(profissional)
                    + " na " + sala + " em " + quando + cliente;
        } else {
            descricao = nomeCurto(autor) + " cancelou o agendamento da " + sala + " em " + quando + cliente;
        }
        registrar(autor, TIPO_AGENDAMENTO_CANCELADO, descricao);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAgendamentoRealocado(
            Usuario autor,
            Agendamento agendamento,
            Sala salaAnterior,
            LocalDateTime inicioAnterior
    ) {
        if (autor == null || agendamento == null) {
            return;
        }
        String cliente = textoCliente(agendamento.getNomeCliente());
        String descricao = nomeCurto(autor)
                + " realocou o agendamento de " + nomeSala(salaAnterior)
                + " em " + formatarDataHora(inicioAnterior)
                + " para " + nomeSala(agendamento.getSala())
                + " em " + formatarDataHora(agendamento.getDataHoraInicio())
                + cliente;
        registrar(autor, TIPO_AGENDAMENTO_REALOCADO, descricao);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarSenhaAlterada(Usuario autor, Usuario alvo, boolean porGestor) {
        if (autor == null || alvo == null) {
            return;
        }
        String descricao;
        if (porGestor && !mesmoUsuario(autor, alvo)) {
            descricao = nomeCurto(autor) + " redefiniu a senha de " + nomeCurto(alvo) + ".";
        } else if (mesmoUsuario(autor, alvo)) {
            descricao = nomeCurto(autor) + " trocou a propria senha.";
        } else {
            descricao = nomeCurto(autor) + " alterou a senha de " + nomeCurto(alvo) + ".";
        }
        registrar(autor, TIPO_SENHA_ALTERADA, descricao);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarContaAprovada(Usuario autor, Usuario conta) {
        if (autor == null || conta == null) {
            return;
        }
        registrar(
                autor,
                TIPO_CONTA_APROVADA,
                nomeCurto(autor) + " aprovou a conta de " + nomeCurto(conta) + " (login " + loginSeguro(conta) + ")."
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarContaRecusada(Usuario autor, Usuario conta) {
        if (autor == null || conta == null) {
            return;
        }
        registrar(
                autor,
                TIPO_CONTA_RECUSADA,
                nomeCurto(autor) + " recusou a conta de " + nomeCurto(conta) + " (login " + loginSeguro(conta) + ")."
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarSerieEncerrada(Usuario autor, EncerramentoSerieRegistro registro) {
        if (autor == null || registro == null) {
            return;
        }
        String cliente = textoCliente(registro.getNomeCliente());
        String profissional = registro.getProfissional() != null ? nomeCurto(registro.getProfissional()) : "profissional";
        String descricao = nomeCurto(autor) + " encerrou serie " + registro.getTipoRecorrencia().toLowerCase(Locale.ROOT)
                + " de " + profissional + cliente;
        registrar(autor, TIPO_SERIE_ENCERRADA, descricao);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarProfissionalEditado(Usuario autor, Usuario profissional, String nomeAnterior, String loginAnterior) {
        if (autor == null || profissional == null) {
            return;
        }
        String descricao = nomeCurto(autor) + " editou o cadastro de " + nomeCurto(profissional);
        if (textoMudou(nomeAnterior, profissional.getNome()) || textoMudou(loginAnterior, profissional.getLogin())) {
            descricao += " (antes: " + textoSeguro(nomeAnterior) + " / " + textoSeguro(loginAnterior)
                    + "; agora: " + textoSeguro(profissional.getNome()) + " / " + textoSeguro(profissional.getLogin()) + ")";
        }
        registrar(autor, TIPO_PROFISSIONAL_EDITADO, descricao + ".");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarProfissionalExcluido(Usuario autor, Usuario profissional) {
        if (autor == null || profissional == null) {
            return;
        }
        registrar(
                autor,
                TIPO_PROFISSIONAL_EXCLUIDO,
                nomeCurto(autor) + " excluiu o usuario " + textoSeguro(profissional.getNome())
                        + " (login " + loginSeguro(profissional) + ")."
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarPeriodicidadeAlterada(
            Usuario autor,
            Usuario profissional,
            Object anterior,
            Object nova,
            int agendamentosMigrados
    ) {
        if (autor == null || profissional == null || anterior == null || nova == null) {
            return;
        }
        String descricao = nomeCurto(autor) + " alterou a forma de pagamento de " + nomeCurto(profissional)
                + " de " + anterior + " para " + nova;
        if (agendamentosMigrados > 0) {
            descricao += " (" + agendamentosMigrados + " agendamento(s) ajustado(s))";
        }
        registrar(autor, TIPO_PERIODICIDADE_ALTERADA, descricao + ".");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarValoresConsultaAlterados(Usuario autor, Usuario profissional, int agendamentosAtualizados) {
        if (autor == null || profissional == null) {
            return;
        }
        String descricao = nomeCurto(autor) + " alterou os valores de consulta de " + nomeCurto(profissional);
        if (agendamentosAtualizados > 0) {
            descricao += " (" + agendamentosAtualizados + " agendamento(s) ajustado(s))";
        }
        registrar(autor, TIPO_VALORES_CONSULTA_ALTERADOS, descricao + ".");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarValoresConsultaLoteAlterados(Usuario autor, int profissionaisAtualizados, int agendamentosAtualizados) {
        if (autor == null || profissionaisAtualizados <= 0) {
            return;
        }
        registrar(
                autor,
                TIPO_VALORES_CONSULTA_ALTERADOS,
                nomeCurto(autor) + " alterou valores de consulta em lote para "
                        + profissionaisAtualizados + " profissional(is)"
                        + " (" + agendamentosAtualizados + " agendamento(s) ajustado(s))."
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarTaxaIndicacaoAlterada(Usuario autor, Usuario profissional, int agendamentosAtualizados) {
        if (autor == null || profissional == null) {
            return;
        }
        String descricao = nomeCurto(autor) + " alterou a taxa de indicacao de " + nomeCurto(profissional);
        if (agendamentosAtualizados > 0) {
            descricao += " (" + agendamentosAtualizados + " agendamento(s) ajustado(s))";
        }
        registrar(autor, TIPO_TAXA_INDICACAO_ALTERADA, descricao + ".");
    }

    private void registrar(Usuario autor, String tipo, String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return;
        }
        AuditoriaEvento evento = new AuditoriaEvento();
        evento.setCriadoEm(LocalDateTime.now());
        if (autor != null) {
            evento.setAutorId(autor.getId());
            evento.setAutorNome(nomeCurto(autor));
        }
        evento.setTipo(tipo);
        evento.setDescricao(descricao.trim());
        auditoriaEventoRepository.save(evento);
    }

    private boolean mesmoUsuario(Usuario a, Usuario b) {
        return a != null && b != null && a.getId() != null && a.getId().equals(b.getId());
    }

    private String nomeCurto(Usuario usuario) {
        if (usuario == null || usuario.getNome() == null || usuario.getNome().isBlank()) {
            return "Usuario";
        }
        String nome = usuario.getNome().trim();
        int espaco = nome.indexOf(' ');
        return espaco > 0 ? nome.substring(0, espaco) : nome;
    }

    private String loginSeguro(Usuario usuario) {
        if (usuario == null || usuario.getLogin() == null || usuario.getLogin().isBlank()) {
            return "-";
        }
        return usuario.getLogin().trim();
    }

    private boolean textoMudou(String anterior, String atual) {
        return !textoSeguro(anterior).equalsIgnoreCase(textoSeguro(atual));
    }

    private String textoSeguro(String texto) {
        if (texto == null || texto.isBlank()) {
            return "-";
        }
        return texto.trim();
    }

    private String nomeSala(Sala sala) {
        if (sala == null || sala.getNome() == null || sala.getNome().isBlank()) {
            return "sala";
        }
        return sala.getNome().trim();
    }

    private String formatarDataHora(LocalDateTime dataHora) {
        if (dataHora == null) {
            return "data nao informada";
        }
        return FORMATO_DATA_HORA.format(dataHora);
    }

    private String textoCliente(String nomeCliente) {
        if (nomeCliente == null || nomeCliente.isBlank()) {
            return ".";
        }
        return " (cliente " + nomeCliente.trim() + ").";
    }
}
