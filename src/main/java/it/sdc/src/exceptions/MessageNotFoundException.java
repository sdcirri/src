package it.sdc.src.exceptions;

import org.springframework.http.HttpStatus;

public class MessageNotFoundException extends BusinessException {
    public MessageNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
