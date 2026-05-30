package com.clinica.sistema.repository;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findByProfissionalIdOrderByDataHoraInicioAsc(Long profissionalId);

    boolean existsByProfissionalId(Long profissionalId);

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
            LocalDateTime inicio,
            LocalDateTime fim
    );

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findByProfissionalIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
            Long profissionalId,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findBySalaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
            Long salaId,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    List<Agendamento> findTop20BySalaIdOrderByDataHoraInicioDesc(Long salaId);

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findTop20BySalaIdAndDataHoraInicioBeforeOrderByDataHoraInicioDesc(Long salaId, LocalDateTime referencia);

    boolean existsBySalaIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
            Long salaId,
            LocalDateTime dataHoraFim,
            LocalDateTime dataHoraInicio
    );

    boolean existsByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
            Long profissionalId,
            LocalDateTime dataHoraFim,
            LocalDateTime dataHoraInicio
    );

    @EntityGraph(attributePaths = {"sala"})
    Optional<Agendamento> findFirstByProfissionalIdAndDataHoraInicioLessThanAndDataHoraFimGreaterThan(
            Long profissionalId,
            LocalDateTime dataHoraFim,
            LocalDateTime dataHoraInicio
    );

    @Transactional
    @Modifying
    void deleteByProfissionalIdIn(List<Long> profissionalIds);

    List<Agendamento> findBySerieFixaIdAndDataHoraInicioGreaterThanEqualOrderByDataHoraInicioAsc(
            String serieFixaId,
            LocalDateTime dataHoraInicio
    );

    List<Agendamento> findBySerieFixaIdOrderByDataHoraInicioAsc(String serieFixaId);

    @EntityGraph(attributePaths = {"profissional", "sala"})
    Optional<Agendamento> findFirstBySerieFixaIdOrderByDataHoraInicioDesc(String serieFixaId);

    @EntityGraph(attributePaths = {"profissional", "sala"})
    Optional<Agendamento> findFirstBySerieFixaIdOrderByDataHoraInicioAsc(String serieFixaId);

    boolean existsBySerieFixaIdAndDataHoraInicio(String serieFixaId, LocalDateTime dataHoraInicio);

    long countBySerieFixaIdAndDataHoraInicioGreaterThanEqual(String serieFixaId, LocalDateTime dataHoraInicio);

    @EntityGraph(attributePaths = {"profissional", "sala"})
    Optional<Agendamento> findFirstBySerieFixaIdAndDataHoraInicioLessThanOrderByDataHoraInicioDesc(
            String serieFixaId,
            LocalDateTime dataHoraInicio
    );

    boolean existsBySerieFixaIdAndSerieEncerradaEmIsNotNull(String serieFixaId);

    @Query("""
            SELECT DISTINCT a.serieFixaId
            FROM Agendamento a
            WHERE a.serieFixaId IS NOT NULL
              AND a.serieFixaId NOT LIKE 'seed-fixo-%'
              AND a.dataHoraInicio >= :agora
            """)
    List<String> findSerieFixaIdsComOcorrenciasFuturas(@Param("agora") LocalDateTime agora);

    @Transactional
    @Modifying
    void deleteBySerieFixaIdStartingWith(String prefix);

    @Query("""
            SELECT a.profissional.nome, a.sala.nome, COUNT(a)
            FROM Agendamento a
            WHERE a.dataHoraInicio >= :inicio
              AND a.dataHoraInicio < :fim
              AND a.profissional.cargo = 'ROLE_PROFISSIONAL'
            GROUP BY a.profissional.id, a.profissional.nome, a.sala.id, a.sala.nome
            ORDER BY a.profissional.nome ASC, a.sala.nome ASC
            """)
    List<Object[]> contarUsoSalasPorProfissionalNoPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    /**
     * Igual ao relatorio por periodo, mas so conta horarios cujo inicio ja passou
     * da regra das 24h (inicio + 24h &lt;= agora).
     */
    @Query("""
            SELECT a.profissional.nome, a.sala.nome, COUNT(a)
            FROM Agendamento a
            WHERE a.dataHoraInicio >= :inicio
              AND a.dataHoraInicio < :fim
              AND a.dataHoraInicio <= :corteConfirmadoApos24h
              AND a.profissional.cargo = 'ROLE_PROFISSIONAL'
            GROUP BY a.profissional.id, a.profissional.nome, a.sala.id, a.sala.nome
            ORDER BY a.profissional.nome ASC, a.sala.nome ASC
            """)
    List<Object[]> contarUsoSalasPorProfissionalNoPeriodoAposRegra24h(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            @Param("corteConfirmadoApos24h") LocalDateTime corteConfirmadoApos24h
    );

    long countByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThan(
            LocalDateTime inicio,
            LocalDateTime fim
    );

    @Query("""
            SELECT COUNT(a)
            FROM Agendamento a
            WHERE (a.fixo IS NULL OR a.fixo = false)
              AND (a.tipoRecorrencia IS NULL OR UPPER(a.tipoRecorrencia) NOT IN ('SEMANAL', 'QUINZENAL'))
            """)
    long countAvulsos();

    @Query("""
            SELECT COUNT(a)
            FROM Agendamento a
            WHERE a.fixo = true
               OR UPPER(COALESCE(a.tipoRecorrencia, '')) IN ('SEMANAL', 'QUINZENAL')
            """)
    long countFixosOuQuinzenais();

    @Query("""
            SELECT COUNT(a)
            FROM Agendamento a
            WHERE a.dataHoraFim < :limite
            """)
    long countComDataHoraFimAntesDe(@Param("limite") LocalDateTime limite);

    @Query("""
            SELECT COUNT(a)
            FROM Agendamento a
            WHERE a.dataHoraInicio >= :inicio
              AND a.dataHoraInicio < :fim
            """)
    long countNoPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @Query("""
            SELECT DISTINCT a.profissional
            FROM Agendamento a
            WHERE a.profissional IS NOT NULL
              AND a.profissional.cargo = 'ROLE_PROFISSIONAL'
              AND a.dataHoraInicio >= :inicio
              AND a.dataHoraInicio < :fim
            ORDER BY a.profissional.nome ASC
            """)
    List<Usuario> findProfissionaisComAgendamentoNoPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM Agendamento a
            WHERE a.dataHoraInicio >= :inicio
              AND a.dataHoraInicio < :fim
              AND (a.fixo IS NULL OR a.fixo = false)
              AND (a.tipoRecorrencia IS NULL OR UPPER(a.tipoRecorrencia) NOT IN ('SEMANAL', 'QUINZENAL'))
            """)
    int deleteAvulsosByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThan(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM Agendamento a
            WHERE a.nomeCliente LIKE :prefixo
            """)
    int deleteByNomeClienteLike(@Param("prefixo") String prefixo);

    List<Agendamento> findByStatusPagamentoAndDataHoraInicioGreaterThanEqual(
            PagamentoStatus statusPagamento,
            LocalDateTime dataHoraInicio
    );

    Optional<Agendamento> findByPagamentoOrderNsu(String pagamentoOrderNsu);

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findAllByPagamentoOrderNsuOrderByDataHoraInicioAsc(String pagamentoOrderNsu);

    List<Agendamento> findByStatusPagamentoAndPagamentoExpiraEmBefore(
            PagamentoStatus statusPagamento,
            LocalDateTime pagamentoExpiraEm
    );

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findByProfissionalIdAndStatusPagamentoAndPagamentoExpiraEmAfterOrderByPagamentoExpiraEmAsc(
            Long profissionalId,
            PagamentoStatus statusPagamento,
            LocalDateTime pagamentoExpiraEm
    );

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findByStatusPagamentoAndPagamentoExpiraEmAfterOrderByPagamentoExpiraEmAsc(
            PagamentoStatus statusPagamento,
            LocalDateTime pagamentoExpiraEm
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    int deleteBySerieFixaIdAndStatusPagamentoNot(String serieFixaId, PagamentoStatus statusPagamento);

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findByProfissionalIdAndDataHoraInicioGreaterThanOrderByDataHoraInicioAsc(
            Long profissionalId,
            LocalDateTime dataHoraInicio
    );

    @EntityGraph(attributePaths = {"profissional", "sala"})
    List<Agendamento> findByDataHoraInicioGreaterThanOrderByDataHoraInicioAsc(LocalDateTime dataHoraInicio);

    @EntityGraph(attributePaths = {"profissional", "sala"})
    @Query("""
            SELECT a FROM Agendamento a
            WHERE a.statusPagamento IN :statuses
              AND a.dataHoraInicio >= :desde
            ORDER BY a.dataHoraInicio ASC
            """)
    List<Agendamento> findByStatusPagamentoInAndDataHoraInicioGreaterThanEqual(
            @Param("statuses") List<PagamentoStatus> statuses,
            @Param("desde") LocalDateTime desde
    );

    @EntityGraph(attributePaths = {"profissional", "sala"})
    @Query("""
            SELECT a FROM Agendamento a
            WHERE a.statusPagamento = com.clinica.sistema.model.PagamentoStatus.PAGO
              AND a.dataPagamento >= :inicio
              AND a.dataPagamento < :fim
            ORDER BY a.dataPagamento DESC, a.id DESC
            """)
    List<Agendamento> findPagosPorDataPagamentoNoPeriodo(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @EntityGraph(attributePaths = {"profissional", "sala"})
    @Query("""
            SELECT a FROM Agendamento a
            WHERE a.sala.id = :salaId
              AND a.dataHoraInicio < :fim
              AND a.dataHoraFim > :inicio
              AND a.id <> :agendamentoId
              AND a.statusPagamento <> com.clinica.sistema.model.PagamentoStatus.LIBERADO_FALTA_PAGAMENTO
            ORDER BY a.id ASC
            """)
    Optional<Agendamento> findFirstOcupacaoAtivaNoHorarioExceto(
            @Param("salaId") Long salaId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            @Param("agendamentoId") Long agendamentoId
    );

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Agendamento a
            WHERE a.sala.id = :salaId
              AND a.dataHoraInicio < :fim
              AND a.dataHoraFim > :inicio
              AND a.statusPagamento <> com.clinica.sistema.model.PagamentoStatus.LIBERADO_FALTA_PAGAMENTO
            """)
    boolean existsOcupacaoAtivaNoHorario(
            @Param("salaId") Long salaId,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );
}
