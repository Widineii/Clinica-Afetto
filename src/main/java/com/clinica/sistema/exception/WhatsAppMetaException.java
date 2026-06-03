package com.clinica.sistema.exception;

public class WhatsAppMetaException extends RuntimeException {

    public WhatsAppMetaException(String message) {
        super(message);
    }

    public WhatsAppMetaException(String message, Throwable cause) {
        super(message, cause);
    }
}
