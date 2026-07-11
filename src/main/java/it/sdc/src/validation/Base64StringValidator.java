package it.sdc.src.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Base64;

public class Base64StringValidator implements ConstraintValidator<Base64String, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        // nullability is out of validator's concern
        if (value == null) return true;
        try {
            Base64.getDecoder().decode(value);
            return true;
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }
}
