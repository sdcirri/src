package it.sdc.src.dto.requests.accountedits;

import it.sdc.src.validation.Base64String;
import jakarta.validation.constraints.NotNull;

public record MessageRequest(
        @NotNull @Base64String String messageData,
        @NotNull @Base64String String messageIV
) {}
