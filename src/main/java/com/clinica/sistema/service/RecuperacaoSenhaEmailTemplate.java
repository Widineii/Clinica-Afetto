package com.clinica.sistema.service;

/**
 * Modelo HTML do e-mail de recuperacao de senha — layout alinhado aos relatorios impressos da clinica.
 * A logo e referenciada como {@code cid:afetto-logo} (anexo inline no {@link EmailEnvioService}).
 */
final class RecuperacaoSenhaEmailTemplate {

    static final String LOGO_CONTENT_ID = "afetto-logo";

    private RecuperacaoSenhaEmailTemplate() {
    }

    record ConteudoEmail(String textoPlano, String html) {
    }

    static ConteudoEmail montar(String codigo, int expiracaoMinutos, String urlSite) {
        String base = normalizarUrlSite(urlSite);

        String textoPlano = """
                Olá,

                Você solicitou a redefinição de senha no Agenda Afetto.

                Seu código é: %s

                Ele expira em %d minutos. Se você não fez esta solicitação, ignore este e-mail.

                Clínica Afetto
                %s
                """.formatted(codigo, expiracaoMinutos, base);

        String html = """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Redefinir senha — Clínica Afetto</title>
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
                                    <p style="margin:0;font-size:14px;line-height:1.4;color:#64748B;">Agenda Afetto · Recuperação de senha</p>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:28px 30px 8px;">
                              <p style="margin:0 0 6px;font-size:12px;font-weight:700;letter-spacing:0.1em;text-transform:uppercase;color:#1B4D5C;">Código de verificação</p>
                              <h1 style="margin:0 0 18px;font-family:Arial,Helvetica,sans-serif;font-size:20px;font-weight:700;line-height:1.35;color:#0F172A;">Redefinir sua senha</h1>
                              <p style="margin:0 0 14px;font-size:15px;line-height:1.65;color:#334155;">Olá,</p>
                              <p style="margin:0 0 22px;font-size:15px;line-height:1.65;color:#334155;">
                                Você solicitou a redefinição de senha no sistema. Informe o código abaixo na tela de confirmação:
                              </p>
                            </td>
                          </tr>
                          <tr>
                            <td align="center" style="padding:0 30px 26px;">
                              <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="width:100%%;background-color:#F8FAFC;border:1px solid #CBD5E1;border-radius:12px;">
                                <tr>
                                  <td align="center" style="padding:22px 18px 18px;">
                                    <p style="margin:0 0 10px;font-size:11px;font-weight:700;letter-spacing:0.12em;text-transform:uppercase;color:#64748B;">Seu código</p>
                                    <p style="margin:0;font-size:38px;line-height:1;font-weight:700;letter-spacing:0.32em;color:#1B4D5C;font-family:Consolas,Monaco,'Courier New',monospace;">%s</p>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 30px 28px;">
                              <p style="margin:0 0 12px;font-size:14px;line-height:1.65;color:#334155;">
                                Este código expira em <strong style="color:#1B4D5C;">%d minutos</strong>.
                              </p>
                              <p style="margin:0;font-size:14px;line-height:1.65;color:#64748B;">
                                Se você não fez esta solicitação, ignore este e-mail. Sua senha permanece a mesma.
                              </p>
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
                """.formatted(LOGO_CONTENT_ID, codigo, expiracaoMinutos, base, base);

        return new ConteudoEmail(textoPlano, html);
    }

    private static String normalizarUrlSite(String urlSite) {
        if (urlSite == null || urlSite.isBlank()) {
            return "http://localhost:8081";
        }
        String base = urlSite.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
