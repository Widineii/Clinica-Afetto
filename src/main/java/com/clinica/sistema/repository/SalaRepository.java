package com.clinica.sistema.repository;

import com.clinica.sistema.model.Sala;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalaRepository extends JpaRepository<Sala, Long> {
    List<Sala> findAllByOrderByNomeAsc();

    boolean existsByNomeIgnoreCase(String nome);
}
