package com.clinica.sistema.repository;

import com.clinica.sistema.model.AuditoriaEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditoriaEventoRepository extends JpaRepository<AuditoriaEvento, Long> {

    List<AuditoriaEvento> findByCriadoEmGreaterThanEqualAndCriadoEmLessThanOrderByCriadoEmDesc(
            LocalDateTime inicioInclusive,
            LocalDateTime fimExclusive
    );

    @Modifying
    @Query("DELETE FROM AuditoriaEvento evento WHERE evento.criadoEm < :limite")
    int deleteByCriadoEmBefore(@Param("limite") LocalDateTime limite);

    long countByTipoAndDescricaoContainingIgnoreCase(String tipo, String trecho);

    @Query("""
            SELECT COUNT(evento) FROM AuditoriaEvento evento
            WHERE evento.tipo = :tipo
              AND LOWER(evento.descricao) LIKE LOWER(CONCAT('%', :marcadorCliente, '%'))
              AND (
                  LOWER(evento.descricao) LIKE LOWER(CONCAT('% na ', :sala, ' %'))
                  OR LOWER(evento.descricao) LIKE LOWER(CONCAT('% da ', :sala, ' %'))
              )
            """)
    long countCancelamentosClienteComSala(
            @Param("tipo") String tipo,
            @Param("marcadorCliente") String marcadorCliente,
            @Param("sala") String sala
    );
}
