package it.sdc.src.controllers;

import it.sdc.src.dto.ContactCryptoDto;
import it.sdc.src.dto.UserCryptoDto;
import it.sdc.src.dto.UserDto;
import it.sdc.src.dto.UserSessionDto;
import it.sdc.src.dto.requests.*;
import it.sdc.src.dto.requests.accountedits.DisplayNameChangeRequest;
import it.sdc.src.dto.requests.accountedits.PasswordChangeRequest;
import it.sdc.src.dto.requests.accountedits.UsernameChangeRequest;
import it.sdc.src.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public UserSessionDto login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @PostMapping("/refresh")
    public UserSessionDto refreshSession() {
        // TODO: Bearer auth
        return authService.refreshAccessToken(UUID.randomUUID(), "xxx");
    }

    @GetMapping("/me/crypto")
    public UserCryptoDto getCryptoSpecs() {
        // TODO: Bearer auth
        return authService.getMyCryptoSpecs(UUID.randomUUID());
    }

    @PostMapping("/register")
    public UserSessionDto register(@Valid @RequestBody UserRegistrationRequest request) {
        return authService.register(request);
    }

    @PostMapping("/register/finalize")
    public UserCryptoDto finalizeRegistration(
            @Valid @RequestBody UserRegistrationFinalizationRequest request
    ) {
        // TODO: Bearer auth
        return authService.finalizeRegistration(UUID.randomUUID(), request);
    }

    @PutMapping("/me/display_name")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto setDisplayName(@Valid @RequestBody DisplayNameChangeRequest request) {
        // TODO: Bearer auth
        return authService.setDisplayName(UUID.randomUUID(), request.displayName());
    }

    @PutMapping("/me/username")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto setUsername(@Valid @RequestBody UsernameChangeRequest request) {
        // TODO: Bearer auth
        return authService.changeUsername(UUID.randomUUID(), request.username());
    }

    @PostMapping("/me/password")
    public UserSessionDto changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        // TODO: Bearer auth
        return authService.changePassword(UUID.randomUUID(), request.password());
    }

    @GetMapping("/{contactId}/crypto")
    public ContactCryptoDto getUserCryptoSpecs(@PathVariable @NotNull UUID contactId) {
        return authService.getUserCryptoSpecs(contactId);
    }
}
