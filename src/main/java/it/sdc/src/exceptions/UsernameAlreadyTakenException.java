package it.sdc.src.exceptions;

import org.springframework.http.HttpStatus;

public class UsernameAlreadyTakenException extends BusinessException {
    public UsernameAlreadyTakenException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
