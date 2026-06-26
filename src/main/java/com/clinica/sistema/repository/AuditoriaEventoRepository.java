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
}
