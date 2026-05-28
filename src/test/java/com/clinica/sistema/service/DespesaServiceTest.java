package com.clinica.sistema.service;

import com.clinica.sistema.dto.DespesaForm;
import com.clinica.sistema.dto.DespesaResumoMesView;
import com.clinica.sistema.model.Despesa;
import com.clinica.sistema.model.TipoDespesa;
import com.clinica.sistema.repository.DespesaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DespesaServiceTest {

    @Mock
    private DespesaRepository repository;

    private DespesaService service;

    @BeforeEach
    void setUp() {
        service = new DespesaService(repository);
    }

    @Test
    void mensalDeveEntrarEmTodosOsMesesAposInicio() {
        Despesa aluguel = despesa(1L, TipoDespesa.MENSAL, "Aluguel", "1500,00", LocalDate.of(2026, 3, 1), null);
        when(repository.findAll()).thenReturn(List.of(aluguel));

        DespesaResumoMesView maio = service.montarResumoMes(YearMonth.of(2026, 5));
        DespesaResumoMesView fevereiro = service.montarResumoMes(YearMonth.of(2026, 2));

        assertEquals(1, maio.getDespesasMensais().size());
        assertEquals(new BigDecimal("1500.00"), maio.getTotalMensais());
        assertTrue(fevereiro.getDespesasMensais().isEmpty());
    }

    @Test
    void mensalEncerradaNaoDeveAparecerNoMesSeguinte() {
        Despesa aluguel = despesa(1L, TipoDespesa.MENSAL, "Aluguel", "1500,00", LocalDate.of(2026, 3, 1), "2026-05");
        when(repository.findAll()).thenReturn(List.of(aluguel));

        DespesaResumoMesView maio = service.montarResumoMes(YearMonth.of(2026, 5));
        DespesaResumoMesView junho = service.montarResumoMes(YearMonth.of(2026, 6));

        assertEquals(1, maio.getDespesasMensais().size());
        assertTrue(junho.getDespesasMensais().isEmpty());
    }

    @Test
    void unicaDeveEntrarSomenteNoMesDaData() {
        Despesa material = despesa(2L, TipoDespesa.UNICA, "Material", "350,00", LocalDate.of(2026, 5, 15), null);
        when(repository.findAll()).thenReturn(List.of(material));

        DespesaResumoMesView maio = service.montarResumoMes(YearMonth.of(2026, 5));
        DespesaResumoMesView junho = service.montarResumoMes(YearMonth.of(2026, 6));

        assertEquals(1, maio.getDespesasUnicas().size());
        assertEquals(new BigDecimal("350.00"), maio.getTotalUnicas());
        assertTrue(junho.getDespesasUnicas().isEmpty());
    }

    @Test
    void totalGeralDeveSomarMensaisEUnicas() {
        Despesa aluguel = despesa(1L, TipoDespesa.MENSAL, "Aluguel", "1000,00", LocalDate.of(2026, 1, 1), null);
        Despesa material = despesa(2L, TipoDespesa.UNICA, "Material", "250,00", LocalDate.of(2026, 5, 10), null);
        when(repository.findAll()).thenReturn(List.of(aluguel, material));

        DespesaResumoMesView maio = service.montarResumoMes(YearMonth.of(2026, 5));

        assertEquals(new BigDecimal("1250.00"), maio.getTotalGeral());
    }

    @Test
    void cadastrarDevePersistirDespesaComParserBrasileiro() {
        when(repository.save(any(Despesa.class))).thenAnswer(invocation -> {
            Despesa salva = invocation.getArgument(0);
            salva.setId(10L);
            return salva;
        });

        DespesaForm form = new DespesaForm();
        form.setTipo(TipoDespesa.MENSAL);
        form.setDescricao("Internet");
        form.setValor("89,90");
        form.setData(LocalDate.of(2026, 5, 1));

        service.cadastrar(form);

        ArgumentCaptor<Despesa> captor = ArgumentCaptor.forClass(Despesa.class);
        verify(repository).save(captor.capture());
        assertEquals(new BigDecimal("89.90"), captor.getValue().getValor());
    }

    @Test
    void encerrarMensalDeveGravarMesEncerrado() {
        Despesa despesa = despesa(1L, TipoDespesa.MENSAL, "Aluguel", "1500,00", LocalDate.of(2026, 3, 1), null);
        when(repository.findById(1L)).thenReturn(Optional.of(despesa));
        when(repository.save(any(Despesa.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.encerrarMensal(1L, YearMonth.of(2026, 5));

        assertEquals("2026-05", despesa.getMesEncerrado());
    }

    @Test
    void excluirUnicaDeveRemoverRegistro() {
        Despesa despesa = despesa(2L, TipoDespesa.UNICA, "Material", "350,00", LocalDate.of(2026, 5, 15), null);
        when(repository.findById(2L)).thenReturn(Optional.of(despesa));

        service.excluirUnica(2L, YearMonth.of(2026, 5));

        verify(repository).delete(despesa);
    }

    @Test
    void cadastrarSemDescricaoDeveFalhar() {
        DespesaForm form = new DespesaForm();
        form.setTipo(TipoDespesa.UNICA);
        form.setValor("10,00");
        form.setData(LocalDate.now());

        assertThrows(RuntimeException.class, () -> service.cadastrar(form));
    }

    private Despesa despesa(
            Long id,
            TipoDespesa tipo,
            String descricao,
            String valor,
            LocalDate dataReferencia,
            String mesEncerrado
    ) {
        Despesa despesa = new Despesa();
        despesa.setId(id);
        despesa.setTipo(tipo);
        despesa.setDescricao(descricao);
        despesa.setValor(com.clinica.sistema.util.MoedaBrasilUtil.parse(valor));
        despesa.setDataReferencia(dataReferencia);
        despesa.setMesEncerrado(mesEncerrado);
        return despesa;
    }
}
