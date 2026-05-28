package com.clinica.sistema;

import com.clinica.sistema.service.AgendamentoService;
import com.clinica.sistema.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class AdminAgendaProfissionaisIntegracaoTest {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private AgendamentoService agendamentoService;

    @Test
    void deveTerProfissionaisCadastradosNoBancoLocal() {
        var equipe = usuarioService.listarProfissionaisDaEquipe();
        assertFalse(equipe.isEmpty(), "Equipe padrao deveria existir apos startup");
        var resumos = agendamentoService.montarResumosProfissionais(equipe);
        assertFalse(resumos.isEmpty());
        assertTrue(resumos.stream().anyMatch(r -> r.getProfissionalNome().equalsIgnoreCase("Julia")));
        assertTrue(resumos.stream().anyMatch(r -> r.getProfissionalNome().equalsIgnoreCase("Polyana")));
    }
}
