package com.clinica.sistema.repository;

import com.clinica.sistema.model.Despesa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DespesaRepository extends JpaRepository<Despesa, Long> {
}
