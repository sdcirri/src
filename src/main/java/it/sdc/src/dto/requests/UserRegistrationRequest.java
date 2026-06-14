package it.sdc.src.dto.requests;

import it.sdc.src.validation.StrongPassword;
import it.sdc.src.validation.ValidUsername;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

public record UserRegistrationRequest(
        @NotNull @ValidUsername String username,
        @Length(min = 1, max = 255) String displayName,
        @NotNull @NotBlank @StrongPassword String password
) {}
