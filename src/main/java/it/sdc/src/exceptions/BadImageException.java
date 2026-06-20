package it.sdc.src.exceptions;

import org.springframework.http.HttpStatus;

public class BadImageException extends BusinessException {
    public BadImageException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
