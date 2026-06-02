package com.clinica.sistema.repository;

import com.clinica.sistema.model.NovoAgendamentoNotificacaoRegistro;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NovoAgendamentoNotificacaoRegistroRepository extends JpaRepository<NovoAgendamentoNotificacaoRegistro, Long> {

    @EntityGraph(attributePaths = {"profissional", "sala", "registradoPor"})
    List<NovoAgendamentoNotificacaoRegistro> findTop15ByIdGreaterThanOrderByRegistradoEmDescIdDesc(Long ultimoVistoId);

    @EntityGraph(attributePaths = {"profissional", "sala", "registradoPor"})
    Optional<NovoAgendamentoNotificacaoRegistro> findFirstByOrderByRegistradoEmDescIdDesc();

    long countByIdGreaterThan(Long ultimoVistoId);
}
