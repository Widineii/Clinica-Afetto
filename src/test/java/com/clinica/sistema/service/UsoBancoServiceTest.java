package com.clinica.sistema.service;

import com.clinica.sistema.dto.UsoBancoView;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.RelatorioMensalArquivadoRepository;
import com.clinica.sistema.repository.SalaRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsoBancoServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;
    @Mock
    private RelatorioMensalArquivadoRepository relatorioMensalArquivadoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private SalaRepository salaRepository;

    @Mock
    private DataSource dataSource;

    private UsoBancoService usoBancoService;

    @BeforeEach
    void setUp() {
        usoBancoService = new UsoBancoService(
                agendamentoRepository,
                relatorioMensalArquivadoRepository,
                usuarioRepository,
                salaRepository,
                dataSource
        );
        ReflectionTestUtils.setField(usoBancoService, "limiteStorageMb", 512);
        ReflectionTestUtils.setField(usoBancoService, "painelProvedor", "Neon");
    }

    @Test
    void deveMontarResumoComEstimativa() throws Exception {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        DatabaseMetaData metaData = org.mockito.Mockito.mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");

        when(agendamentoRepository.buscarResumoContagensPainel(any(), any(), any(), any(), any()))
                .thenReturn(new Object[]{12_500L, 3_000L, 9_500L, 8_000L, 1_100L, 52L});
        when(relatorioMensalArquivadoRepository.count()).thenReturn(8L);
        when(relatorioMensalArquivadoRepository.countComPdfLegado()).thenReturn(0L);
        when(relatorioMensalArquivadoRepository.somaBytesJson()).thenReturn(120_000L);
        when(usuarioRepository.count()).thenReturn(21L);
        when(usuarioRepository.countByCargo("ROLE_PROFISSIONAL")).thenReturn(20L);
        when(salaRepository.count()).thenReturn(4L);

        UsoBancoView resumo = usoBancoService.montarResumo();

        assertEquals(12_500L, resumo.getTotalAgendamentos());
        assertEquals(20L, resumo.getTotalProfissionais());
        assertEquals(52L, resumo.getAgendamentosHoje());
        assertFalse(resumo.isPostgresComTamanhoReal());
        assertEquals("ok", resumo.getNivelAlerta());
        assertEquals("117,2 KB", resumo.getBytesJsonLabel());
    }

    @Test
    void deveFormatarBytes() {
        assertEquals("512 B", UsoBancoService.formatarBytes(512));
        assertEquals("1,5 KB", UsoBancoService.formatarBytes(1536));
        assertEquals("2,00 MB", UsoBancoService.formatarBytes(2_097_152));
        assertEquals("70 GB", UsoBancoService.formatarLimitePlano(71680));
        assertEquals("512 MB", UsoBancoService.formatarLimitePlano(512));
    }
}
