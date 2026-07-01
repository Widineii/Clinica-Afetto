package com.clinica.sistema.repository;

import com.clinica.sistema.model.PacienteCadernoObservacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PacienteCadernoObservacaoRepository extends JpaRepository<PacienteCadernoObservacao, Long> {

    List<PacienteCadernoObservacao> findByProfissionalIdAndChaveCadernoOrderByCriadoEmAsc(
            Long profissionalId,
            String chaveCaderno
    );

    Optional<PacienteCadernoObservacao> findByIdAndProfissionalId(Long id, Long profissionalId);

    void deleteByProfissionalIdAndChaveCaderno(Long profissionalId, String chaveCaderno);

    List<PacienteCadernoObservacao> findByProfissionalIdOrderByCriadoEmDesc(Long profissionalId);

    List<PacienteCadernoObservacao> findByProfissionalIdAndCriadoEmGreaterThanEqualOrderByCriadoEmAsc(
            Long profissionalId,
            LocalDateTime inicioInclusive
    );

    List<PacienteCadernoObservacao> findByProfissionalIdAndLembreteEmBetweenOrderByLembreteEmAsc(
            Long profissionalId,
            LocalDateTime inicioInclusive,
            LocalDateTime fimInclusive
    );
}
