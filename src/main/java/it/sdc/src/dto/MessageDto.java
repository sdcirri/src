package it.sdc.src.dto;

import it.sdc.src.validation.Base64String;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MessageDto(
        @NotNull @Positive Long timestamp,
        @NotNull @Base64String String data,
        @NotNull @Base64String String iv,
        @NotNull MessageDirection direction
) {
    public enum MessageDirection {
        INCOMING,
        OUTGOING
    }
}
