package com.clinica.sistema.config;

import com.clinica.sistema.model.NovidadePublicoAlvo;
import com.clinica.sistema.model.NovidadeSite;
import com.clinica.sistema.repository.NovidadeSiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(50)
public class NovidadeSiteInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NovidadeSiteInitializer.class);

    private final NovidadeSiteRepository novidadeSiteRepository;

    public NovidadeSiteInitializer(NovidadeSiteRepository novidadeSiteRepository) {
        this.novidadeSiteRepository = novidadeSiteRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<NovidadeSeed> seeds = List.of(
                new NovidadeSeed(
                        "v2.827-email-recuperacao-senha",
                        "2.827",
                        "Esqueci minha senha por e-mail ativo",
                        "Informe login e e-mail na tela de acesso para receber um codigo e redefinir a senha.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        827,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.826-recuperacao-senha",
                        "2.826",
                        "Esqueci minha senha por e-mail",
                        "Na tela de login, informe login e e-mail para receber um codigo e redefinir a senha.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        826,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.825-forma-pagamento-agenda",
                        "2.825",
                        "Forma de pagamento na agenda",
                        "Profissionais veem o card Diário, Semanal ou Mensal logo abaixo do menu, sem precisar abrir Meus pagamentos.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        825,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.824-central-corrigida",
                        "2.824",
                        "Central dos profissionais corrigida",
                        "Correção ao abrir a Central (admin/Polyana): a tela de mensagens WhatsApp não quebra mais por tabela ausente no banco de produção.",
                        NovidadePublicoAlvo.DONA_CLINICA,
                        824,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.823-realocacao-retroativa",
                        "2.823",
                        "Realocação retroativa corrigida",
                        "A gestão volta a poder realocar consultas passadas recentes sem bloqueio indevido do status de pagamento.",
                        NovidadePublicoAlvo.DONA_CLINICA,
                        823,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.823-agenda-estabilidade",
                        "2.823",
                        "Agenda mais estável",
                        "Correções na grade semanal, no caderno mensal e nos conflitos de sala e horário para evitar erro ao abrir ou salvar agendamentos.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        822,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.813-tema-claro-escuro-sistema",
                        "2.813",
                        "Tema Claro, Escuro e Sistema",
                        "Todos os usuários podem escolher o tema no menu do perfil e na tela de login. "
                                + "A opção Sistema segue o tom do aparelho (claro ou escuro).",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        813,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.813-login-escuro-legivel",
                        "2.813",
                        "Login no tema escuro",
                        "No modo escuro, a tela de login mantém o mesmo fundo e painel do claro, "
                                + "com letras mais escuras e campos legíveis.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        812,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.813-menu-ajuda-whatsapp",
                        "2.813",
                        "Menu Ajuda atualizado",
                        "No menu do perfil: Regras da clínica, WhatsApp da recepção (31) 8283-5857 "
                                + "e Falar com suporte técnico.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        811,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.813-contraste-escuro",
                        "2.813",
                        "Contraste no tema escuro",
                        "Melhor leitura em Central, Financeiro, Agenda, tabelas, cards de agendamento, "
                                + "modal Editar perfil e badges coloridos.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        810,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.757-taxas-sala-central",
                        "2.757",
                        "Taxas de sala na Central",
                        "Nova aba Taxas de sala: defina avulso, semanal, quinzenal e mensal por profissional ou para todos. "
                                + "Atualiza Clín. nas consultas pendentes; pagas não mudam. Manual com seção exclusiva da Polyana.",
                        NovidadePublicoAlvo.DONA_CLINICA,
                        485,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.757-valores-alterar-prof",
                        "2.757",
                        "Valores da consulta e Alterar valor",
                        "No agendamento: Prof. recebe (você informa), Clínica cobra (taxa da Central, somente leitura) e Líq. calculado. "
                                + "Em Meus agendamentos, Alterar valor muda o Prof. nas datas pendentes de séries fixa, quinzenal ou mensal. "
                                + "Manual atualizado com seção só para profissionais.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        484,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.6-login-renovado",
                        "2.6",
                        "Nova tela de login",
                        "Login em tela cheia com fundo teal, blocos centralizados e area de acesso renovada para entrar mais rapido na agenda.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        482,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.6-login-renovado-dona",
                        "2.6",
                        "Nova tela de login",
                        "Tela de acesso com visual da clinica, layout centralizado e lembrar acesso no mesmo padrao dos profissionais.",
                        NovidadePublicoAlvo.DONA_CLINICA,
                        482,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.481-novidades-aba",
                        "2.481",
                        "Aba Novidades",
                        "Ao entrar na agenda, confira o que mudou no sistema. Marque \"Não mostrar de novo\" ou aguarde 3 dias para sumir sozinho.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        481,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.481-novidades-polyana",
                        "2.481",
                        "Painel de novidades",
                        "Você vê todas as atualizações — as dos profissionais e as da gestão da clínica.",
                        NovidadePublicoAlvo.DONA_CLINICA,
                        481,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.480-aviso-cancelamento",
                        "2.480",
                        "Aviso de cancelamento mais claro",
                        "No formulário de Novo agendamento, as regras de cancelamento ficaram em tópicos separados: série fixa/quinzenal, avulso e horário já pago.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        480,
                        diasAtras(0)
                ),
                new NovidadeSeed(
                        "v2.479-manual-atualizado",
                        "2.479",
                        "Manual de uso atualizado",
                        "O manual ganhou seções sobre Meu relatório, forma de pagamento, agenda do dia e cancelamento de datas com a regra de 24 horas.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        479,
                        diasAtras(1)
                ),
                new NovidadeSeed(
                        "v2.478-cancelamento-serie",
                        "2.478",
                        "Cancelar data da série",
                        "Profissionais podem cancelar uma data de série fixa ou quinzenal em Meus agendamentos, com mais de 24 horas de antecedência e sem pagamento na data. Avulso não cancela pelo app.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        478,
                        diasAtras(2)
                ),
                new NovidadeSeed(
                        "v2.477-meu-relatorio",
                        "2.477",
                        "Meu relatório",
                        "Novo botão Meu relatório: resumo mensal dos seus atendimentos e taxas PIX pagas, com filtro por sala.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        477,
                        diasAtras(3)
                ),
                new NovidadeSeed(
                        "v2.476-forma-pagamento",
                        "2.476",
                        "Escolher forma de pagamento",
                        "No topo da agenda você escolhe Diário, Semanal ou Mensal. Depois de trocar, aguarde 24 horas para mudar de novo.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        476,
                        diasAtras(4)
                ),
                new NovidadeSeed(
                        "v2.475-agenda-do-dia",
                        "2.475",
                        "Agenda do dia",
                        "A seção Agenda do dia mostra só os seus atendimentos de hoje, em todas as salas.",
                        NovidadePublicoAlvo.PROFISSIONAL,
                        475,
                        diasAtras(5)
                ),
                new NovidadeSeed(
                        "v2.474-central-polyana",
                        "2.474",
                        "Central dos profissionais",
                        "A Central dos profissionais ficou exclusiva da Polyana: cadastro, senhas e forma de pagamento inicial de cada profissional.",
                        NovidadePublicoAlvo.DONA_CLINICA,
                        474,
                        diasAtras(2)
                ),
                new NovidadeSeed(
                        "v2.473-grade-gestao",
                        "2.473",
                        "Cancelamento na grade",
                        "Na grade semanal, dois cliques em um horário permitem cancelar data ou encerrar série de qualquer profissional.",
                        NovidadePublicoAlvo.DONA_CLINICA,
                        473,
                        diasAtras(3)
                )
        );

        int inseridas = 0;
        for (NovidadeSeed seed : seeds) {
            if (novidadeSiteRepository.findByCodigo(seed.codigo()).isPresent()) {
                continue;
            }
            NovidadeSite novidade = new NovidadeSite();
            novidade.setCodigo(seed.codigo());
            novidade.setVersao(seed.versao());
            novidade.setTitulo(seed.titulo());
            novidade.setDescricao(seed.descricao());
            novidade.setPublicoAlvo(seed.publicoAlvo());
            novidade.setPublicadaEm(seed.publicadaEm());
            novidade.setOrdemExibicao(seed.ordem());
            novidadeSiteRepository.save(novidade);
            inseridas++;
        }
        if (inseridas > 0) {
            log.info("Novidades do site: {} registro(s) inicial(is) inserido(s).", inseridas);
        }
    }

    private static LocalDateTime diasAtras(int dias) {
        return LocalDateTime.now().minusDays(dias);
    }

    private record NovidadeSeed(
            String codigo,
            String versao,
            String titulo,
            String descricao,
            NovidadePublicoAlvo publicoAlvo,
            int ordem,
            LocalDateTime publicadaEm
    ) {
    }
}
