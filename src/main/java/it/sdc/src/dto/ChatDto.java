package it.sdc.src.dto;

import it.sdc.src.validation.Base64String;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChatDto(
        @NotNull UUID contactId,
        @NotNull @Base64String String contactEd25519,
        @NotNull @Base64String String contactX25519
) {}
