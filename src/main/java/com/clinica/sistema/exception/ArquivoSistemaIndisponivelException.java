package com.clinica.sistema.exception;

public class ArquivoSistemaIndisponivelException extends RuntimeException {

    public ArquivoSistemaIndisponivelException(String message) {
        super(message);
    }

    public ArquivoSistemaIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
