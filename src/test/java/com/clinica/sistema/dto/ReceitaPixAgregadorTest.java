package com.clinica.sistema.dto;

import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceitaPixAgregadorTest {

    @Test
    void deveAgregarReceitaPorSalaProfissionalETipo() {
        List<ReceitaPixLinhaView> linhas = List.of(
                linha("Julia", "Sala 1", "AVULSO", "35.00"),
                linha("Carol", "Sala 2", "SEMANAL", "32.00"),
                linha("Julia", "Sala 1", "QUINZENAL", "25.00")
        );
        BigDecimal total = new BigDecimal("92.00");

        List<ReceitaPixFatiaView> porSala = ReceitaPixAgregador.porSala(linhas, total);
        List<ReceitaPixFatiaView> porProfissional = ReceitaPixAgregador.porProfissional(linhas, total);
        List<ReceitaPixFatiaView> porTipo = ReceitaPixAgregador.porTipo(linhas, total);

        assertEquals(2, porSala.size());
        assertEquals("Sala 1", porSala.get(0).getRotulo());
        assertEquals(0, new BigDecimal("60.00").compareTo(porSala.get(0).getValor()));

        assertEquals(2, porProfissional.size());
        assertEquals("Julia", porProfissional.get(0).getRotulo());

        assertEquals(3, porTipo.size());
        assertTrue(porTipo.stream().anyMatch(fatia -> "Avulso".equals(fatia.getRotulo())));
    }

    private ReceitaPixLinhaView linha(String profissional, String sala, String tipo, String valor) {
        Usuario usuario = new Usuario();
        usuario.setNome(profissional);

        Sala salaEntidade = new Sala();
        salaEntidade.setNome(sala);

        Agendamento agendamento = new Agendamento();
        agendamento.setProfissional(usuario);
        agendamento.setSala(salaEntidade);
        agendamento.setTipoRecorrencia(tipo);
        agendamento.setDataPagamento(LocalDateTime.of(2026, 5, 10, 10, 0));
        agendamento.setDataHoraInicio(LocalDateTime.of(2026, 5, 10, 11, 0));

        return new ReceitaPixLinhaView(agendamento, new BigDecimal(valor));
    }
}
