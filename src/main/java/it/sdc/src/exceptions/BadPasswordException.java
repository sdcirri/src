package it.sdc.src.exceptions;

import org.springframework.http.HttpStatus;

public class BadPasswordException extends BusinessException {
    public BadPasswordException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
