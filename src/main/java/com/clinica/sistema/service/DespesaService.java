package com.clinica.sistema.service;

import com.clinica.sistema.dto.DespesaForm;
import com.clinica.sistema.dto.DespesaLinhaView;
import com.clinica.sistema.dto.DespesaResumoMesView;
import com.clinica.sistema.model.Despesa;
import com.clinica.sistema.model.TipoDespesa;
import com.clinica.sistema.repository.DespesaRepository;
import com.clinica.sistema.util.MoedaBrasilUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

@Service
public class DespesaService {

    private final DespesaRepository repository;

    public DespesaService(DespesaRepository repository) {
        this.repository = repository;
    }

    public DespesaResumoMesView montarResumoMes(YearMonth mesSelecionado) {
        List<Despesa> todas = repository.findAll();

        List<DespesaLinhaView> mensais = todas.stream()
                .filter(d -> d.getTipo() == TipoDespesa.MENSAL)
                .filter(d -> d.ativaNoMes(mesSelecionado))
                .sorted(Comparator.comparing(Despesa::getDescricao, String.CASE_INSENSITIVE_ORDER))
                .map(DespesaLinhaView::new)
                .toList();

        List<DespesaLinhaView> unicas = todas.stream()
                .filter(d -> d.getTipo() == TipoDespesa.UNICA)
                .filter(d -> d.ativaNoMes(mesSelecionado))
                .sorted(Comparator.comparing(Despesa::getDataReferencia))
                .map(DespesaLinhaView::new)
                .toList();

        BigDecimal totalMensais = somarValores(todas, mesSelecionado, TipoDespesa.MENSAL);
        BigDecimal totalUnicas = somarValores(todas, mesSelecionado, TipoDespesa.UNICA);

        return new DespesaResumoMesView(mesSelecionado, mensais, unicas, totalMensais, totalUnicas);
    }

    @Transactional
    public Despesa cadastrar(DespesaForm form) {
        validarFormulario(form);

        Despesa despesa = new Despesa();
        despesa.setTipo(form.getTipo());
        despesa.setDescricao(form.getDescricao().trim());
        despesa.setValor(MoedaBrasilUtil.parse(form.getValor()));
        despesa.setDataReferencia(form.getData());
        return repository.save(despesa);
    }

    @Transactional
    public void encerrarMensal(Long id, YearMonth mesSelecionado) {
        Despesa despesa = buscarObrigatoria(id);
        if (despesa.getTipo() != TipoDespesa.MENSAL) {
            throw new RuntimeException("Somente despesas mensais podem ser encerradas.");
        }
        if (!despesa.ativaNoMes(mesSelecionado)) {
            throw new RuntimeException("Esta despesa mensal nao esta ativa no mes selecionado.");
        }
        despesa.setMesEncerradoFrom(mesSelecionado);
        repository.save(despesa);
    }

    @Transactional
    public void excluirUnica(Long id, YearMonth mesSelecionado) {
        Despesa despesa = buscarObrigatoria(id);
        if (despesa.getTipo() != TipoDespesa.UNICA) {
            throw new RuntimeException("Somente despesas unicas podem ser excluidas por esta acao.");
        }
        if (!despesa.ativaNoMes(mesSelecionado)) {
            throw new RuntimeException("Esta despesa unica nao pertence ao mes selecionado.");
        }
        repository.delete(despesa);
    }

    private Despesa buscarObrigatoria(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Despesa nao encontrada."));
    }

    private BigDecimal somarValores(List<Despesa> despesas, YearMonth mes, TipoDespesa tipo) {
        return despesas.stream()
                .filter(d -> d.getTipo() == tipo)
                .filter(d -> d.ativaNoMes(mes))
                .map(Despesa::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validarFormulario(DespesaForm form) {
        if (form.getTipo() == null) {
            throw new RuntimeException("Selecione o tipo da despesa.");
        }
        if (form.getDescricao() == null || form.getDescricao().isBlank()) {
            throw new RuntimeException("Informe a descricao da despesa.");
        }
        if (form.getDescricao().trim().length() > 200) {
            throw new RuntimeException("A descricao deve ter no maximo 200 caracteres.");
        }
        if (form.getData() == null) {
            throw new RuntimeException("Informe a data da despesa.");
        }
        MoedaBrasilUtil.parse(form.getValor());
    }
}
