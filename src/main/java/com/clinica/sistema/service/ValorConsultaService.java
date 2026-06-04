package com.clinica.sistema.service;

import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.TurnoLocacao;
import com.clinica.sistema.model.Agendamento;
import com.clinica.sistema.model.PagamentoStatus;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

@Service
public class ValorConsultaService {

    public static final BigDecimal CLINICA_SALA_4 = new BigDecimal("25.00");
    public static final BigDecimal CLINICA_FIXO_SEMANAL = new BigDecimal("35.00");
    public static final BigDecimal CLINICA_AVULSO = new BigDecimal("35.00");
    public static final BigDecimal CLINICA_QUINZENAL = new BigDecimal("35.00");
    public static final BigDecimal CLINICA_MENSAL = new BigDecimal("32.00");
    public static final BigDecimal CLINICA_TURNO_LOCACAO = new BigDecimal("500.00");
    /** @deprecated use {@link #CLINICA_AVULSO} */
    @Deprecated
    public static final BigDecimal CLINICA_AVULSO_QUINZENAL = CLINICA_AVULSO;
    public static final BigDecimal INDICACAO_PERCENTUAL = new BigDecimal("0.30");
    public static final BigDecimal INDICACAO_PERCENTUAL_PADRAO = new BigDecimal("30.00");

    /** Taxa de sala padrao do sistema (avulso/semanal/quinzenal 35; mensal 32). */
    public static BigDecimal taxaSalaPadraoSistema(String recorrencia) {
        return valorClientePadraoPorRecorrencia(recorrencia);
    }

    /** @deprecated use {@link #taxaSalaPadraoSistema(String)} */
    @Deprecated
    public static BigDecimal valorClientePadraoPorRecorrencia(String recorrencia) {
        if (recorrencia == null) {
            return CLINICA_AVULSO;
        }
        return switch (recorrencia.toUpperCase(Locale.ROOT)) {
            case "SEMANAL" -> CLINICA_FIXO_SEMANAL;
            case "QUINZENAL" -> CLINICA_QUINZENAL;
            case "MENSAL" -> CLINICA_MENSAL;
            default -> CLINICA_AVULSO;
        };
    }

    public void aplicarValores(Agendamento agendamento, AgendamentoForm form, Sala sala, String recorrencia) {
        aplicarValores(agendamento, form, sala, recorrencia, true);
    }

    public void aplicarValorConsultaPropriaDona(Agendamento agendamento, AgendamentoForm form) {
        BigDecimal valorRecebe = normalizarValor(
                form.getValorProfissionalRecebe(),
                "Informe o valor da sua consulta."
        );
        agendamento.setValorProfissionalRecebe(valorRecebe);
        agendamento.setValorClinicaCobra(BigDecimal.ZERO);
        agendamento.setValorLiquidoProfissional(valorRecebe);
        agendamento.setIndicacaoDona(false);
    }

    public void aplicarValores(
            Agendamento agendamento,
            AgendamentoForm form,
            Sala sala,
            String recorrencia,
            boolean permitirIndicacao
    ) {
        aplicarValores(agendamento, form, sala, recorrencia, permitirIndicacao, null);
    }

    public void aplicarValores(
            Agendamento agendamento,
            AgendamentoForm form,
            Sala sala,
            String recorrencia,
            boolean permitirIndicacao,
            Usuario profissional
    ) {
        BigDecimal valorRecebe = resolverValorProfissionalRecebe(form, profissional, recorrencia);
        boolean indicacao = permitirIndicacao
                && !TurnoLocacao.isTurno(form.getTurnoLocacao())
                && form.isIndicacaoDona();
        BigDecimal valorClinica = indicacao
                ? calcularTarifaClinicaIndicacao(valorRecebe, profissional)
                : resolverValorClinicaSemIndicacao(form, sala, recorrencia, profissional);
        agendamento.setValorProfissionalRecebe(valorRecebe);
        agendamento.setValorClinicaCobra(valorClinica);
        agendamento.setValorLiquidoProfissional(calcularLiquido(valorRecebe, valorClinica));
        agendamento.setIndicacaoDona(indicacao);
    }

