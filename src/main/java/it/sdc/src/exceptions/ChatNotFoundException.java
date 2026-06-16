package it.sdc.src.exceptions;

import org.springframework.http.HttpStatus;

public class ChatNotFoundException extends BusinessException {
    public ChatNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
