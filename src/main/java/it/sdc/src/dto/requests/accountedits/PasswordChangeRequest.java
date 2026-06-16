package it.sdc.src.dto.requests.accountedits;

import it.sdc.src.validation.StrongPassword;
import jakarta.validation.constraints.NotNull;

public record PasswordChangeRequest(
        @NotNull @StrongPassword String password
) {}
