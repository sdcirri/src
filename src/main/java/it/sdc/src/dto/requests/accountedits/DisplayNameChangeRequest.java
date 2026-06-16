package it.sdc.src.dto.requests.accountedits;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

public record DisplayNameChangeRequest(
        @NotNull @Length(min = 1, max = 255) String displayName
) {}
