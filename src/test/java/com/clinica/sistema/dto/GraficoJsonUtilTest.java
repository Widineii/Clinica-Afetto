package com.clinica.sistema.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GraficoJsonUtilTest {

    @Test
    void deveSerializarPagamentosPixComoJsonValido() {
        ReceitaPixLinhaView linha = new ReceitaPixLinhaView(
                criarAgendamento("Julia", "Sala 1", "AVULSO"),
                new BigDecimal("35.00")
        );

        String json = GraficoJsonUtil.serializarPagamentosPix(List.of(linha));

        assertTrue(json.contains("\"profissionalNome\":\"Julia\""));
        assertTrue(json.contains("\"valorTaxa\":35.00"));
    }

    @Test
    void deveSerializarGraficosDoRelatorioSemanal() {
        RelatorioLinhaView linha = new RelatorioLinhaView();
        linha.setProfissionalNome("Carol");
        linha.setTotalHorarios(5);
        linha.setSala1(2);
        linha.setSala2(3);

        String salas = GraficoJsonUtil.serializarUsoSalasRelatorio(List.of(linha));
        String profissionais = GraficoJsonUtil.serializarProfissionaisRelatorio(List.of(linha));

        assertTrue(salas.contains("\"rotulo\":\"Sala 1\""));
        assertTrue(salas.contains("\"valor\":2"));
        assertTrue(profissionais.contains("\"rotulo\":\"Carol\""));
        assertTrue(profissionais.contains("\"valor\":5"));
    }

    private com.clinica.sistema.model.Agendamento criarAgendamento(
            String profissional,
            String sala,
            String tipo
    ) {
        com.clinica.sistema.model.Usuario usuario = new com.clinica.sistema.model.Usuario();
        usuario.setNome(profissional);

        com.clinica.sistema.model.Sala salaEntidade = new com.clinica.sistema.model.Sala();
        salaEntidade.setNome(sala);

        com.clinica.sistema.model.Agendamento agendamento = new com.clinica.sistema.model.Agendamento();
        agendamento.setProfissional(usuario);
        agendamento.setSala(salaEntidade);
        agendamento.setTipoRecorrencia(tipo);
        return agendamento;
    }
}
