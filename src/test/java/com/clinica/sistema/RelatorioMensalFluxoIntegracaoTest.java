package com.clinica.sistema;

import com.clinica.sistema.dto.AgendamentoForm;
import com.clinica.sistema.dto.RelatorioMensalUsoSalasView;
import com.lowagie.text.pdf.PdfReader;
import com.clinica.sistema.dto.RelatorioUsoSalaProfissional;
import com.clinica.sistema.model.RelatorioMensalArquivado;
import com.clinica.sistema.model.Sala;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.RelatorioMensalArquivadoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.service.AgendamentoService;
import com.clinica.sistema.service.RelatorioMensalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.DayOfWeek;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class RelatorioMensalFluxoIntegracaoTest {

    @Autowired
    private AgendamentoService agendamentoService;

    @Autowired
    private RelatorioMensalService relatorioMensalService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SalaRepository salaRepository;

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository;

    @BeforeEach
    void limparDadosDeTeste() {
        agendamentoRepository.deleteAll();
        relatorioMensalArquivadoRepository.deleteAll();
    }

    @Test
    void deveCriar15AgendamentosEGerarRelatorioDoMesPassado() throws Exception {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);

        Usuario admin = usuarioRepository.findByLogin("admin").orElseThrow();
        List<Usuario> profissionais = List.of(
                buscarProfissional("julia"),
                buscarProfissional("carol"),
                buscarProfissional("polyana")
        );
        List<Sala> salas = salaRepository.findAllByOrderByNomeAsc();
        assertFalse(salas.isEmpty());

        List<LocalDate> diasUteis = diasUteisDoMes(mesPassado);
        assertTrue(diasUteis.size() >= 5, "Mes passado precisa ter dias uteis para o teste.");

        int criados = 0;
        for (int i = 0; i < 15; i++) {
            Usuario profissional = profissionais.get(i % profissionais.size());
            Sala sala = salas.get(i % salas.size());
            LocalDate dia = diasUteis.get(i % diasUteis.size());
            LocalTime horario = LocalTime.of(7 + (i % 10), 0);

            AgendamentoForm form = new AgendamentoForm();
            form.setProfissionalId(profissional.getId());
            form.setSalaId(sala.getId());
            form.setNomeCliente("Cliente teste " + (i + 1));
            form.setDataAtendimento(dia);
            form.setHorarioAtendimento(horario);
            form.setRecorrencia("AVULSO");
            form.setFixo(false);
            form.setValorProfissionalRecebe(new BigDecimal("150.00"));
            form.setValorClinicaCobra(new BigDecimal("35.00"));

            agendamentoService.salvar(form, admin);
            criados++;
        }

        long noBancoAntes = contarAgendamentosNoMes(mesPassado);
        assertEquals(15, criados);
        assertEquals(15, noBancoAntes);

        RelatorioMensalUsoSalasView relatorioAntes = agendamentoService.montarRelatorioMensalUsoSalas(mesPassado);
        assertEquals(15, relatorioAntes.getTotalGeral());
        imprimirRelatorio("ANTES DE ARQUIVAR", relatorioAntes);

        Optional<RelatorioMensalArquivado> arquivado = relatorioMensalService.arquivarMesSeNecessario(mesPassado);
        assertTrue(arquivado.isPresent());
        assertNull(arquivado.get().getPdf(), "PDF nao deve ser gravado no banco");
        assertNotNull(arquivado.get().getDadosJson());
        assertFalse(arquivado.get().getDadosJson().isBlank());
        assertEquals(15, arquivado.get().getAgendamentosRemovidos());

        long noBancoDepois = contarAgendamentosNoMes(mesPassado);
        assertEquals(0, noBancoDepois);

        RelatorioMensalUsoSalasView relatorioDepois = relatorioMensalService.carregarRelatorioParaExibicao(mesPassado);
        assertEquals(15, relatorioDepois.getTotalGeral());
        imprimirRelatorio("DEPOIS DE ARQUIVAR (salvo no sistema)", relatorioDepois);

        byte[] pdfGerado = relatorioMensalService.gerarPdfDoArquivado(arquivado.get());
        assertTrue(pdfGerado.length > 500);

        System.out.println();
        System.out.println("PDF gerado: " + relatorioMensalService.nomeArquivoPdf(mesPassado));
        System.out.println("Tamanho do PDF: " + pdfGerado.length + " bytes");
        PdfReader leitorPdf = new PdfReader(pdfGerado);
        int paginas = leitorPdf.getNumberOfPages();
        leitorPdf.close();
        System.out.println("Paginas no PDF: " + paginas);
        assertTrue(paginas <= 2, "PDF deve ter no maximo 2 paginas, mas tem " + paginas);
        System.out.println("Teste OK: 15 agendamentos criados, relatorio gerado e mes passado limpo do banco.");
    }

    @Test
    void deveManterSemanalEQuinzenalAoLimparMesPassado() {
        YearMonth mesPassado = YearMonth.now().minusMonths(1);

        Usuario admin = usuarioRepository.findByLogin("admin").orElseThrow();
        Usuario julia = buscarProfissional("julia");
        List<Sala> salas = salaRepository.findAllByOrderByNomeAsc();
        LocalDate dia = diasUteisDoMes(mesPassado).get(0);

        AgendamentoForm avulso = new AgendamentoForm();
        avulso.setProfissionalId(julia.getId());
        avulso.setSalaId(salas.get(0).getId());
        avulso.setNomeCliente("Cliente avulso");
        avulso.setDataAtendimento(dia);
        avulso.setHorarioAtendimento(LocalTime.of(7, 0));
        avulso.setRecorrencia("AVULSO");
        avulso.setValorProfissionalRecebe(new BigDecimal("150.00"));
        avulso.setValorClinicaCobra(new BigDecimal("35.00"));
        agendamentoService.salvar(avulso, admin);

        AgendamentoForm semanal = new AgendamentoForm();
        semanal.setProfissionalId(julia.getId());
        semanal.setSalaId(salas.get(1).getId());
        semanal.setNomeCliente("Cliente semanal");
        semanal.setDataAtendimento(dia);
        semanal.setHorarioAtendimento(LocalTime.of(9, 0));
        semanal.setRecorrencia("SEMANAL");
        semanal.setValorProfissionalRecebe(new BigDecimal("150.00"));
        semanal.setValorClinicaCobra(new BigDecimal("35.00"));
        agendamentoService.salvar(semanal, admin);

        LocalDateTime inicioMes = mesPassado.atDay(1).atStartOfDay();
        LocalDateTime fimMes = mesPassado.plusMonths(1).atDay(1).atStartOfDay();

        long avulsosAntes = contarAvulsosNoMes(inicioMes, fimMes);
        long serieAntes = contarSerieNoMes(inicioMes, fimMes);
        assertEquals(1, avulsosAntes);
        assertTrue(serieAntes >= 1);

        relatorioMensalService.arquivarMesSeNecessario(mesPassado);

        assertEquals(0, contarAvulsosNoMes(inicioMes, fimMes));
        assertTrue(contarSerieNoMes(inicioMes, fimMes) >= 1);
    }

    private long contarAvulsosNoMes(LocalDateTime inicio, LocalDateTime fim) {
        return agendamentoRepository.findByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(inicio, fim)
                .stream()
                .filter(ag -> !Boolean.TRUE.equals(ag.getFixo())
                        && !"SEMANAL".equalsIgnoreCase(ag.getTipoRecorrencia())
                        && !"QUINZENAL".equalsIgnoreCase(ag.getTipoRecorrencia()))
                .count();
    }

    private long contarSerieNoMes(LocalDateTime inicio, LocalDateTime fim) {
        return agendamentoRepository.findByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(inicio, fim)
                .stream()
                .filter(ag -> Boolean.TRUE.equals(ag.getFixo())
                        || "SEMANAL".equalsIgnoreCase(ag.getTipoRecorrencia())
                        || "QUINZENAL".equalsIgnoreCase(ag.getTipoRecorrencia()))
                .count();
    }

    private Usuario buscarProfissional(String login) {
        return usuarioRepository.findByLogin(login).orElseThrow();
    }

    private List<LocalDate> diasUteisDoMes(YearMonth mes) {
        List<LocalDate> dias = new ArrayList<>();
        for (LocalDate dia = mes.atDay(1); !dia.isAfter(mes.atEndOfMonth()); dia = dia.plusDays(1)) {
            if (dia.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dias.add(dia);
            }
        }
        return dias;
    }

    private long contarAgendamentosNoMes(YearMonth mes) {
        LocalDateTime inicio = mes.atDay(1).atStartOfDay();
        LocalDateTime fim = mes.plusMonths(1).atDay(1).atStartOfDay();
        return agendamentoRepository.findByDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                inicio,
                fim
        ).size();
    }

    private void imprimirRelatorio(String titulo, RelatorioMensalUsoSalasView relatorio) {
        System.out.println();
        System.out.println("=== " + titulo + " ===");
        System.out.println("Periodo: " + relatorio.getMesReferenciaLabel());
        System.out.println("Total: " + relatorio.getTotalGeral() + " horario(s)");
        for (RelatorioUsoSalaProfissional prof : relatorio.getProfissionais()) {
            System.out.println(prof.getProfissionalNome());
            prof.getSalas().forEach(sala ->
                    System.out.println("  " + sala.getSalaNome() + ": " + sala.getQuantidade() + " vez(es)")
            );
        }
    }
}
