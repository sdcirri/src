package it.sdc.src.dto;

import it.sdc.src.validation.Base64String;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record UserSessionDto(
        @NotNull UUID id,
        @NotNull @NotBlank @Base64String String accessToken,
        @NotNull @Positive Long accessTokenExpires,
        @NotNull @NotBlank @Base64String String refreshToken,
        @NotNull @Positive Long refreshTokenExpires
) {}
