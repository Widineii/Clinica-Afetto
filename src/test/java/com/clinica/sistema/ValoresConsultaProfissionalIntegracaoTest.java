package com.clinica.sistema;

import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.AtualizarValoresConsultaProfissionalForm;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ValoresConsultaProfissionalIntegracaoTest {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void polyanaSalvaValorDaJuliaEFormularioDaAgendaRecebePadrao() {
        Usuario polyana = usuarioRepository.findByLogin("polyana").orElseThrow();
        Usuario julia = usuarioRepository.findByLogin("julia").orElseThrow();

        AtualizarValoresConsultaProfissionalForm valores = new AtualizarValoresConsultaProfissionalForm();
        valores.setUsuarioId(julia.getId());
        valores.setValorAvulso(new BigDecimal("199.50"));
        valores.setValorSemanal(new BigDecimal("189.00"));
        valores.setValorQuinzenal(new BigDecimal("179.00"));
        valores.setValorMensal(new BigDecimal("169.00"));
        usuarioService.atualizarValoresConsultaProfissional(valores, polyana);

        Usuario juliaAtualizada = usuarioRepository.findById(julia.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("199.50").compareTo(juliaAtualizada.getValorConsultaAvulso()));

        AgendamentoForm form = new AgendamentoForm();
        form.setProfissionalId(julia.getId());
        form.setRecorrencia("AVULSO");
        usuarioService.preencherValorConsultaPadraoNoForm(form, julia.getId(), "AVULSO");
        assertEquals(0, new BigDecimal("199.50").compareTo(form.getValorProfissionalRecebe()));

        String json = usuarioService.jsonValoresConsultaPadraoPorProfissional();
        assertTrue(json.contains("199.50"), () -> "JSON deveria expor valor da Julia: " + json);
    }
}
