package it.sdc.src.exceptions;

import org.springframework.http.HttpStatus;

public class PwnedPasswordException extends BusinessException {
    public PwnedPasswordException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
