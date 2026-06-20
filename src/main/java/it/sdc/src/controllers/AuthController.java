package it.sdc.src.controllers;

import it.sdc.src.auth.UserPrincipal;
import it.sdc.src.dto.UserCryptoDto;
import it.sdc.src.dto.UserSessionDto;
import it.sdc.src.dto.requests.*;
import it.sdc.src.dto.requests.accountedits.PasswordChangeRequest;
import it.sdc.src.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    public UserSessionDto refreshSession(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return authService.refreshAccessToken(userPrincipal.getRefreshToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSessionDto register(@Valid @RequestBody UserRegistrationRequest request) {
        return authService.register(request);
    }

    @PostMapping("/register/finalize")
    public UserCryptoDto finalizeRegistration(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UserRegistrationFinalizationRequest request
    ) {
        return authService.finalizeRegistration(userPrincipal.getUserId(), request);
    }

    @PostMapping("/me/password")
    public UserSessionDto changePassword(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        return authService.changePassword(userPrincipal.getUserId(), request.password());
    }
}
