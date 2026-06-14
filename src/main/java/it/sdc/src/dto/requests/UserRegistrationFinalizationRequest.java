package it.sdc.src.dto.requests;

import it.sdc.src.validation.Base64String;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UserRegistrationFinalizationRequest(
        @NotNull UUID id,
        @NotNull @NotBlank @Base64String String kekSalt,
        @NotNull @NotBlank @Base64String String privateEd25519Crypto,
        @NotNull @NotBlank @Base64String String privateEd25519IV,
        @NotNull @NotBlank @Base64String String publicEd25519,
        @NotNull @NotBlank @Base64String String privateX25519Crypto,
        @NotNull @NotBlank @Base64String String privateX25519IV,
        @NotNull @NotBlank @Base64String String publicX25519
) {}
