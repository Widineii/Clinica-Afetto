package com.clinica.sistema.service;

import com.clinica.sistema.model.AvisoPagamentoEmailJanela;

/**
 * Modelo HTML do e-mail de aviso de pagamento — mesmo visual do {@link RecuperacaoSenhaEmailTemplate}.
 */
final class AvisoPagamentoEmailTemplate {

    static final String LOGO_CONTENT_ID = "afetto-logo";

    private AvisoPagamentoEmailTemplate() {
    }

    record ConteudoEmail(String textoPlano, String html) {
    }

    static ConteudoEmail montar(
            AvisoPagamentoEmailJanela janela,
            String nome,
            String total,
            String urlPagamentos
    ) {
        String nomeAplicado = nome == null || nome.isBlank() ? "Profissional" : nome.trim();
        String totalAplicado = total == null || total.isBlank() ? "R$ 0,00" : total.trim();
        String assuntoLinha = janela.getTituloAssunto();
        String corpoTexto = janela.montarCorpo(nomeAplicado, totalAplicado);
        String mensagemHtml = formatarCorpoHtml(janela, totalAplicado);
        String base = normalizarUrlSite(urlPagamentos);

        String textoPlano = """
                %s

                %s

                Acesse Meus pagamentos: %s
                """.formatted(assuntoLinha, corpoTexto, urlPagamentos);

        String html = """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s — Clínica Afetto</title>
                </head>
                <body style="margin:0;padding:0;background-color:#F1F5F9;font-family:Arial,Helvetica,sans-serif;color:#334155;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#F1F5F9;padding:32px 16px 40px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:600px;background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                          <tr>
                            <td style="height:5px;background-color:#1B4D5C;font-size:0;line-height:0;">&nbsp;</td>
                          </tr>
                          <tr>
                            <td style="padding:28px 30px 22px;border-bottom:1px solid #E2E8F0;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                                <tr>
                                  <td valign="middle" style="width:200px;padding-right:20px;">
                                    <img src="cid:%s" alt="Clínica Afetto" width="180" style="display:block;width:180px;max-width:180px;height:auto;max-height:96px;border:0;">
                                  </td>
                                  <td valign="middle" align="right" style="text-align:right;">
                                    <p style="margin:0 0 4px;font-family:Arial,Helvetica,sans-serif;font-size:22px;font-weight:700;line-height:1.2;color:#1B4D5C;">Clínica Afetto</p>
                                    <p style="margin:0;font-size:14px;line-height:1.4;color:#64748B;">Agenda Afetto · Pagamento pendente</p>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:28px 30px 8px;">
                              <p style="margin:0 0 6px;font-size:12px;font-weight:700;letter-spacing:0.1em;text-transform:uppercase;color:#1B4D5C;">%s</p>
                              <h1 style="margin:0 0 18px;font-family:Arial,Helvetica,sans-serif;font-size:20px;font-weight:700;line-height:1.35;color:#0F172A;">Regularize seu pagamento</h1>
                              <p style="margin:0 0 14px;font-size:15px;line-height:1.65;color:#334155;">Olá, <strong style="color:#1B4D5C;">%s</strong>.</p>
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td align="center" style="padding:0 30px 22px;">
                              <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="width:100%%;background-color:#F8FAFC;border:1px solid #CBD5E1;border-radius:12px;">
                                <tr>
                                  <td align="center" style="padding:22px 18px 18px;">
                                    <p style="margin:0 0 10px;font-size:11px;font-weight:700;letter-spacing:0.12em;text-transform:uppercase;color:#64748B;">Valor pendente</p>
                                    <p style="margin:0;font-size:32px;line-height:1;font-weight:700;color:#1B4D5C;font-family:Arial,Helvetica,sans-serif;">%s</p>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td align="center" style="padding:0 30px 28px;">
                              <table role="presentation" cellspacing="0" cellpadding="0" border="0">
                                <tr>
                                  <td style="border-radius:8px;background-color:#1B4D5C;">
                                    <a href="%s" style="display:inline-block;padding:14px 28px;color:#ffffff;font-weight:700;text-decoration:none;font-size:15px;">Entrar no site e pagar</a>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 30px 22px;background-color:#F8FAFC;border-top:1px solid #E2E8F0;">
                              <p style="margin:0 0 4px;font-size:13px;font-weight:700;color:#1B4D5C;text-align:center;">Clínica Afetto</p>
                              <p style="margin:0 0 8px;font-size:12px;color:#64748B;text-align:center;">Saúde mental com acolhimento</p>
                              <p style="margin:0;font-size:11px;color:#94A3B8;text-align:center;">
                                <a href="%s" style="color:#1B4D5C;text-decoration:none;">%s</a>
                              </p>
                            </td>
                          </tr>
                        </table>
                        <p style="margin:14px 0 0;font-size:11px;color:#94A3B8;text-align:center;">
                          E-mail automático — não responda.
                        </p>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                assuntoLinha,
                LOGO_CONTENT_ID,
                assuntoLinha,
                nomeAplicado,
                mensagemHtml,
                totalAplicado,
                urlPagamentos,
                base,
                base
        );

        return new ConteudoEmail(textoPlano, html);
    }

    private static String formatarCorpoHtml(AvisoPagamentoEmailJanela janela, String total) {
        String corpo = janela.montarCorpo("Profissional", total);
        int inicio = corpo.indexOf("\n\n");
        String apenasMensagem = inicio >= 0 ? corpo.substring(inicio + 2) : corpo;
        int fimRodape = apenasMensagem.lastIndexOf("\n\nClínica Afetto");
        if (fimRodape >= 0) {
            apenasMensagem = apenasMensagem.substring(0, fimRodape);
        }
        String[] paragrafos = apenasMensagem.split("\n\n");
        StringBuilder html = new StringBuilder();
        for (String paragrafo : paragrafos) {
            if (paragrafo.isBlank()) {
                continue;
            }
            html.append("<p style=\"margin:0 0 14px;font-size:15px;line-height:1.65;color:#334155;\">")
                    .append(paragrafo.replace("\n", "<br>"))
                    .append("</p>");
        }
        return html.toString();
    }

    private static String normalizarUrlSite(String urlPagamentos) {
        if (urlPagamentos == null || urlPagamentos.isBlank()) {
            return "http://localhost:8081";
        }
        String base = urlPagamentos.trim();
        int barraAgendamentos = base.indexOf("/agendamentos/");
        if (barraAgendamentos > 0) {
            base = base.substring(0, barraAgendamentos);
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
