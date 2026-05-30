package com.clinica.sistema.service;

import com.clinica.sistema.dto.EncerramentoSerieNotificacaoView;
import com.clinica.sistema.model.EncerramentoSerieRegistro;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.EncerramentoSerieRegistroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncerramentoSerieNotificacaoServiceTest {

    @Mock
    private EncerramentoSerieRegistroRepository encerramentoSerieRegistroRepository;

    private EncerramentoSerieNotificacaoService service;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        service = new EncerramentoSerieNotificacaoService(encerramentoSerieRegistroRepository);
        session = new MockHttpSession();
    }

    @Test
    void notificacaoOcultaQuandoNaoHaEncerramentos() {
        when(encerramentoSerieRegistroRepository.findFirstByOrderByEncerradoEmDescIdDesc())
                .thenReturn(Optional.empty());

        assertTrue(service.avaliarNotificacao(session).isEmpty());
    }

    @Test
    void notificacaoApareceParaEncerramentoNovo() {
        EncerramentoSerieRegistro registro = registro(5L, "Julia", "Maria Silva", "SEMANAL");
        when(encerramentoSerieRegistroRepository.findFirstByOrderByEncerradoEmDescIdDesc())
                .thenReturn(Optional.of(registro));
        when(encerramentoSerieRegistroRepository.countByIdGreaterThan(0L)).thenReturn(1L);

        Optional<EncerramentoSerieNotificacaoView> notificacao = service.avaliarNotificacao(session);

        assertTrue(notificacao.isPresent());
        assertEquals("Julia encerrou uma série semanal", notificacao.get().getMensagemResumo());
        assertTrue(notificacao.get().getMensagemPainel().contains("Maria Silva"));
    }

    @Test
    void marcarComoVistoRemoveNotificacao() {
        EncerramentoSerieRegistro registro = registro(8L, "Carol", "João", "QUINZENAL");
        when(encerramentoSerieRegistroRepository.findFirstByOrderByEncerradoEmDescIdDesc())
                .thenReturn(Optional.of(registro));
        when(encerramentoSerieRegistroRepository.countByIdGreaterThan(0L)).thenReturn(2L);
        when(encerramentoSerieRegistroRepository.countByIdGreaterThan(8L)).thenReturn(0L);

        assertTrue(service.deveExibirBolinha(session));

        service.marcarComoVisto(session);

        assertEquals(8L, service.obterUltimoVistoId(session));
        assertFalse(service.deveExibirBolinha(session));
    }

    private EncerramentoSerieRegistro registro(
            Long id,
            String encerradoPorNome,
            String cliente,
            String tipoRecorrencia
    ) {
        Usuario encerradoPor = new Usuario();
        encerradoPor.setNome(encerradoPorNome);

        EncerramentoSerieRegistro registro = new EncerramentoSerieRegistro();
        registro.setId(id);
        registro.setNomeCliente(cliente);
        registro.setTipoRecorrencia(tipoRecorrencia);
        registro.setEncerradoPor(encerradoPor);
        registro.setEncerradoEm(LocalDateTime.now());
        registro.setQuantidadeHorarios(4);
        return registro;
    }
}
