package it.sdc.src.exceptions;

import org.springframework.http.HttpStatus;

public class CryptoConflictException extends BusinessException {
    public CryptoConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
