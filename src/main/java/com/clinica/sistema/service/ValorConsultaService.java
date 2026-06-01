package com.clinica.sistema.service;

import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.Sala;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ValorConsultaService {

    public static final BigDecimal CLINICA_SALA_4 = new BigDecimal("25.00");
    public static final BigDecimal CLINICA_FIXO_SEMANAL = new BigDecimal("32.00");
    public static final BigDecimal CLINICA_AVULSO_QUINZENAL = new BigDecimal("35.00");
    public static final BigDecimal INDICACAO_PERCENTUAL = new BigDecimal("0.30");

    public void aplicarValores(Agendamento agendamento, AgendamentoForm form, Sala sala, String recorrencia) {
        aplicarValores(agendamento, form, sala, recorrencia, true);
    }

    public void aplicarValores(
            Agendamento agendamento,
            AgendamentoForm form,
            Sala sala,
            String recorrencia,
            boolean permitirIndicacao
    ) {
        BigDecimal valorRecebe = normalizarValor(form.getValorProfissionalRecebe(), "Informe quanto o cliente paga ao profissional.");
        boolean indicacao = permitirIndicacao && form.isIndicacaoDona();
        BigDecimal valorClinica = indicacao
                ? calcularTarifaClinicaIndicacao(valorRecebe)
                : resolverValorClinicaSemIndicacao(form, sala, recorrencia, valorRecebe);
        agendamento.setValorProfissionalRecebe(valorRecebe);
        agendamento.setValorClinicaCobra(valorClinica);
        agendamento.setValorLiquidoProfissional(calcularLiquido(valorRecebe, valorClinica));
        agendamento.setIndicacaoDona(indicacao);
    }

    private BigDecimal resolverValorClinicaSemIndicacao(
            AgendamentoForm form,
            Sala sala,
            String recorrencia,
            BigDecimal valorRecebe
    ) {
        if (form.getValorClinicaCobra() != null && form.getValorClinicaCobra().signum() > 0) {
            return normalizarValor(form.getValorClinicaCobra(), "Informe quanto a clinica cobra nesta sessao.");
        }
        return calcularTarifaClinicaPadrao(sala, recorrencia);
    }

    public BigDecimal calcularTarifaClinicaPadrao(Sala sala, String recorrencia) {
        if (isSala4(sala)) {
            return CLINICA_SALA_4;
        }
        if ("SEMANAL".equalsIgnoreCase(recorrencia)) {
            return CLINICA_FIXO_SEMANAL;
        }
        return CLINICA_AVULSO_QUINZENAL;
    }

    /**
     * Prioridade: indicacao da dona (30%) substitui qualquer tarifa fixa (Sala 4, fixo, avulso).
     */
    public BigDecimal resolverValorClinicaParaForm(
            AgendamentoForm form,
            Sala sala,
            String recorrencia,
            BigDecimal valorRecebe
    ) {
        if (form.isIndicacaoDona()) {
            return calcularTarifaClinicaIndicacao(valorRecebe);
        }
        if (form.getValorClinicaCobra() != null && form.getValorClinicaCobra().signum() > 0) {
            return normalizarValor(form.getValorClinicaCobra(), "Informe quanto a clinica cobra nesta sessao.");
        }
        return calcularTarifaClinicaPadrao(sala, recorrencia);
    }

    public BigDecimal calcularTarifaClinicaIndicacao(BigDecimal valorConsulta) {
        return valorConsulta
                .multiply(INDICACAO_PERCENTUAL)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calcularLiquido(BigDecimal valorRecebe, BigDecimal valorClinica) {
        return valorRecebe.subtract(valorClinica).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizarValor(BigDecimal valor, String mensagemErro) {
        if (valor == null || valor.signum() <= 0) {
            throw new RuntimeException(mensagemErro);
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isSala4(Sala sala) {
        return sala != null
                && sala.getNome() != null
                && sala.getNome().trim().equalsIgnoreCase("Sala 4");
    }

    public void copiarValores(Agendamento destino, Agendamento origem) {
        destino.setValorProfissionalRecebe(origem.getValorProfissionalRecebe());
        destino.setValorClinicaCobra(origem.getValorClinicaCobra());
        destino.setValorLiquidoProfissional(origem.getValorLiquidoProfissional());
        destino.setIndicacaoDona(origem.getIndicacaoDona());
    }

    /** Demais datas de uma serie semanal/quinzenal: taxa padrao da clinica, sem indicacao. */
    public void copiarValoresOcorrenciaSerie(
            Agendamento destino,
            Agendamento origem,
            Sala sala,
            String recorrencia
    ) {
        BigDecimal valorRecebe = origem.getValorProfissionalRecebe();
        destino.setValorProfissionalRecebe(valorRecebe);
        destino.setIndicacaoDona(false);
        BigDecimal valorClinica = calcularTarifaClinicaPadrao(sala, recorrencia);
        destino.setValorClinicaCobra(valorClinica);
        if (valorRecebe != null) {
            destino.setValorLiquidoProfissional(calcularLiquido(valorRecebe, valorClinica));
        }
    }
}
