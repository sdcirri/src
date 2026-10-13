package it.sdc.src.exceptions;

import org.springframework.http.HttpStatus;

public class PartialRegistrationException extends BusinessException {
    public PartialRegistrationException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
