package it.sdc.src.dto.requests;

import it.sdc.src.validation.Base64String;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MessageRequest(
        // Allow messages of at most 20 MB (20971520 bytes become 27962028 when encoded)
        @NotNull @Size(min = 4, max = 27962028) @Base64String String messageData,
        // 12 bytes IV is 16 bytes in B64
        @NotNull @Size(min = 16, max = 16) @Base64String String messageIV
) {}
