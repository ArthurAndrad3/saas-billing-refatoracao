package com.saas.billing.port;

/** Falha em um serviço externo (banco, gateway, SMTP). */
public class InfrastructureException extends RuntimeException {

    public InfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
