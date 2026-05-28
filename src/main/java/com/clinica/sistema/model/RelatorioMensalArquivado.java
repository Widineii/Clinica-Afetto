package com.clinica.sistema.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "relatorios_mensais_arquivados",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ano", "mes"})
)
@Data
public class RelatorioMensalArquivado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int ano;

    private int mes;

    @Column(nullable = false)
    private String mesLabel;

    private long totalGeral;

    private long agendamentosRemovidos;

    @Column(nullable = false)
    private LocalDateTime geradoEm;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] pdf;

    private LocalDateTime pdfRemovidoEm;

    /** Admin/dona baixou o PDF mensal pelo sino; some o aviso ate o proximo mes. */
    private LocalDateTime pdfNotificacaoBaixadoEm;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String dadosJson;

    public boolean temPdfDisponivel() {
        return pdf != null && pdf.length > 0;
    }
}
