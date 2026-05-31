package com.clinica.sistema.repository;

import com.clinica.sistema.model.NovidadeSite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NovidadeSiteRepository extends JpaRepository<NovidadeSite, Long> {

    Optional<NovidadeSite> findByCodigo(String codigo);

    List<NovidadeSite> findAllByOrderByOrdemExibicaoDescPublicadaEmDesc();
}
