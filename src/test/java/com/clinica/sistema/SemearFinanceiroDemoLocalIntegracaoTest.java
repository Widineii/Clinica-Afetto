package com.clinica.sistema;

import com.clinica.sistema.repository.UsuarioRepository;
import com.clinica.sistema.service.FinanceiroDemoSeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("local")
class SemearFinanceiroDemoLocalIntegracaoTest {

    @Autowired
    private FinanceiroDemoSeedService financeiroDemoSeedService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void semearPixDemonstracaoNoBancoLocal() {
        long profissionais = usuarioRepository.findByCargoOrderByNomeAsc("ROLE_PROFISSIONAL").stream()
                .filter(prof -> !Boolean.TRUE.equals(prof.getDonaClinica()))
                .count();
        int esperado = (int) profissionais * FinanceiroDemoSeedService.PAGAMENTOS_POR_PROFISSIONAL;

        int criados = financeiroDemoSeedService.semearPixDemonstracaoMesAtual();

        assertEquals(esperado, criados);
    }

    @Test
    void limparPixDemonstracaoNoBancoLocal() {
        financeiroDemoSeedService.limparPixDemonstracao();
    }
}
