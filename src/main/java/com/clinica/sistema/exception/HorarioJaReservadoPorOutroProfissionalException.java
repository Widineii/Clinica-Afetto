package com.clinica.sistema.exception;

public class HorarioJaReservadoPorOutroProfissionalException extends RuntimeException {

    public static final String MENSAGEM_PADRAO =
            "A vaga foi preenchida por outro profissional. "
                    + "O pagamento nao pode ser concluido neste horario. "
                    + "Escolha outro horario ou sala na agenda.";

    public HorarioJaReservadoPorOutroProfissionalException() {
        super(MENSAGEM_PADRAO);
    }

    public HorarioJaReservadoPorOutroProfissionalException(String detalhe) {
        super(MENSAGEM_PADRAO + " " + detalhe);
    }
}
