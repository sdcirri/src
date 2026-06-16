package it.sdc.src.exceptions;

import org.springframework.http.HttpStatus;

public class PasswordConflictException extends BusinessException {
    public PasswordConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
