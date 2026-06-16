package it.sdc.src.dto.requests.accountedits;

import it.sdc.src.validation.ValidUsername;
import jakarta.validation.constraints.NotNull;

public record UsernameChangeRequest(
        @NotNull @ValidUsername String username
) {}
