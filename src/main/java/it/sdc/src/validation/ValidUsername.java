package it.sdc.src.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Pattern(regexp = ValidUsername.REGEX)
public @interface ValidUsername {
    String REGEX = "^[A-Za-z0-9_.-]{3,255}$";

    String message() default "must be a valid username";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
