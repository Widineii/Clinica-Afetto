package com.clinica.sistema.tools;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Gera o PDF do contrato de licenciamento da Agenda Afetto.
 * Executar: mvnw.cmd -q -DskipTests compile exec:java -Dexec.mainClass=com.clinica.sistema.tools.GeradorContratoPdf
 */
public final class GeradorContratoPdf {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(10, 135, 133));
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);

    private GeradorContratoPdf() {
    }

    public static void main(String[] args) throws DocumentException, IOException {
        Path destino = Paths.get("docs", "contrato-venda-agenda-afetto.pdf");
        gerar(destino);
        System.out.println("PDF gerado: " + destino.toAbsolutePath());
    }

    static void gerar(Path destino) throws DocumentException, IOException {
        destino.getParent().toFile().mkdirs();
        try (var out = java.nio.file.Files.newOutputStream(destino)) {
            gerar(out);
        }
    }

    public static void gerar(java.io.OutputStream out) throws DocumentException, IOException {
        Document doc = new Document(PageSize.A4, 50, 50, 45, 45);
        PdfWriter.getInstance(doc, out);
        doc.open();
        escreverContrato(doc);
        doc.close();
    }

    private static void escreverContrato(Document doc) throws DocumentException {
        Paragraph titulo = new Paragraph("CONTRATO DE LICENCIAMENTO DE SOFTWARE\nE PRESTAÇÃO DE SERVIÇOS DE SUPORTE", TITLE);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(4);
        doc.add(titulo);

        Paragraph sub = new Paragraph("Sistema: Agenda Afetto — Plataforma Web de Gestão Clínica\nVersão entregue: 2.6 | Documento: junho/2026", SUBTITLE);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(14);
        doc.add(sub);

        doc.add(corpo(
                "Pelo presente instrumento particular, de um lado WIDINEI MARTINS DE OLIVEIRA, "
                        + "brasileiro, inscrito no CPF sob nº 147.786.936-04, RG nº MG-24.207.266, "
                        + "residente e domiciliado em Rua Francisco Castro Monteiro, nº 94, telefone/WhatsApp "
                        + "(37) 99855-0994, e-mail widineimartins4@gmail.com, doravante denominado CONTRATADO ou DESENVOLVEDOR; "
                        + "e, de outro lado, _______________________________________________, inscrito(a) no CPF/CNPJ sob nº "
                        + "_______________________, com endereço em _______________________________________________, "
                        + "representado(a) neste ato por _______________________________________________, doravante denominado(a) "
                        + "CONTRATANTE ou CLÍNICA, têm entre si justo e contratado o seguinte:"));

        doc.add(corpo("Histórico de uso e validação. As partes reconhecem que, antes da formalização deste contrato, "
                + "a CONTRATANTE utilizou o sistema Agenda Afetto por aproximadamente 2 (dois) meses em ambiente "
                + "operacional real, para testes, monitoramento do uso diário e adequações ao fluxo de trabalho da clínica, "
                + "incluindo ajustes e alterações solicitadas pela CONTRATANTE conforme a rotina dos profissionais e da "
                + "administração. Esse período serviu como validação prática do sistema entregue."));

        doc.add(secao("CLÁUSULA 1 — DO OBJETO"));
        doc.add(corpo("1.1. O presente contrato tem por objeto a licença de uso exclusiva do software web denominado "
                + "\"Agenda Afetto\", bem como a prestação de serviços de implantação, entrega técnica e suporte pelo "
                + "prazo de 3 (três) meses, contados a partir da data de assinatura deste instrumento ou do pagamento "
                + "integral, o que ocorrer por último."));
        doc.add(corpo("1.2. O software é uma aplicação web desenvolvida em Java (Spring Boot), com interface em "
                + "Thymeleaf/HTML, destinada à gestão operacional, financeira e administrativa de clínica de saúde, "
                + "incluindo controle de agenda por profissional, sala e paciente."));
        doc.add(corpo("1.3. A CONTRATANTE declara ter acompanhado o uso do sistema durante o período de aproximadamente "
                + "2 (dois) meses descrito acima, tendo tido oportunidade de testar as funcionalidades, solicitar ajustes "
                + "e verificar o funcionamento no dia a dia da clínica. Com a assinatura deste instrumento e o pagamento "
                + "acordado, considera-se o sistema aceito para fins de entrega, ressalvados apenas defeitos (bugs) "
                + "corrigíveis no prazo de suporte da Cláusula 6."));
        doc.add(corpo("1.4. Limitação de finalidade: o Agenda Afetto destina-se à gestão de agenda, financeiro e "
                + "administrativo da clínica. O sistema não substitui prontuário eletrônico, registro clínico completo "
                + "do paciente nem qualquer obrigação técnica, ética ou legal dos profissionais de saúde. A responsabilidade "
                + "pelo atendimento, diagnóstico, conduta clínica e documentação médica/psicológica permanece exclusivamente "
                + "com os profissionais e com a CONTRATANTE."));

        doc.add(secao("CLÁUSULA 2 — DESCRIÇÃO DETALHADA DO SISTEMA ENTREGUE"));
        doc.add(corpo("O CONTRATADO entrega licença de uso do sistema com os módulos e funcionalidades abaixo descritos, "
                + "na versão vigente à data da assinatura:"));

        doc.add(modulo("2.1. Módulo de Autenticação e Acesso",
                "Tela de login personalizada com identidade visual da clínica;",
                "Controle de acesso por perfil: Administrador e Profissional;",
                "Usuários individuais por profissional da equipe;",
                "Opção \"lembrar-me\" e sessão segura;",
                "Proteção CSRF e tratamento de erros de acesso."));

        doc.add(modulo("2.2. Módulo de Agenda",
                "Agendamento de consultas com data, horário, profissional, sala e paciente;",
                "Visualização em calendário/agenda operacional;",
                "Edição, cancelamento e realocação de agendamentos;",
                "Controle de séries recorrentes de atendimento;",
                "Registro de status de atendimento e pagamento vinculado ao agendamento;",
                "Caderno de agendamentos e filtros por profissional/período."));

        doc.add(modulo("2.3. Módulo Financeiro",
                "Painel financeiro com receitas e despesas;",
                "Controle de status de pagamento (pago, pendente, aguardando confirmação, indicação etc.);",
                "Registro de despesas por tipo;",
                "Integração com pagamentos via PIX;",
                "Integração com gateway InfinitePay (checkout, webhook e confirmação);",
                "Pagamentos por consulta, por semana e em lote;",
                "Prazos e regras automáticas de confirmação e expiração de pagamento;",
                "Área \"Meus pagamentos\" para acompanhamento."));

        doc.add(modulo("2.4. Módulo de Indicações e Reservas",
                "Fluxo de indicação/reserva de pacientes entre profissionais;",
                "Painel de indicações pendentes para aprovação da administração;",
                "Regras de prazo e status específicos para indicações."));

        doc.add(modulo("2.5. Módulo de Relatórios",
                "Relatório mensal consolidado da clínica;",
                "Relatório semanal;",
                "Relatório individual por profissional (\"Meu relatório\");",
                "Arquivamento automático de relatórios mensais em PDF;",
                "Rotinas automáticas de fechamento e geração conforme calendário configurado."));

        doc.add(modulo("2.6. Central de Profissionais (Administrador)",
                "Visão centralizada de agendamentos de todos os profissionais;",
                "Ferramentas administrativas para gestão da operação clínica;",
                "Painel de uso do banco de dados (admin)."));

        doc.add(modulo("2.7. Notificações",
                "Sino de notificações no sistema;",
                "Alertas de novos agendamentos para perfil administrativo;",
                "Notificações relacionadas a relatório mensal;",
                "Interface de popup com layout responsivo."));

        doc.add(modulo("2.8. Manual, Ajuda e Novidades",
                "Manual de uso integrado ao sistema;",
                "Seção de ajuda e suporte com link para WhatsApp configurável;",
                "Sistema de novidades do site para comunicação de atualizações aos usuários."));

        doc.add(modulo("2.9. Infraestrutura e Tecnologia",
                "Aplicação web em produção em VPS Linux na KingHost, com banco PostgreSQL no mesmo servidor (Docker), conforme Cláusula 3;",
                "Ambiente de desenvolvimento local (perfil local com H2);",
                "Código-fonte completo do projeto entregue via repositório Git ou arquivo compactado;",
                "Documentação básica de execução (README) e variáveis de ambiente necessárias."));

        adicionarClausulaInfraestrutura(doc);

        doc.add(secao("CLÁUSULA 4 — DO PREÇO E FORMA DE PAGAMENTO"));
        doc.add(tabelaPrecos());
        doc.add(corpo("4.2. Forma de pagamento: ( ) À vista   ( ) Parcelado em _____ vezes   | Meio: ( ) PIX   ( ) Transferência   ( ) Outro: __________"));
        doc.add(corpo("4.3. Chave PIX / dados bancários do CONTRATADO: conferir com o CONTRATADO — widineimartins8@gmail.com"));
        doc.add(corpo("4.4. A entrega definitiva do acesso, credenciais e código-fonte ocorrerá após a confirmação do pagamento integral ou do primeiro pagamento acordado, conforme combinado entre as partes."));
        doc.add(corpo("4.5. Em caso de atraso no pagamento superior a 15 (quinze) dias do vencimento acordado, o CONTRATADO poderá "
                + "suspender o suporte técnico e o acesso técnico até a regularização dos valores em aberto."));

        doc.add(secao("CLÁUSULA 5 — DA LICENÇA E PROPRIEDADE INTELECTUAL"));
        doc.add(corpo("5.1. O CONTRATADO concede à CONTRATANTE licença de uso exclusiva e perpétua para a operação desta clínica "
                + "do sistema Agenda Afetto, incluindo direito de hospedar, utilizar e manter o software em produção."));
        doc.add(corpo("5.2. Com o pagamento integral, a CONTRATANTE recebe o código-fonte da versão entregue e o direito de uso "
                + "exclusivo na operação da clínica contratante. O CONTRATADO não revenderá a mesma customização específica desta "
                + "clínica a concorrentes diretos. Permanece facultado reutilizar componentes genéricos, bibliotecas e know-how em "
                + "outros projetos, sem reproduzir a customização exclusiva desta CONTRATANTE."));
        doc.add(corpo("5.3. Bibliotecas de terceiros, frameworks open source e serviços externos (InfinitePay, KingHost, PostgreSQL etc.) permanecem regidos por suas respectivas licenças e termos de uso."));
        doc.add(corpo("5.4. Marcas, logotipos e identidade visual da clínica pertencem à CONTRATANTE. A marca \"Agenda Afetto\" e componentes genéricos reutilizáveis permanecem de titularidade do CONTRATADO, salvo renúncia expressa."));
        doc.add(corpo("5.5. A CONTRATANTE autoriza o CONTRATADO a utilizar o projeto Agenda Afetto em seu portfólio profissional "
                + "(site pessoal, LinkedIn, GitHub, currículo, propostas e apresentações), mencionando o desenvolvimento do sistema "
                + "e, se necessário, o nome da clínica como referência. Não é permitido divulgar dados de pacientes, dados financeiros, "
                + "credenciais ou capturas com informações sensíveis; imagens devem ser genéricas, mascaradas ou aprovadas pela CONTRATANTE."));

        doc.add(secao("CLÁUSULA 6 — DO SUPORTE TÉCNICO (3 MESES)"));
        doc.add(corpo("6.1. O CONTRATADO prestará suporte técnico por 90 (noventa) dias corridos, incluindo:"));
        doc.add(bullet("Correção de bugs e erros de funcionamento do sistema entregue;"));
        doc.add(bullet("Orientação sobre uso das funcionalidades existentes;"));
        doc.add(bullet("Ajustes menores de configuração (usuários, variáveis de ambiente, deploy);"));
        doc.add(bullet("Atendimento via WhatsApp e/ou e-mail em horário comercial (segunda a sexta, 9h às 18h)."));
        doc.add(corpo("6.2. Não estão incluídos no suporte (salvo orçamento à parte):"));
        doc.add(bullet("Desenvolvimento de novas funcionalidades ou módulos;"));
        doc.add(bullet("Integrações novas com WhatsApp, Google Agenda, outros gateways de pagamento, sistemas externos, automações ou APIs de terceiros;"));
        doc.add(bullet("Criação de novas telas, relatórios, regras de negócio ou mudanças estruturais no funcionamento do sistema;"));
        doc.add(bullet("Redesign completo de telas;"));
        doc.add(bullet("Migração para outro servidor/provedor não acordado;"));
        doc.add(bullet("Custos de hospedagem VPS (KingHost), domínio e taxas de gateway de pagamento;"));
        doc.add(bullet("Treinamento presencial ou visitas à clínica;"));
        doc.add(bullet("Problemas causados por mau uso, alteração não autorizada do código por terceiros ou indisponibilidade de serviços externos."));
        doc.add(corpo("6.3. O suporte técnico de 3 (três) meses compreende exclusivamente correção de erros, "
                + "orientação de uso e ajustes pontuais relacionados às funcionalidades já entregues. Novas funcionalidades, "
                + "integrações, automações, alterações estruturais ou mudanças de regra de negócio serão objeto de orçamento separado."));
        doc.add(corpo("6.4. Prazo de resposta: até 48 (quarenta e oito) horas úteis para demandas normais; urgências operacionais (sistema fora do ar) com prioridade em até 24 horas úteis."));
        doc.add(corpo("6.5. Após o período de 3 meses, o suporte não será prestado automaticamente. Correções, ajustes, "
                + "integrações ou novas funcionalidades dependerão de contratação à parte, com orçamento prévio."));

        doc.add(secao("CLÁUSULA 7 — DAS OBRIGAÇÕES DO CONTRATADO"));
        doc.add(bullet("Entregar o sistema funcional conforme descrito na Cláusula 2;"));
        doc.add(bullet("Fornecer código-fonte, instruções de deploy e credenciais acordadas;"));
        doc.add(bullet("Prestar suporte pelo prazo de 3 meses;"));
        doc.add(bullet("Manter sigilo sobre dados da clínica e pacientes a que tiver acesso;"));
        doc.add(bullet("Informar limitações conhecidas do sistema no momento da entrega."));

        doc.add(secao("CLÁUSULA 8 — DAS OBRIGAÇÕES DA CONTRATANTE"));
        doc.add(bullet("Efetuar o pagamento nos prazos acordados;"));
        doc.add(bullet("Fornecer dados, logotipos e informações necessários à operação;"));
        doc.add(bullet("Manter backup de dados e credenciais, sendo responsável por cópias de segurança no VPS KingHost (Cláusula 3.1.4);"));
        doc.add(bullet("Contratar e pagar hospedagem VPS (KingHost), domínio e serviços de pagamento (InfinitePay etc.), conforme Cláusula 3;"));
        doc.add(bullet("Utilizar o sistema em conformidade com a LGPD e demais normas aplicáveis à área da saúde;"));
        doc.add(bullet("Não sublicenciar, revender ou distribuir o sistema a terceiros sem autorização."));

        doc.add(secao("CLÁUSULA 9 — DA LGPD E PROTEÇÃO DE DADOS"));
        doc.add(corpo("9.1. A CONTRATANTE é controladora dos dados pessoais de pacientes e profissionais inseridos no sistema."));
        doc.add(corpo("9.2. O CONTRATADO atua como operador técnico apenas durante o suporte, tratando dados estritamente para correção e manutenção, observando confidencialidade."));
        doc.add(corpo("9.3. A CONTRATANTE declara possuir base legal para tratamento dos dados e responsabiliza-se por consentimentos, políticas de privacidade e segurança operacional."));

        doc.add(secao("CLÁUSULA 10 — DA GARANTIA"));
        doc.add(corpo("10.1. O CONTRATADO garante que o software, na data da entrega, executa as funcionalidades descritas neste contrato, ressalvadas limitações de serviços de terceiros."));
        doc.add(corpo("10.2. Bugs reportados durante o período de suporte serão corrigidos sem custo adicional, desde que relacionados ao escopo entregue."));
        doc.add(corpo("10.3. O software é entregue \"como está\" após o término do suporte, cabendo à CONTRATANTE contratar manutenção evolutiva se desejar."));
        doc.add(corpo("10.4. O CONTRATADO não se responsabiliza por decisões clínicas, condutas de atendimento ou obrigações "
                + "regulatórias de prontuário eletrônico, uma vez que o sistema tem finalidade administrativa e operacional (item 1.4)."));

        doc.add(secao("CLÁUSULA 11 — DA LIMITAÇÃO DE RESPONSABILIDADE"));
        doc.add(corpo("11.1. O CONTRATADO não se responsabiliza, na extensão permitida pela lei, por lucros cessantes, perda de "
                + "receita, danos indiretos, indisponibilidade da KingHost/InfinitePay/internet, erro de uso da equipe da "
                + "CONTRATANTE ou perda de dados por ausência de backup/cancelamento do VPS."));
        doc.add(corpo("11.2. A responsabilidade total do CONTRATADO ficará limitada ao valor efetivamente pago pela CONTRATANTE (Cláusula 4)."));

        doc.add(secao("CLÁUSULA 12 — DA RESCISÃO"));
        doc.add(corpo("12.1. O descumprimento de obrigações essenciais autoriza a parte prejudicada a rescindir o contrato, mediante notificação escrita com prazo de 10 (dez) dias para saneamento."));
        doc.add(corpo("12.2. Em caso de rescisão por inadimplência da CONTRATANTE, o CONTRATADO poderá suspender suporte e acesso técnico até regularização."));
        doc.add(corpo("12.3. Valores já pagos por serviços efetivamente prestados não serão restituídos."));

        doc.add(secao("CLÁUSULA 13 — DISPOSIÇÕES GERAIS"));
        doc.add(bullet("Este contrato substitui acordos verbais anteriores sobre o mesmo objeto;"));
        doc.add(bullet("Alterações só terão validade se feitas por escrito e assinadas por ambas as partes;"));
        doc.add(bullet("A tolerância de uma parte não implica renúncia de direitos;"));
        doc.add(bullet("O CONTRATADO presta serviços como pessoa física (CPF), emitindo recibo ou nota fiscal conforme legislação aplicável à sua situação;"));
        doc.add(bullet("Fica eleito o foro da comarca de ___________________________________, renunciando-se a qualquer outro, por mais privilegiado que seja."));

        doc.add(espaco(10));
        doc.add(corpo("E, por estarem de pleno acordo, assinam o presente instrumento em 2 (duas) vias de igual teor e forma."));
        doc.add(corpo("Local e data: _________________________, _____ de _________________ de 2026."));
        doc.add(espaco(20));
        doc.add(assinatura("CONTRATADO (Desenvolvedor)", "Nome: Widinei Martins de Oliveira", "CPF: 147.786.936-04"));
        doc.add(espaco(15));
        doc.add(assinatura("CONTRATANTE (Clínica)", "Nome/Razão Social: ___________________________", "CPF/CNPJ: ___________________________________"));
        doc.add(espaco(15));
        doc.add(assinatura("Testemunha 1", "Nome: _______________________________________", "CPF: ________________________________________"));
        doc.add(espaco(15));
        doc.add(assinatura("Testemunha 2", "Nome: _______________________________________", "CPF: ________________________________________"));
        doc.add(espaco(10));
        Paragraph obs = new Paragraph(
                "Observação: Este documento é um modelo contratual. Recomenda-se revisão por advogado antes da assinatura. "
                        + "O CONTRATADO atua como pessoa física (CPF); não possui CNPJ. Emissão de recibo conforme orientação contábil local.",
                SMALL);
        obs.setSpacingBefore(8);
        doc.add(obs);
    }

    private static void adicionarClausulaInfraestrutura(Document doc) throws DocumentException {
        doc.add(secao("CLÁUSULA 3 — DA INFRAESTRUTURA (HOSPEDAGEM E BANCO DE DADOS)"));
        doc.add(corpo("O sistema Agenda Afetto opera em produção em um servidor VPS (KingHost) no Brasil. "
                + "O site e o banco PostgreSQL rodam no mesmo servidor, em containers Docker. "
                + "Reiniciar ou atualizar o sistema não apaga agendamentos, relatórios nem cadastros."));

        doc.add(corpo("3.1. Servidor VPS — KingHost"));
        doc.add(tabelaDoisColunas(
                "Provedor", "KingHost (kinghost.com.br) — servidor virtual privado (VPS)",
                "Plano", "VPS 4 GB LINUX",
                "Plataforma", "Ubuntu 24.04 LTS com Docker",
                "Memória RAM", "4,00 GB",
                "Processadores (vCPU)", "2 processadores",
                "Espaço em disco", "70,00 GB contratados",
                "Localização", "Brasil",
                "Sistema", "Agenda Afetto — Plataforma Web de Gestão Clínica (versão 2.6)",
                "Domínio / URL", "afetto-agenda.vps-kinghost.net",
                "Arquitetura", "Containers Docker: aplicação Java (Spring Boot) porta 8080 + PostgreSQL 16",
                "Banco de dados", "PostgreSQL 16, banco clinica_agenda, volume persistente no disco do VPS",
                "Custo mensal do VPS", "R$ 48,00 (quarenta e oito reais por mês)",
                "Pagamento", "CONTRATANTE (Clínica), diretamente à KingHost, conforme Cláusula 8"));
        doc.add(corpo("3.1.1. O VPS mantém o site no ar 24h. Acesso pelo navegador; atualizações por deploy no servidor."));
        doc.add(corpo("3.1.2. Banco PostgreSQL no mesmo VPS: dados em volume persistente; redeploy não apaga histórico; "
                + "painel admin «Uso do banco» acompanha tamanho e percentual de uso."));
        doc.add(corpo("3.1.3. Vantagens: custo único de R$ 48/mês (site + banco); 4 GB RAM e 70 GB disco com folga; "
                + "servidor no Brasil; sem limitações de planos gratuitos que dormem ou expiram."));
        doc.add(corpo("3.1.4. Backup dos dados: a CONTRATANTE é responsável pelo VPS KingHost e por solicitar/manter backups "
                + "do banco PostgreSQL. O CONTRATADO orienta, mas não garante recuperação se o servidor for apagado, "
                + "reinstalado ou cancelado sem backup. Não cancelar o VPS sem exportar dados."));

        doc.add(corpo("3.2. Resumo dos custos mensais de infraestrutura (serviço de terceiro):"));
        doc.add(tabelaInfraestrutura());
        doc.add(corpo("3.3. O valor acima não está incluído no preço da Cláusula 4 (R$ 2.000,00). "
                + "É despesa operacional da CONTRATANTE, paga à KingHost."));
        doc.add(corpo("3.4. Credenciais do painel KingHost, servidor (SSH) e administrador do sistema serão entregues "
                + "conforme combinado na entrega técnica."));
    }

    private static PdfPTable tabelaDoisColunas(String... pares) throws DocumentException {
        PdfPTable tabela = new PdfPTable(new float[]{1.4f, 3.6f});
        tabela.setWidthPercentage(100);
        tabela.setSpacingAfter(6);
        for (int i = 0; i < pares.length; i += 2) {
            PdfPCell rotulo = new PdfPCell(new Phrase(pares[i], BODY_BOLD));
            rotulo.setBackgroundColor(new Color(244, 248, 248));
            rotulo.setPadding(5);
            tabela.addCell(rotulo);
            PdfPCell valor = new PdfPCell(new Phrase(pares[i + 1], BODY));
            valor.setPadding(5);
            tabela.addCell(valor);
        }
        return tabela;
    }

    private static PdfPTable tabelaInfraestrutura() throws DocumentException {
        PdfPTable tabela = new PdfPTable(new float[]{2.2f, 1.2f, 1.2f, 1.4f});
        tabela.setWidthPercentage(100);
        tabela.setSpacingAfter(8);
        cabecalho(tabela, "Serviço");
        cabecalho(tabela, "Provedor");
        cabecalho(tabela, "Plano");
        cabecalho(tabela, "Custo mensal (R$)");
        linhaQuatro(tabela, "VPS (site + banco PostgreSQL)", "KingHost", "VPS 4 GB LINUX", "48,00");
        linhaQuatro(tabela, "Total mensal de infraestrutura", "", "", "48,00/mês");
        return tabela;
    }

    private static void linhaQuatro(PdfPTable tabela, String c1, String c2, String c3, String c4) {
        Font font = c1.startsWith("Total") ? BODY_BOLD : BODY;
        tabela.addCell(celula(c1, font));
        tabela.addCell(celula(c2, font));
        tabela.addCell(celula(c3, font));
        tabela.addCell(celula(c4, font));
    }

    private static Paragraph secao(String texto) {
        Paragraph p = new Paragraph(texto, H2);
        p.setSpacingBefore(10);
        p.setSpacingAfter(6);
        return p;
    }

    private static Paragraph corpo(String texto) {
        Paragraph p = new Paragraph(texto, BODY);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setSpacingAfter(5);
        return p;
    }

    private static Paragraph bullet(String texto) {
        Paragraph p = new Paragraph("• " + texto, BODY);
        p.setIndentationLeft(12);
        p.setSpacingAfter(3);
        return p;
    }

    private static Paragraph modulo(String titulo, String... itens) throws DocumentException {
        Paragraph bloco = new Paragraph();
        bloco.setSpacingAfter(6);
        bloco.add(new Chunk(titulo + "\n", BODY_BOLD));
        for (String item : itens) {
            bloco.add(new Chunk("• " + item + "\n", BODY));
        }
        return bloco;
    }

    private static PdfPTable tabelaPrecos() throws DocumentException {
        PdfPTable tabela = new PdfPTable(new float[]{0.6f, 3.4f, 1.2f});
        tabela.setWidthPercentage(100);
        tabela.setSpacingAfter(8);
        cabecalho(tabela, "Item");
        cabecalho(tabela, "Descrição");
        cabecalho(tabela, "Valor (R$)");
        linha(tabela, "1", "Licença de uso do sistema Agenda Afetto (v2.6), entrega técnica, implantação na KingHost "
                + "e suporte por 3 (três) meses para correção de erros (Cláusula 6)", "2.000,00");
        return tabela;
    }

    private static void cabecalho(PdfPTable tabela, String texto) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, BODY_BOLD));
        cell.setBackgroundColor(new Color(244, 248, 248));
        cell.setPadding(5);
        tabela.addCell(cell);
    }

    private static void linha(PdfPTable tabela, String c1, String c2, String c3) {
        Font font = c1.isEmpty() ? BODY_BOLD : BODY;
        tabela.addCell(celula(c1, font));
        tabela.addCell(celula(c2, font));
        tabela.addCell(celula(c3, font));
    }

    private static PdfPCell celula(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setPadding(5);
        return cell;
    }

    private static Paragraph assinatura(String titulo, String linha1, String linha2) {
        Paragraph p = new Paragraph();
        p.add(new Chunk("_______________________________________________\n", BODY));
        p.add(new Chunk(titulo + "\n", BODY_BOLD));
        p.add(new Chunk(linha1 + "\n", BODY));
        p.add(new Chunk(linha2, BODY));
        return p;
    }

    private static Paragraph espaco(float altura) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(altura);
        return p;
    }
}
