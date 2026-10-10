package it.sdc.src.dto.requests.accountedits;

import it.sdc.src.validation.Base64String;
import it.sdc.src.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank @Size(max = 255) String oldPassword,
        @NotNull @StrongPassword String newPassword,
        @NotNull @Base64String String newKekSalt,
        @NotNull @Base64String String newPrivateEd25519,
        @NotNull @Base64String String newIvEd25519,
        @NotNull @Base64String String newPrivateX25519,
        @NotNull @Base64String String newIvX25519
) {}
