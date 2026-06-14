package it.sdc.src.dto;

import it.sdc.src.validation.Base64String;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContactCryptoDto(
        @NotNull @NotBlank @Base64String String publicEd25519,
        @NotNull @NotBlank @Base64String String publicX25519
) {}
