package com.clinica.sistema.repository;

import com.clinica.sistema.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByLogin(String login);

    List<Usuario> findByCargoOrderByNomeAsc(String cargo);

    @Query("""
            SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END
            FROM Usuario u
            WHERE u.senha IS NOT NULL
              AND u.senha <> ''
              AND u.senha NOT LIKE '$2a$%'
            """)
    boolean existsSenhaLegada();

    long countByCargo(String cargo);
}
