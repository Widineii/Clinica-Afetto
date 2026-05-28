package com.clinica.sistema.repository;

import com.clinica.sistema.dto.RelatorioArquivadoCabecalhoProjection;
import com.clinica.sistema.dto.RelatorioHistoricoMetadadosProjection;
import com.clinica.sistema.model.RelatorioMensalArquivado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RelatorioMensalArquivadoRepository extends JpaRepository<RelatorioMensalArquivado, Long> {

    Optional<RelatorioMensalArquivado> findByAnoAndMes(int ano, int mes);

    @Query("""
            SELECT r.dadosJson
            FROM RelatorioMensalArquivado r
            WHERE r.ano = :ano AND r.mes = :mes
            """)
    Optional<String> findDadosJsonByAnoAndMes(@Param("ano") int ano, @Param("mes") int mes);

    @Query("""
            SELECT CASE WHEN r.pdf IS NOT NULL THEN true ELSE false END
            FROM RelatorioMensalArquivado r
            WHERE r.ano = :ano AND r.mes = :mes
            """)
    Optional<Boolean> temPdfByAnoAndMes(@Param("ano") int ano, @Param("mes") int mes);

    @Query("""
            SELECT r.geradoEm AS geradoEm, r.agendamentosRemovidos AS agendamentosRemovidos
            FROM RelatorioMensalArquivado r
            WHERE r.ano = :ano AND r.mes = :mes
            """)
    Optional<RelatorioArquivadoCabecalhoProjection> findCabecalhoByAnoAndMes(
            @Param("ano") int ano,
            @Param("mes") int mes
    );

    boolean existsByAnoAndMes(int ano, int mes);

    List<RelatorioMensalArquivado> findAllByOrderByAnoDescMesDesc();

    @Query("""
            SELECT r.ano AS ano, r.mes AS mes, r.mesLabel AS mesLabel, r.dadosJson AS dadosJson
            FROM RelatorioMensalArquivado r
            ORDER BY r.ano DESC, r.mes DESC
            """)
    List<RelatorioHistoricoMetadadosProjection> listarHistoricoMetadados();

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM RelatorioMensalArquivado r
            WHERE r.ano = :ano
              AND r.mes = :mes
              AND r.dadosJson IS NOT NULL
              AND LENGTH(r.dadosJson) > 0
            """)
    boolean existsComDadosJson(@Param("ano") int ano, @Param("mes") int mes);

    List<RelatorioMensalArquivado> findByPdfIsNotNull();

    @Query("""
            SELECT r.pdfNotificacaoBaixadoEm
            FROM RelatorioMensalArquivado r
            WHERE r.ano = :ano AND r.mes = :mes
            """)
    Optional<LocalDateTime> findPdfNotificacaoBaixadoEmByAnoAndMes(
            @Param("ano") int ano,
            @Param("mes") int mes
    );

    @Query("""
            SELECT COALESCE(SUM(LENGTH(r.dadosJson)), 0)
            FROM RelatorioMensalArquivado r
            WHERE r.dadosJson IS NOT NULL
            """)
    long somaBytesJson();

    @Query("""
            SELECT COUNT(r)
            FROM RelatorioMensalArquivado r
            WHERE r.pdf IS NOT NULL
            """)
    long countComPdfLegado();

    @Query(
            value = """
                    SELECT COALESCE(SUM(octet_length(pdf)), 0)
                    FROM relatorios_mensais_arquivados
                    WHERE pdf IS NOT NULL
                    """,
            nativeQuery = true
    )
    Long somaBytesPdfLegado();
}
