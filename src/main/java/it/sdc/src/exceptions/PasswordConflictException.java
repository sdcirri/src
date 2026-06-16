package it.sdc.src.exceptions;

public class PasswordConflictException extends RuntimeException {
    public PasswordConflictException(String message) {
        super(message);
    }
}