    private BigDecimal resolverValorClinicaSemIndicacao(
            AgendamentoForm form,
            Sala sala,
            String recorrencia,
            Usuario profissional
    ) {
        if (TurnoLocacao.isTurno(form.getTurnoLocacao())) {
            return CLINICA_TURNO_LOCACAO;
        }
        return resolverTaxaSalaProfissional(profissional, sala, recorrencia, form.getTurnoLocacao());
    }

    public BigDecimal calcularTarifaClinicaPadrao(Sala sala, String recorrencia) {
        return calcularTarifaClinicaPadrao(sala, recorrencia, null);
    }

    public BigDecimal calcularTarifaClinicaPadrao(Sala sala, String recorrencia, String turnoLocacao) {
        if (TurnoLocacao.isTurno(turnoLocacao)) {
            return CLINICA_TURNO_LOCACAO;
        }
        if (isSala4(sala)) {
            return CLINICA_SALA_4;
        }
        if ("SEMANAL".equalsIgnoreCase(recorrencia)) {
            return CLINICA_FIXO_SEMANAL;
        }
        if ("QUINZENAL".equalsIgnoreCase(recorrencia)) {
            return CLINICA_QUINZENAL;
        }
        if ("MENSAL".equalsIgnoreCase(recorrencia)) {
            return CLINICA_MENSAL;
        }
        return CLINICA_AVULSO;
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
        if (TurnoLocacao.isTurno(form.getTurnoLocacao())) {
            return CLINICA_TURNO_LOCACAO;
        }
        return calcularTarifaClinicaPadrao(sala, recorrencia, form.getTurnoLocacao());
    }

    public static BigDecimal percentualTaxaIndicacaoPadrao() {
        return INDICACAO_PERCENTUAL_PADRAO;
    }

    public BigDecimal percentualTaxaIndicacao(Usuario profissional) {
        if (profissional != null && profissional.getPercentualTaxaIndicacao() != null
                && profissional.getPercentualTaxaIndicacao().signum() > 0) {
            return profissional.getPercentualTaxaIndicacao().setScale(2, RoundingMode.HALF_UP);
        }
        return INDICACAO_PERCENTUAL_PADRAO;
    }

    public BigDecimal fracaoTaxaIndicacao(Usuario profissional) {
        return percentualTaxaIndicacao(profissional)
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
    }

    public BigDecimal calcularTarifaClinicaIndicacao(BigDecimal valorConsulta) {
        return calcularTarifaClinicaIndicacao(valorConsulta, null);
    }

