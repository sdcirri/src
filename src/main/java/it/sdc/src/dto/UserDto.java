package it.sdc.src.dto;

import it.sdc.src.validation.ValidUsername;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

import java.util.UUID;

public record UserDto(
        @NotNull UUID id,
        @NotNull @ValidUsername String username,
        @Length(min = 1, max = 255) String displayName
) {}
