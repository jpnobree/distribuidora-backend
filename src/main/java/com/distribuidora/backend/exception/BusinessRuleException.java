package com.distribuidora.backend.exception;

// Operacao valida no formato, mas proibida por uma regra de negocio (422).
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