    public BigDecimal calcularTarifaClinicaIndicacao(BigDecimal valorConsulta, Usuario profissional) {
        return valorConsulta
                .multiply(fracaoTaxaIndicacao(profissional))
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

    private BigDecimal resolverValorProfissionalRecebe(
            AgendamentoForm form,
            Usuario profissional,
            String recorrencia
    ) {
        BigDecimal informado = form != null ? form.getValorProfissionalRecebe() : null;
        if (informado != null && informado.signum() > 0) {
            return normalizarValor(informado, "Informe quanto o cliente paga ao profissional.");
        }
        throw new RuntimeException("Informe quanto o cliente paga ao profissional.");
    }

    /** Taxa de sala cadastrada na Central → Valores (por tipo de agendamento). */
    public Optional<BigDecimal> taxaSalaCadastradaProfissional(Usuario profissional, String recorrencia) {
        if (profissional == null || recorrencia == null) {
            return Optional.empty();
        }
        BigDecimal valor = switch (recorrencia.toUpperCase(Locale.ROOT)) {
            case "SEMANAL" -> profissional.getValorConsultaSemanal();
            case "QUINZENAL" -> profissional.getValorConsultaQuinzenal();
            case "MENSAL" -> profissional.getValorConsultaMensal();
            default -> profissional.getValorConsultaAvulso();
        };
        if (valor != null && valor.signum() > 0) {
            return Optional.of(valor.setScale(2, RoundingMode.HALF_UP));
        }
        return Optional.empty();
    }

    public BigDecimal resolverTaxaSalaProfissional(
            Usuario profissional,
            Sala sala,
            String recorrencia,
            String turnoLocacao
    ) {
        if (TurnoLocacao.isTurno(turnoLocacao)) {
            return CLINICA_TURNO_LOCACAO;
        }
        return taxaSalaCadastradaProfissional(profissional, recorrencia)
                .orElseGet(() -> calcularTarifaClinicaPadrao(sala, recorrencia, turnoLocacao));
    }

    /** @deprecated taxa de sala — use {@link #taxaSalaCadastradaProfissional(Usuario, String)} */
    @Deprecated
    public Optional<BigDecimal> valorPadraoProfissionalRecebe(Usuario profissional, String recorrencia) {
        return taxaSalaCadastradaProfissional(profissional, recorrencia);
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
        BigDecimal valorClinica = resolverTaxaSalaProfissional(
                origem.getProfissional(),
                sala,
                recorrencia,
                origem.getTurnoLocacao()
        );
        destino.setValorClinicaCobra(valorClinica);
        if (valorRecebe != null) {
            destino.setValorLiquidoProfissional(calcularLiquido(valorRecebe, valorClinica));
        }
    }

    /**
     * Propaga taxa de sala da Central para consultas existentes do profissional.
     * Usa a taxa do tipo (avulso/semanal/quinzenal/mensal) de cada agendamento.
     * Consultas ja pagas nao sao alteradas; pendentes recalculam Clin. e Liquido (Prof. recebe intacto).
     */
    public boolean aplicarValoresPadraoProfissionalNoAgendamento(Agendamento agendamento, Usuario profissional) {
        if (agendamento == null || profissional == null || TurnoLocacao.isTurno(agendamento.getTurnoLocacao())) {
            return false;
        }
        if (PagamentoStatus.PAGO.equals(agendamento.getStatusPagamento())) {
            return false;
        }
        String recorrencia = recorrenciaDoAgendamento(agendamento);
        BigDecimal valorRecebe = agendamento.getValorProfissionalRecebe();
        BigDecimal valorClinica = agendamento.isIndicacaoDona()
                && valorRecebe != null
                && valorRecebe.signum() > 0
                ? calcularTarifaClinicaIndicacao(valorRecebe, profissional)
                : resolverTaxaSalaProfissional(
                        profissional,
                        agendamento.getSala(),
                        recorrencia,
                        agendamento.getTurnoLocacao()
                );

        BigDecimal valorLiquidoAtual = agendamento.getValorLiquidoProfissional();
        BigDecimal valorLiquidoNovo = valorLiquidoAtual;
        if (valorRecebe != null && valorRecebe.signum() > 0) {
            valorLiquidoNovo = calcularLiquido(valorRecebe, valorClinica);
        }

        boolean alterado = !mesmoValor(agendamento.getValorClinicaCobra(), valorClinica);
        if (valorRecebe != null && valorRecebe.signum() > 0) {
            alterado = alterado || !mesmoValor(valorLiquidoAtual, valorLiquidoNovo);
        }
        if (!alterado) {
            return false;
        }

        agendamento.setValorClinicaCobra(valorClinica);
        if (valorRecebe != null && valorRecebe.signum() > 0) {
            agendamento.setValorLiquidoProfissional(valorLiquidoNovo);
        }
        sincronizarValorPagamentoPendente(agendamento, valorClinica);
        return true;
    }

    public String recorrenciaDoAgendamento(Agendamento agendamento) {
        if (agendamento.isQuinzenal()) {
            return "QUINZENAL";
        }
        if (agendamento.isMensal()) {
            return "MENSAL";
        }
        if (agendamento.isFixoSemanal()) {
            return "SEMANAL";
        }
        if (agendamento.getTipoRecorrencia() != null && !agendamento.getTipoRecorrencia().isBlank()) {
            return agendamento.getTipoRecorrencia().toUpperCase(Locale.ROOT);
        }
        if (Boolean.TRUE.equals(agendamento.getFixo())) {
            return "SEMANAL";
        }
        return "AVULSO";
    }

    private void sincronizarValorPagamentoPendente(Agendamento agendamento, BigDecimal taxaClinica) {
        if (agendamento.getStatusPagamento() == PagamentoStatus.PAGO || taxaClinica == null) {
            return;
        }
        agendamento.setValorPagamento(taxaClinica.setScale(2, RoundingMode.HALF_UP));
    }

    private static boolean mesmoValor(BigDecimal atual, BigDecimal novo) {
        if (atual == null && novo == null) {
            return true;
        }
        if (atual == null || novo == null) {
            return false;
        }
        return atual.setScale(2, RoundingMode.HALF_UP).compareTo(novo.setScale(2, RoundingMode.HALF_UP)) == 0;
    }
}
