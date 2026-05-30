package com.clinica.sistema.repository;

import com.clinica.sistema.model.EncerramentoSerieRegistro;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EncerramentoSerieRegistroRepository extends JpaRepository<EncerramentoSerieRegistro, Long> {

    @EntityGraph(attributePaths = {"profissional", "sala", "encerradoPor"})
    List<EncerramentoSerieRegistro> findTop30ByOrderByEncerradoEmDesc();

    @EntityGraph(attributePaths = {"profissional", "sala", "encerradoPor"})
    java.util.Optional<EncerramentoSerieRegistro> findFirstByOrderByEncerradoEmDescIdDesc();

    long countByIdGreaterThan(Long id);
}
