package it.sdc.src.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class BusinessException extends RuntimeException {
    private final HttpStatus statusCode;

    public BusinessException(String message, HttpStatus statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public BusinessException(HttpStatus statusCode) {
        this.statusCode = statusCode;
    }
}
