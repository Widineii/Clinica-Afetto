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
        Document doc = new Document(PageSize.A4, 50, 50, 45, 45);
        PdfWriter.getInstance(doc, java.nio.file.Files.newOutputStream(destino));
        doc.open();

        Paragraph titulo = new Paragraph("CONTRATO DE LICENCIAMENTO DE SOFTWARE\nE PRESTAÇÃO DE SERVIÇOS DE SUPORTE", TITLE);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(4);
        doc.add(titulo);

        Paragraph sub = new Paragraph("Sistema: Agenda Afetto — Plataforma Web de Gestão Clínica\nVersão entregue: 2.6 | Documento: maio/2026", SUBTITLE);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(14);
        doc.add(sub);

        doc.add(corpo(
                "Pelo presente instrumento particular, de um lado _______________________________________________, "
                        + "brasileiro(a), inscrito(a) no CPF sob nº _______________________, residente e domiciliado(a) em "
                        + "_______________________________________________, doravante denominado(a) CONTRATADO ou DESENVOLVEDOR; "
                        + "e, de outro lado, _______________________________________________, inscrito(a) no CPF/CNPJ sob nº "
                        + "_______________________, com endereço em _______________________________________________, "
                        + "representado(a) neste ato por _______________________________________________, doravante denominado(a) "
                        + "CONTRATANTE ou CLÍNICA, têm entre si justo e contratado o seguinte:"));

        doc.add(secao("CLÁUSULA 1 — DO OBJETO"));
        doc.add(corpo("1.1. O presente contrato tem por objeto a licença de uso exclusiva do software web denominado "
                + "\"Agenda Afetto\", bem como a prestação de serviços de implantação, entrega técnica e suporte pelo "
                + "prazo de 3 (três) meses, contados a partir da data de assinatura deste instrumento ou do pagamento "
                + "integral, o que ocorrer por último."));
        doc.add(corpo("1.2. O software é uma aplicação web desenvolvida em Java (Spring Boot), com interface em "
                + "Thymeleaf/HTML, destinada à gestão operacional, financeira e administrativa de clínica de saúde, "
                + "incluindo controle de agenda por profissional, sala e paciente."));

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
                "Aplicação web em produção na nuvem (Railway) com banco PostgreSQL gerenciado (Neon), conforme Cláusula 3;",
                "Ambiente de desenvolvimento local (perfil local com H2);",
                "Código-fonte completo do projeto entregue via repositório Git ou arquivo compactado;",
                "Documentação básica de execução (README) e variáveis de ambiente necessárias."));

        adicionarClausulaInfraestrutura(doc);

        doc.add(secao("CLÁUSULA 4 — DO PREÇO E FORMA DE PAGAMENTO"));
        doc.add(tabelaPrecos());
        doc.add(corpo("4.2. Forma de pagamento: ( ) À vista   ( ) Parcelado em _____ vezes   | Meio: ( ) PIX   ( ) Transferência   ( ) Outro: __________"));
        doc.add(corpo("4.3. Chave PIX / dados bancários do CONTRATADO: _______________________________________________"));
        doc.add(corpo("4.4. A entrega definitiva do acesso, credenciais e código-fonte ocorrerá após a confirmação do pagamento integral ou do primeiro pagamento acordado, conforme combinado entre as partes."));

        doc.add(secao("CLÁUSULA 5 — DA LICENÇA E PROPRIEDADE INTELECTUAL"));
        doc.add(corpo("5.1. O CONTRATADO concede à CONTRATANTE licença de uso exclusiva e perpetua do sistema Agenda Afetto para operação da clínica contratante, incluindo direito de hospedar, utilizar e manter o software em produção."));
        doc.add(corpo("5.2. Com o pagamento integral, a CONTRATANTE recebe o código-fonte e passa a ser titular do direito de uso exclusivo na sua operação, não podendo o CONTRATADO revender ou licenciar o mesmo código, customizado para esta clínica, a terceiros concorrentes diretos, salvo acordo em contrário."));
        doc.add(corpo("5.3. Bibliotecas de terceiros, frameworks open source e serviços externos (InfinitePay, Railway, Neon, PostgreSQL etc.) permanecem regidos por suas respectivas licenças e termos de uso."));
        doc.add(corpo("5.4. Marcas, logotipos e identidade visual da clínica pertencem à CONTRATANTE. A marca \"Agenda Afetto\" e componentes genéricos reutilizáveis permanecem de titularidade do CONTRATADO, salvo renúncia expressa."));

        doc.add(secao("CLÁUSULA 6 — DO SUPORTE TÉCNICO (3 MESES)"));
        doc.add(corpo("6.1. O CONTRATADO prestará suporte técnico por 90 (noventa) dias corridos, incluindo:"));
        doc.add(bullet("Correção de bugs e erros de funcionamento do sistema entregue;"));
        doc.add(bullet("Orientação sobre uso das funcionalidades existentes;"));
        doc.add(bullet("Ajustes menores de configuração (usuários, variáveis de ambiente, deploy);"));
        doc.add(bullet("Atendimento via WhatsApp e/ou e-mail em horário comercial (segunda a sexta, 9h às 18h)."));
        doc.add(corpo("6.2. Não estão incluídos no suporte (salvo orçamento à parte):"));
        doc.add(bullet("Desenvolvimento de novas funcionalidades ou módulos;"));
        doc.add(bullet("Redesign completo de telas;"));
        doc.add(bullet("Migração para outro servidor/provedor não acordado;"));
        doc.add(bullet("Custos de hospedagem (Railway), banco de dados (Neon), domínio e taxas de gateway de pagamento;"));
        doc.add(bullet("Treinamento presencial ou visitas à clínica;"));
        doc.add(bullet("Problemas causados por mau uso, alteração não autorizada do código por terceiros ou indisponibilidade de serviços externos."));
        doc.add(corpo("6.3. Prazo de resposta: até 48 (quarenta e oito) horas úteis para demandas normais; urgências operacionais (sistema fora do ar) com prioridade em até 24 horas úteis."));
        doc.add(corpo("6.4. Após o período de 3 meses, eventuais manutenções poderão ser contratadas separadamente mediante proposta comercial."));

        doc.add(secao("CLÁUSULA 7 — DAS OBRIGAÇÕES DO CONTRATADO"));
        doc.add(bullet("Entregar o sistema funcional conforme descrito na Cláusula 2;"));
        doc.add(bullet("Fornecer código-fonte, instruções de deploy e credenciais acordadas;"));
        doc.add(bullet("Prestar suporte pelo prazo de 3 meses;"));
        doc.add(bullet("Manter sigilo sobre dados da clínica e pacientes a que tiver acesso;"));
        doc.add(bullet("Informar limitações conhecidas do sistema no momento da entrega."));

        doc.add(secao("CLÁUSULA 8 — DAS OBRIGAÇÕES DA CONTRATANTE"));
        doc.add(bullet("Efetuar o pagamento nos prazos acordados;"));
        doc.add(bullet("Fornecer dados, logotipos e informações necessários à operação;"));
        doc.add(bullet("Manter backup de dados e credenciais de acesso;"));
        doc.add(bullet("Contratar e pagar hospedagem (Railway), banco de dados (Neon), domínio e serviços de pagamento (InfinitePay etc.), conforme Cláusula 3;"));
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

        doc.add(secao("CLÁUSULA 11 — DA RESCISÃO"));
        doc.add(corpo("11.1. O descumprimento de obrigações essenciais autoriza a parte prejudicada a rescindir o contrato, mediante notificação escrita com prazo de 10 (dez) dias para saneamento."));
        doc.add(corpo("11.2. Em caso de rescisão por inadimplência da CONTRATANTE, o CONTRATADO poderá suspender suporte e acesso técnico até regularização."));
        doc.add(corpo("11.3. Valores já pagos por serviços efetivamente prestados não serão restituídos."));

        doc.add(secao("CLÁUSULA 12 — DISPOSIÇÕES GERAIS"));
        doc.add(bullet("Este contrato substitui acordos verbais anteriores sobre o mesmo objeto;"));
        doc.add(bullet("Alterações só terão validade se feitas por escrito e assinadas por ambas as partes;"));
        doc.add(bullet("A tolerância de uma parte não implica renúncia de direitos;"));
        doc.add(bullet("O CONTRATADO presta serviços como pessoa física (CPF), emitindo recibo ou nota fiscal conforme legislação aplicável à sua situação;"));
        doc.add(bullet("Fica eleito o foro da comarca de ___________________________________, renunciando-se a qualquer outro, por mais privilegiado que seja."));

        doc.add(espaco(10));
        doc.add(corpo("E, por estarem de pleno acordo, assinam o presente instrumento em 2 (duas) vias de igual teor e forma."));
        doc.add(corpo("Local e data: _________________________, _____ de _________________ de 2026."));
        doc.add(espaco(20));
        doc.add(assinatura("CONTRATADO (Desenvolvedor)", "Nome: _______________________________________", "CPF: ________________________________________"));
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

        doc.close();
    }

    private static void adicionarClausulaInfraestrutura(Document doc) throws DocumentException {
        doc.add(secao("CLÁUSULA 3 — DA INFRAESTRUTURA EM NUVEM (HOSPEDAGEM E BANCO DE DADOS)"));
        doc.add(corpo("O sistema Agenda Afetto opera em produção com o site (aplicação web) e o banco de dados em serviços "
                + "distintos na nuvem. Os dados da clínica ficam no banco; reiniciar ou atualizar o site não apaga "
                + "agendamentos, relatórios nem cadastros."));

        doc.add(corpo("3.1. Hospedagem do site (aplicação web) — Railway"));
        doc.add(tabelaDoisColunas(
                "Provedor", "Railway (railway.app) — plataforma de hospedagem em nuvem",
                "Serviço", "Web Service em container Docker, deploy automático via GitHub",
                "Nome do projeto", "clinica-agenda",
                "Sistema", "Agenda Afetto — Plataforma Web de Gestão Clínica (versão 2.6)",
                "URL pública", "https://clinica-agenda-production-e992.up.railway.app",
                "Região", "Estados Unidos (US East), alinhada ao banco Neon",
                "Recursos", "Memória RAM configurada em 1 GB",
                "Plano", "Railway Hobby (cobrança mensal conforme uso)",
                "Custo mensal estimado", "R$ 28,00 a R$ 55,00 (referência em reais; fatura conforme consumo e cotação USD/BRL)",
                "Pagamento", "CONTRATANTE (Clínica), conforme Cláusula 8"));
        doc.add(corpo("3.1.1. O Railway mantém o site no ar 24h, executando a aplicação Java (Spring Boot). "
                + "Atualizações são publicadas por deploy; o acesso é pelo navegador, sem instalar software na clínica."));
        doc.add(corpo("3.1.2. No plano Hobby pago, o serviço permanece disponível para uso diário. "
                + "Lentidão pontual pode ocorrer na primeira requisição após longo período sem acesso."));

        doc.add(corpo("3.2. Banco de dados — Neon (PostgreSQL)"));
        doc.add(tabelaDoisColunas(
                "Provedor", "Neon (neon.tech) — PostgreSQL gerenciado na nuvem (serverless)",
                "Serviço", "PostgreSQL 16, projeto clinica-agenda, região US East (AWS)",
                "Plano", "Free (gratuito)",
                "Custo mensal", "R$ 0,00 (zero reais por mês, na data deste contrato)",
                "Limite de armazenamento", "512 MB (0,5 GB)",
                "Uso atual aproximado", "cerca de 30 MB (~6% do limite), medido pelo painel «Uso do banco (Neon)»",
                "Conexão", "Variáveis PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD, PGSSLMODE=require (SSL)",
                "Pagamento", "CONTRATANTE — atualmente sem cobrança; upgrade futuro conforme 3.2.4"));
        doc.add(corpo("3.2.1. Como funciona: agendamentos, usuários, financeiro e relatórios ficam no PostgreSQL do Neon; "
                + "o banco é independente do site — redeploy ou reinício no Railway não apaga dados; "
                + "o painel admin exibe tamanho real e percentual do limite (alertas em 60% e 85%)."));
        doc.add(corpo("3.2.2. Por que Neon free: o volume atual da clínica usa ~30 MB (~6%), muito abaixo do teto de 0,5 GB; "
                + "PostgreSQL profissional com persistência duradoura; arquitetura site (Railway) + banco (Neon) "
                + "permite escalar só quando necessário."));
        doc.add(corpo("3.2.3. Limitações do plano free: instância pode dormir sem consultas; "
                + "primeira conexão do dia pode demorar alguns segundos; backups com retenção limitada."));
        doc.add(corpo("3.2.4. Upgrade futuro: se uso ultrapassar ~60% (307 MB) ou crescer muito o histórico, "
                + "poderá ser necessário plano pago Neon (custo adicional em dólares); CONTRATADO orientará quando "
                + "o painel indicar atenção/crítico."));

        doc.add(corpo("3.3. Resumo dos custos mensais de infraestrutura (serviços de terceiros):"));
        doc.add(tabelaInfraestrutura());
        doc.add(corpo("3.4. Os valores acima não estão incluídos no preço de licenciamento da Cláusula 4 (R$ 3.000,00). "
                + "São despesas operacionais da CONTRATANTE. Domínio personalizado, se contratado, terá custo à parte."));
        doc.add(corpo("3.5. Credenciais dos painéis Railway, Neon e administrador do sistema serão entregues à CONTRATANTE "
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
        linhaQuatro(tabela, "Hospedagem do site Agenda Afetto", "Railway", "Hobby", "28,00 a 55,00");
        linhaQuatro(tabela, "Banco PostgreSQL", "Neon", "Free", "0,00");
        linhaQuatro(tabela, "Total estimado de infraestrutura", "", "", "28,00 a 55,00/mês");
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
        linha(tabela, "1", "Licença de uso exclusiva do sistema Agenda Afetto (v2.6) + entrega técnica", "2.500,00");
        linha(tabela, "2", "Suporte técnico por 3 (três) meses (correções, orientação e ajustes — Cláusula 6)", "500,00");
        linha(tabela, "", "TOTAL", "3.000,00");
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
