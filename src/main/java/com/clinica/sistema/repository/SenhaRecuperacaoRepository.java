package com.clinica.sistema.repository;

import com.clinica.sistema.model.SenhaRecuperacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SenhaRecuperacaoRepository extends JpaRepository<SenhaRecuperacao, Long> {

    Optional<SenhaRecuperacao> findFirstByUsuarioIdAndUsadoEmIsNullOrderByCriadoEmDesc(Long usuarioId);
}
