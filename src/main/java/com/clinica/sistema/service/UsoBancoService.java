package com.clinica.sistema.service;

import com.clinica.sistema.dto.UsoBancoView;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.RelatorioMensalArquivadoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Locale;

@Service
public class UsoBancoService {

    private static final Logger log = LoggerFactory.getLogger(UsoBancoService.class);

    /** Media aproximada por linha em agendamentos + indices (bytes). */
    private static final long BYTES_ESTIMADOS_POR_AGENDAMENTO = 800L;
    private static final long BYTES_ESTIMADOS_OVERHEAD = 512_000L;

    private final AgendamentoRepository agendamentoRepository;
    private final RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SalaRepository salaRepository;
    private final DataSource dataSource;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.banco.storage-limit-mb:${app.neon.storage-limit-mb:512}}")
    private int limiteStorageMb;

    @Value("${app.banco.painel-provedor:KingHost}")
    private String painelProvedor;

    public UsoBancoService(
            AgendamentoRepository agendamentoRepository,
            RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository,
            UsuarioRepository usuarioRepository,
            SalaRepository salaRepository,
            DataSource dataSource
    ) {
        this.agendamentoRepository = agendamentoRepository;
        this.relatorioMensalArquivadoRepository = relatorioMensalArquivadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.salaRepository = salaRepository;
        this.dataSource = dataSource;
    }

    public UsoBancoView montarResumo() {
        LocalDateTime agora = LocalDateTime.now();
        YearMonth mesAtual = YearMonth.now();
        LocalDateTime inicioMes = mesAtual.atDay(1).atStartOfDay();
        LocalDateTime fimMes = mesAtual.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime inicioHoje = LocalDate.now().atStartOfDay();
        LocalDateTime fimHoje = inicioHoje.plusDays(1);

        long totalAgendamentos;
        long avulsos;
        long fixos;
        long encerrados;
        long noMes;
        long hoje;
        Object[] contagensAgendamentos = agendamentoRepository.buscarResumoContagensPainel(
                agora,
                inicioMes,
                fimMes,
                inicioHoje,
                fimHoje
        );
        totalAgendamentos = extrairContagem(contagensAgendamentos, 0);
        avulsos = extrairContagem(contagensAgendamentos, 1);
        fixos = extrairContagem(contagensAgendamentos, 2);
        encerrados = extrairContagem(contagensAgendamentos, 3);
        noMes = extrairContagem(contagensAgendamentos, 4);
        hoje = extrairContagem(contagensAgendamentos, 5);

        long relatorios = relatorioMensalArquivadoRepository.count();
        long relatoriosComPdf = relatorioMensalArquivadoRepository.countComPdfLegado();
        long bytesJson = somarBytesJsonRelatorios();
        boolean postgres = isPostgresql();
        long bytesPdf = postgres ? somarBytesPdfLegadoForaDaTransacaoPrincipal() : 0L;

        long bytesEstimados = BYTES_ESTIMADOS_OVERHEAD
                + totalAgendamentos * BYTES_ESTIMADOS_POR_AGENDAMENTO
                + bytesJson
                + bytesPdf;

        Long bytesBancoReal = postgres ? consultarTamanhoBancoPostgresForaDaTransacaoPrincipal().orElse(null) : null;
        long bytesReferencia = bytesBancoReal != null ? bytesBancoReal : bytesEstimados;
        long limiteBytes = (long) limiteStorageMb * 1024L * 1024L;
        double percentual = limiteBytes > 0
                ? Math.min(100.0, (bytesReferencia * 100.0) / limiteBytes)
                : 0.0;
        int barraPercentual = (int) Math.round(Math.min(100.0, percentual));

        return new UsoBancoView(
                totalAgendamentos,
                avulsos,
                fixos,
                encerrados,
                noMes,
                hoje,
                usuarioRepository.count(),
                usuarioRepository.countByCargo("ROLE_PROFISSIONAL"),
                salaRepository.count(),
                relatorios,
                relatoriosComPdf,
                formatarBytes(bytesJson),
                formatarBytes(bytesPdf),
                bytesBancoReal,
                bytesBancoReal != null ? formatarBytes(bytesBancoReal) : null,
                formatarBytes(bytesEstimados),
                painelProvedor != null ? painelProvedor.trim() : "KingHost",
                formatarLimitePlano(limiteStorageMb),
                limiteStorageMb,
                formatarPercentual(percentual),
                barraPercentual,
                classificarAlerta(percentual),
                bytesBancoReal != null
        );
    }

    private static long extrairContagem(Object[] linha, int indice) {
        if (linha == null || indice >= linha.length || linha[indice] == null) {
            return 0L;
        }
        if (linha[indice] instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private long somarBytesJsonRelatorios() {
        try {
            return relatorioMensalArquivadoRepository.somaBytesJson();
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel somar bytes do JSON dos relatorios: {}", e.getMessage());
            return 0L;
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public long somarBytesPdfLegadoForaDaTransacaoPrincipal() {
        try {
            Long soma = relatorioMensalArquivadoRepository.somaBytesPdfLegado();
            return soma != null ? soma : 0L;
        } catch (RuntimeException e) {
            log.warn("Soma de PDF legado indisponivel: {}", e.getMessage());
            return 0L;
        }
    }

    private boolean isPostgresql() {
        try (Connection connection = dataSource.getConnection()) {
            String produto = connection.getMetaData().getDatabaseProductName();
            return produto != null && produto.toLowerCase(Locale.ROOT).contains("postgres");
        } catch (SQLException e) {
            log.debug("Nao foi possivel detectar o banco: {}", e.getMessage());
            return false;
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public java.util.Optional<Long> consultarTamanhoBancoPostgresForaDaTransacaoPrincipal() {
        if (entityManager == null) {
            return java.util.Optional.empty();
        }
        try {
            Object resultado = entityManager.createNativeQuery(
                    "SELECT pg_database_size(current_database())"
            ).getSingleResult();
            if (resultado instanceof Number number) {
                return java.util.Optional.of(number.longValue());
            }
        } catch (RuntimeException e) {
            log.warn("Falha ao ler tamanho do banco PostgreSQL: {}", e.getMessage());
        }
        return java.util.Optional.empty();
    }

    static String formatarPercentual(double percentual) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", percentual);
    }

    static String formatarLimitePlano(int limiteMb) {
        if (limiteMb >= 1024 && limiteMb % 1024 == 0) {
            return (limiteMb / 1024) + " GB";
        }
        if (limiteMb >= 1024) {
            return String.format(Locale.forLanguageTag("pt-BR"), "%.1f GB", limiteMb / 1024.0);
        }
        return limiteMb + " MB";
    }

    public static String formatarBytes(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.forLanguageTag("pt-BR"), "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.forLanguageTag("pt-BR"), "%.2f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format(Locale.forLanguageTag("pt-BR"), "%.2f GB", gb);
    }

    private static String classificarAlerta(double percentual) {
        if (percentual >= 85.0) {
            return "critico";
        }
        if (percentual >= 60.0) {
            return "atencao";
        }
        return "ok";
    }
}
