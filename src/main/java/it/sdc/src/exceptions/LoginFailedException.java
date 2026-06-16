package it.sdc.src.exceptions;

import org.springframework.http.HttpStatus;

public class LoginFailedException extends BusinessException {
    public LoginFailedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
