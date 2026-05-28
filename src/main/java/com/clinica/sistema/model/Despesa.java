package com.clinica.sistema.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Entity
@Table(name = "despesas")
@Data
public class Despesa {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoDespesa tipo;

    @Column(nullable = false, length = 200)
    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    /** Inicio da despesa mensal ou data do pagamento unico. */
    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    /** Ultimo mes em que a despesa mensal permanece ativa (inclusive). */
    @Column(name = "mes_encerrado", length = 7)
    private String mesEncerrado;

    public YearMonth getMesEncerradoAsYearMonth() {
        if (mesEncerrado == null || mesEncerrado.isBlank()) {
            return null;
        }
        return YearMonth.parse(mesEncerrado);
    }

    public void setMesEncerradoFrom(YearMonth mes) {
        this.mesEncerrado = mes != null ? mes.toString() : null;
    }

    public boolean ativaNoMes(YearMonth mes) {
        if (tipo == TipoDespesa.UNICA) {
            return YearMonth.from(dataReferencia).equals(mes);
        }
        YearMonth inicio = YearMonth.from(dataReferencia);
        if (mes.isBefore(inicio)) {
            return false;
        }
        YearMonth encerrado = getMesEncerradoAsYearMonth();
        return encerrado == null || !mes.isAfter(encerrado);
    }

    public String getValorFormatado() {
        if (valor == null) {
            return "—";
        }
        NumberFormat formato = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        return formato.format(valor);
    }

    public String getDataReferenciaFormatada() {
        return dataReferencia != null ? dataReferencia.format(DATA_BR) : "—";
    }
}
