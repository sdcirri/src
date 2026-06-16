package it.sdc.src.exceptions;

import org.springframework.http.HttpStatus;

public class SelfChatException extends BusinessException {
    public SelfChatException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
