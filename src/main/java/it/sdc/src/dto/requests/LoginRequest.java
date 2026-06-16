package it.sdc.src.dto.requests;

import it.sdc.src.validation.ValidUsername;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

public record LoginRequest(
        @NotNull @ValidUsername String username,
        @NotNull @Length(min = 8, max = 255) String password
) {}
