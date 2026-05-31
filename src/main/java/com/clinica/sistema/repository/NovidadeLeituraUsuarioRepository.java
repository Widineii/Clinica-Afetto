package com.clinica.sistema.repository;

import com.clinica.sistema.model.NovidadeLeituraUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NovidadeLeituraUsuarioRepository extends JpaRepository<NovidadeLeituraUsuario, Long> {

    Optional<NovidadeLeituraUsuario> findByUsuarioIdAndNovidadeId(Long usuarioId, Long novidadeId);

    List<NovidadeLeituraUsuario> findByUsuarioIdAndNovidadeIdIn(Long usuarioId, Collection<Long> novidadeIds);
}
