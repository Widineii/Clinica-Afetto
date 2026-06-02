package com.clinica.sistema.service;

import com.clinica.sistema.dto.ProfissionalUsoSiteLinha;
import com.clinica.sistema.dto.RelatorioUsoSiteView;
import com.clinica.sistema.model.Usuario;
import com.clinica.sistema.repository.AgendamentoRepository;
import com.clinica.sistema.repository.UsuarioRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("local")
public class RelatorioUsoSiteService {

    private final UsuarioRepository usuarioRepository;
    private final AgendamentoRepository agendamentoRepository;

    public RelatorioUsoSiteService(
            UsuarioRepository usuarioRepository,
            AgendamentoRepository agendamentoRepository
    ) {
        this.usuarioRepository = usuarioRepository;
        this.agendamentoRepository = agendamentoRepository;
    }

    @Transactional(readOnly = true)
    public RelatorioUsoSiteView montarRelatorio() {
        List<Usuario> profissionais = usuarioRepository.findByCargoOrderByNomeAsc("ROLE_PROFISSIONAL");
        Map<Long, Long> agendamentosPorProfissional = carregarContagemAgendamentos();

        List<ProfissionalUsoSiteLinha> linhas = profissionais.stream()
                .map(profissional -> montarLinha(profissional, agendamentosPorProfissional))
                .toList();

        int totalJaAcessaram = (int) linhas.stream().filter(ProfissionalUsoSiteLinha::jaAcessouSite).count();
        int totalJaAgendaram = (int) linhas.stream().filter(ProfissionalUsoSiteLinha::jaAgendou).count();
        int total = linhas.size();

        return new RelatorioUsoSiteView(
                total,
                totalJaAcessaram,
                total - totalJaAcessaram,
                totalJaAgendaram,
                total - totalJaAgendaram,
                linhas
        );
    }

    private Map<Long, Long> carregarContagemAgendamentos() {
        Map<Long, Long> contagem = new HashMap<>();
        for (Object[] linha : agendamentoRepository.contarAgendamentosPorProfissional()) {
            Long profissionalId = (Long) linha[0];
            Long total = (Long) linha[1];
            contagem.put(profissionalId, total);
        }
        return contagem;
    }

    private ProfissionalUsoSiteLinha montarLinha(Usuario profissional, Map<Long, Long> agendamentosPorProfissional) {
        long totalAgendamentos = agendamentosPorProfissional.getOrDefault(profissional.getId(), 0L);
        boolean jaAcessou = profissional.getUltimoAcessoEm() != null;
        return new ProfissionalUsoSiteLinha(
                profissional.getId(),
                profissional.getNome(),
                profissional.getLogin(),
                Boolean.TRUE.equals(profissional.getDonaClinica()),
                profissional.getUltimoAcessoEm(),
                totalAgendamentos,
                jaAcessou,
                totalAgendamentos > 0
        );
    }
}
