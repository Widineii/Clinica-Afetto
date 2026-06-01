package com.clinica.sistema.config;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PeriodicidadePagamento;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.RelatorioMensalArquivadoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupDataInitializer implements CommandLineRunner {
    private static final String SENHA_PROFISSIONAIS_PADRAO = "297b";
    private static final int SEMANAS_FIXAS_PADRAO = 12;
    private static final String PREFIXO_SERIE_FIXA_SEED = "seed-fixo-";
    private static final LocalDate DATA_BASE_DEMONSTRACAO = LocalDate.of(2026, 4, 20);
    private static final String RECORRENCIA_AVULSO = "AVULSO";
    private static final String RECORRENCIA_SEMANAL = "SEMANAL";
    private static final String RECORRENCIA_QUINZENAL = "QUINZENAL";

    private final SalaRepository salaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository;
    private final PasswordEncoder passwordEncoder;
    private final SegurancaProperties segurancaProperties;

    @Value("${app.seed-demo-data:false}")
    private boolean seedDemoData;

    @Value("${app.seed-carga-inicial-fixos:true}")
    private boolean seedCargaInicialFixos;

    @Value("${app.seed-admin-login:}")
    private String adminLogin;

    @Value("${app.seed-admin-password:}")
    private String adminPassword;

    @Value("${app.seed-admin-name:Administracao}")
    private String adminName;

    @Value("${app.seed-test-user.enabled:false}")
    private boolean seedTestUserEnabled;

    @Value("${app.seed-test-user.login:teste}")
    private String testUserLogin;

    @Value("${app.seed-test-user.password:297b}")
    private String testUserPassword;

    @Value("${app.seed-test-user.name:Perfil Teste}")
    private String testUserName;

    @Value("${app.seed-test-user.reset-password-on-startup:true}")
    private boolean testUserResetPasswordOnStartup;

    public StartupDataInitializer(
            SalaRepository salaRepository,
            UsuarioRepository usuarioRepository,
            AgendamentoRepository agendamentoRepository,
            RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository,
            PasswordEncoder passwordEncoder,
            SegurancaProperties segurancaProperties
    ) {
        this.salaRepository = salaRepository;
        this.usuarioRepository = usuarioRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.relatorioMensalArquivadoRepository = relatorioMensalArquivadoRepository;
        this.passwordEncoder = passwordEncoder;
        this.segurancaProperties = segurancaProperties;
    }

    @Override
    public void run(String... args) {
        garantirSalas();
        sincronizarUsuariosPadrao();
        garantirAdmin();
        garantirUsuarioTeste();
        migrarSenhasLegadas();

        if (seedDemoData) {
            resetarBaseDemonstracao();
            semearAgendamentosAvulsosMesPassadoParaRelatorio();
            return;
        }

        if (deveSincronizarCargaInicial()) {
            sincronizarCargaInicialClinica();
            return;
        }

    }

    @Transactional
    public void sincronizarCargaInicialClinica() {
        sincronizarUsuariosPadrao();
        garantirAdmin();
        if (seedCargaInicialFixos) {
            sincronizarAgendamentosFixosPadrao();
        }
    }

    @Transactional
    public Usuario resetarBaseDemonstracao() {
        String loginAdmin = (adminLogin != null && !adminLogin.isBlank()) ? adminLogin : "admin";
        Usuario adminExistente = usuarioRepository.findByLogin(loginAdmin).orElse(null);
        return resetarBaseDemonstracao(adminExistente);
    }

    @Transactional
    public Usuario resetarBaseDemonstracao(Usuario adminPreservado) {
        agendamentoRepository.deleteAllInBatch();
        relatorioMensalArquivadoRepository.deleteAllInBatch();
        salaRepository.deleteAllInBatch();

        if (adminPreservado == null) {
            usuarioRepository.deleteAllInBatch();
        } else {
            List<Long> idsParaRemover = usuarioRepository.findAll().stream()
                    .filter(usuario -> !usuario.getId().equals(adminPreservado.getId()))
                    .map(Usuario::getId)
                    .toList();
            if (!idsParaRemover.isEmpty()) {
                usuarioRepository.deleteAllByIdInBatch(idsParaRemover);
            }
        }

        garantirSalas();
        if (adminPreservado != null) {
            usuarioRepository.save(adminPreservado);
        }
        sincronizarUsuariosPadrao();
        garantirAdmin();
        if (seedCargaInicialFixos) {
            sincronizarAgendamentosFixosPadrao();
        }

        String loginAdmin = adminPreservado != null ? adminPreservado.getLogin() : (adminLogin != null && !adminLogin.isBlank() ? adminLogin : "admin");
        return usuarioRepository.findByLogin(loginAdmin)
                .orElseThrow(() -> new RuntimeException("Nao foi possivel recriar o admin da demonstracao."));
    }

    private boolean deveSincronizarCargaInicial() {
        if (seedDemoData) {
            return true;
        }

        long usuariosAtendentes = usuarioRepository.findAll().stream()
                .filter(usuario -> !"admin".equalsIgnoreCase(usuario.getLogin()))
                .count();

        return usuariosAtendentes == 0;
    }

    private void garantirSalas() {
        List<String> salas = List.of("Sala 1", "Sala 2", "Sala 3", "Sala 4");
        for (String nomeSala : salas) {
            boolean existe = salaRepository.findAllByOrderByNomeAsc().stream()
                    .anyMatch(sala -> sala.getNome().equalsIgnoreCase(nomeSala));
            if (!existe) {
                Sala sala = new Sala();
                sala.setNome(nomeSala);
                salaRepository.save(sala);
            }
        }
    }

    private void garantirAdmin() {
        if (adminLogin == null || adminLogin.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return;
        }

        salvarOuAtualizarUsuario(new UsuarioPadrao(
                adminName,
                adminLogin,
                adminPassword,
                "ROLE_ADMIN",
                false
        ));
    }

    /**
     * Perfil opcional para testes em producao (ativar com app.seed-test-user.enabled=true).
     * Nao e dona da clinica; no primeiro acesso pode pedir troca de senha.
     */
    private void garantirUsuarioTeste() {
        if (!seedTestUserEnabled) {
            return;
        }
        if (testUserLogin == null || testUserLogin.isBlank()
                || testUserPassword == null || testUserPassword.isBlank()) {
            return;
        }

        String login = testUserLogin.trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioRepository.findByLogin(login).orElseGet(Usuario::new);
        boolean novo = usuario.getId() == null;

        usuario.setNome(testUserName != null && !testUserName.isBlank() ? testUserName.trim() : "Perfil Teste");
        usuario.setLogin(login);
        usuario.setCargo("ROLE_PROFISSIONAL");
        usuario.setDonaClinica(false);
        if (usuario.getPeriodicidadePagamento() == null) {
            usuario.setPeriodicidadePagamento(PeriodicidadePagamento.DIARIO);
        }

        if (novo || testUserResetPasswordOnStartup) {
            usuario.setSenha(passwordEncoder.encode(testUserPassword.trim()));
        }
        if (novo && segurancaProperties.isExigirTrocaSenhaPrimeiroAcesso()) {
            usuario.setDeveTrocarSenha(true);
        }

        usuarioRepository.save(usuario);
    }

    private void sincronizarUsuariosPadrao() {
        List<UsuarioPadrao> usuariosPadrao = List.of(
                new UsuarioPadrao("Polyana", "polyana", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", true),
                new UsuarioPadrao("Carol", "carol", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Itamara", "itamara", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Julia", "julia", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Juliano", "juliano", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Jessica Mota", "jessicamota", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Jessica Houri", "jessicahouri", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Rosi", "rosi", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Maria Paula", "mariapaula", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Breno", "breno", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Tathiane", "tathiane", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Luiza", "luiza", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Andreia", "andreia", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Cibele", "cibele", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Bruna", "bruna", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Claudia", "claudia", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Ana Paula", "anapaula", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Leticia", "leticia", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Juliana Cristina", "julianacristina", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false),
                new UsuarioPadrao("Larissa", "larissa", SENHA_PROFISSIONAIS_PADRAO, "ROLE_PROFISSIONAL", false)
        );

        usuariosPadrao.forEach(this::salvarOuAtualizarUsuario);
    }

    /** Cria agendamentos avulsos no mes passado para o relatorio/PDF local ter dados reais. */
    private void semearAgendamentosAvulsosMesPassadoParaRelatorio() {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);
        Map<String, Usuario> usuariosPorLogin = usuarioRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        usuario -> usuario.getLogin().toLowerCase(Locale.ROOT),
                        Function.identity()
                ));
        List<Sala> salas = salaRepository.findAllByOrderByNomeAsc();
        if (salas.isEmpty()) {
            return;
        }

        List<String> logins = List.of("carol", "julia", "polyana", "itamara", "juliano", "breno", "rosi");
        List<LocalDate> dias = List.of(
                mesPassado.atDay(7), mesPassado.atDay(8), mesPassado.atDay(9), mesPassado.atDay(10),
                mesPassado.atDay(11), mesPassado.atDay(14), mesPassado.atDay(15), mesPassado.atDay(16),
                mesPassado.atDay(17), mesPassado.atDay(18), mesPassado.atDay(21), mesPassado.atDay(22),
                mesPassado.atDay(23), mesPassado.atDay(24), mesPassado.atDay(25)
        );
        int[] horas = {7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21};
        List<Agendamento> avulsos = new ArrayList<>();

        for (int i = 0; i < dias.size(); i++) {
            String login = logins.get(i % logins.size());
            Usuario profissional = usuariosPorLogin.get(login);
            if (profissional == null) {
                continue;
            }
            Sala sala = salas.get(i % salas.size());
            LocalDateTime inicio = dias.get(i).atTime(horas[i], 0);
            Agendamento agendamento = new Agendamento();
            agendamento.setProfissional(profissional);
            agendamento.setSala(sala);
            agendamento.setNomeCliente("Demonstracao relatorio " + (i + 1));
            agendamento.setDataHoraInicio(inicio);
            agendamento.setDataHoraFim(inicio.plusHours(1));
            agendamento.setFixo(false);
            agendamento.setTipoRecorrencia(RECORRENCIA_AVULSO);
            avulsos.add(agendamento);
        }

        if (!avulsos.isEmpty()) {
            agendamentoRepository.saveAll(avulsos);
        }
    }

    private void sincronizarAgendamentosFixosPadrao() {
        Map<String, Usuario> usuariosPorLogin = usuarioRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        usuario -> usuario.getLogin().toLowerCase(Locale.ROOT),
                        Function.identity()
                ));
        Map<String, Sala> salasPorNome = salaRepository.findAllByOrderByNomeAsc().stream()
                .collect(java.util.stream.Collectors.toMap(
                        sala -> sala.getNome().toLowerCase(Locale.ROOT),
                        Function.identity()
                ));

        List<AgendamentoFixoPadrao> padroes = listarAgendamentosFixosPadrao();
        List<Agendamento> novosAgendamentos = new ArrayList<>();
        LocalDate inicioBase = DATA_BASE_DEMONSTRACAO;

        for (AgendamentoFixoPadrao padrao : padroes) {
            Usuario profissional = usuariosPorLogin.get(padrao.loginProfissional().toLowerCase(Locale.ROOT));
            Sala sala = salasPorNome.get(padrao.nomeSala().toLowerCase(Locale.ROOT));
            if (profissional == null || sala == null) {
                continue;
            }

            String serieFixaId = PREFIXO_SERIE_FIXA_SEED + padrao.recorrencia().toLowerCase(Locale.ROOT) + "-" + padrao.chave();
            LocalDate primeiraData = padrao.dataInicial() != null
                    ? padrao.dataInicial()
                    : inicioBase.with(TemporalAdjusters.nextOrSame(padrao.diaSemana()));

            int repeticoes = obterRepeticoesSeed(padrao.recorrencia());
            int saltoSemanas = obterSaltoSeed(padrao.recorrencia());

            for (int semana = 0; semana < repeticoes; semana++) {
                LocalDateTime inicio = primeiraData.plusWeeks((long) semana * saltoSemanas).atTime(padrao.horario());
                Agendamento agendamento = new Agendamento();
                agendamento.setProfissional(profissional);
                agendamento.setSala(sala);
                agendamento.setNomeCliente(padrao.nomeCliente());
                agendamento.setDataHoraInicio(inicio);
                agendamento.setDataHoraFim(inicio.plusHours(1));
                agendamento.setFixo(!RECORRENCIA_AVULSO.equals(padrao.recorrencia()));
                agendamento.setSerieFixaId(serieFixaId);
                agendamento.setTipoRecorrencia(padrao.recorrencia());
                novosAgendamentos.add(agendamento);
            }
        }

        agendamentoRepository.deleteBySerieFixaIdStartingWith(PREFIXO_SERIE_FIXA_SEED);
        agendamentoRepository.saveAll(novosAgendamentos);
    }

    private void salvarOuAtualizarUsuario(UsuarioPadrao usuarioPadrao) {
        Usuario profissional = usuarioRepository.findByLogin(usuarioPadrao.login())
                .orElseGet(Usuario::new);
        boolean novo = profissional.getId() == null;
        profissional.setNome(usuarioPadrao.nome());
        profissional.setLogin(usuarioPadrao.login());
        profissional.setCargo(usuarioPadrao.cargo());
        profissional.setDonaClinica(usuarioPadrao.donaClinica());
        if (usuarioPadrao.donaClinica()) {
            profissional.setDeveTrocarSenha(false);
        }
        if (novo || (seedDemoData && "ROLE_ADMIN".equals(usuarioPadrao.cargo()))) {
            profissional.setSenha(passwordEncoder.encode(usuarioPadrao.senha()));
        }
        if (novo
                && segurancaProperties.isExigirTrocaSenhaPrimeiroAcesso()
                && !"ROLE_ADMIN".equals(usuarioPadrao.cargo())) {
            profissional.setDeveTrocarSenha(true);
        }
        usuarioRepository.save(profissional);
    }

    private void migrarSenhasLegadas() {
        if (!usuarioRepository.existsSenhaLegada()) {
            return;
        }
        for (Usuario usuario : usuarioRepository.findAll()) {
            String senha = usuario.getSenha();
            if (senha != null && !senha.isBlank() && !senha.startsWith("$2a$")) {
                usuario.setSenha(passwordEncoder.encode(senha));
                usuarioRepository.save(usuario);
            }
        }
    }

    private List<AgendamentoFixoPadrao> listarAgendamentosFixosPadrao() {
        return List.of();
    }

    private int obterRepeticoesSeed(String recorrencia) {
        return switch (recorrencia) {
            case RECORRENCIA_SEMANAL -> SEMANAS_FIXAS_PADRAO;
            case RECORRENCIA_QUINZENAL -> 6;
            default -> 1;
        };
    }

    private int obterSaltoSeed(String recorrencia) {
        return RECORRENCIA_QUINZENAL.equals(recorrencia) ? 2 : 1;
    }

    private void adicionar(
            List<AgendamentoFixoPadrao> padroes,
            String loginProfissional,
            String nomeSala,
            DayOfWeek diaSemana,
            int hora,
            String nomeCliente,
            String recorrencia,
            String chave
    ) {
        adicionar(padroes, loginProfissional, nomeSala, diaSemana, hora, nomeCliente, recorrencia, chave, null);
    }

    private void adicionar(
            List<AgendamentoFixoPadrao> padroes,
            String loginProfissional,
            String nomeSala,
            DayOfWeek diaSemana,
            int hora,
            String nomeCliente,
            String recorrencia,
            String chave,
            LocalDate dataInicial
    ) {
        padroes.add(new AgendamentoFixoPadrao(
                loginProfissional,
                nomeSala,
                diaSemana,
                LocalTime.of(hora, 0),
                nomeCliente,
                recorrencia,
                chave,
                dataInicial
        ));
    }

    private record UsuarioPadrao(String nome, String login, String senha, String cargo, boolean donaClinica) {
    }

    private record AgendamentoFixoPadrao(
            String loginProfissional,
            String nomeSala,
            DayOfWeek diaSemana,
            LocalTime horario,
            String nomeCliente,
            String recorrencia,
            String chave,
            LocalDate dataInicial
    ) {
    }
}
