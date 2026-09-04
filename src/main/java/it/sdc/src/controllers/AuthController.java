package it.sdc.src.controllers;

import it.sdc.src.auth.UserPrincipal;
import it.sdc.src.dto.UserCryptoDto;
import it.sdc.src.dto.UserSessionDto;
import it.sdc.src.dto.requests.*;
import it.sdc.src.dto.requests.accountedits.PasswordChangeRequest;
import it.sdc.src.service.AuthCookieService;
import it.sdc.src.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/auth")
public class AuthController {
    private final AuthCookieService authCookieService;
    private final AuthService authService;

    private ResponseEntity<Void> sessionResponse(UserSessionDto session, HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, authCookieService.buildAccessCookie(session.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, authCookieService.buildRefreshCookie(session.refreshToken()).toString())
                .build();
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request) {
        UserSessionDto session = authService.login(request.username(), request.password());
        return sessionResponse(session, HttpStatus.NO_CONTENT);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refreshSession(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        UserSessionDto session = authService.refreshAccessToken(userPrincipal.getRefreshToken());
        return sessionResponse(session, HttpStatus.NO_CONTENT);
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody UserRegistrationRequest request) {
        UserSessionDto session = authService.register(request);
        return sessionResponse(session, HttpStatus.CREATED);
    }

    @PostMapping("/register/finalize")
    public UserCryptoDto finalizeRegistration(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UserRegistrationFinalizationRequest request
    ) {
        return authService.finalizeRegistration(userPrincipal.getUserId(), request);
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        UserSessionDto session = authService.changePassword(userPrincipal.getUserId(), request);
        return sessionResponse(session, HttpStatus.NO_CONTENT);
    }
}
