package com.clinica.sistema.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "contrato_licenciamento_rascunho")
@Data
public class ContratoLicenciamentoRascunho {

    public static final String ID_PADRAO = "padrao";
    public static final String ID_BRUTO = "bruto";
    public static final String ID_MENSALIDADE = "mensalidade";

    @Id
    @Column(length = 40)
    private String id = ID_PADRAO;

    @Column(name = "dados_json", nullable = false, columnDefinition = "TEXT")
    private String dadosJson = "{}";

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "atualizado_por_usuario_id")
    private Long atualizadoPorUsuarioId;

    @Column(name = "atualizado_por_nome", length = 120)
    private String atualizadoPorNome;

    @Column(name = "contratante_finalizado", nullable = false)
    private boolean contratanteFinalizado = false;

    @Column(name = "contratante_finalizado_em")
    private LocalDateTime contratanteFinalizadoEm;

    @Column(name = "contratante_finalizado_por_nome", length = 120)
    private String contratanteFinalizadoPorNome;
}
