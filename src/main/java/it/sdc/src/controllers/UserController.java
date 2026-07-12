package it.sdc.src.controllers;

import it.sdc.src.auth.UserPrincipal;
import it.sdc.src.dto.ContactCryptoDto;
import it.sdc.src.dto.UserCryptoDto;
import it.sdc.src.dto.UserDto;
import it.sdc.src.dto.requests.accountedits.DisplayNameChangeRequest;
import it.sdc.src.dto.requests.accountedits.UsernameChangeRequest;
import it.sdc.src.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @GetMapping("/search")
    public List<UserDto> searchUsers(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam("q") @NotBlank @Size(min = 3, max = 255) String searchQuery,
            @RequestParam(value = "n", defaultValue = "0") @PositiveOrZero int pageNumber
    ) {
        return userService.searchUsers(searchQuery, pageNumber, userPrincipal.getUserId());
    }

    @GetMapping("/{userId}")
    public UserDto getUserInfo(@PathVariable @NotNull UUID userId) {
        return userService.getUserInfo(userId);
    }

    @PutMapping("/me/display_name")
    public UserDto setDisplayName(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody DisplayNameChangeRequest request
    ) {
        return userService.setDisplayName(userPrincipal.getUserId(), request.displayName());
    }

    @PutMapping("/me/username")
    public UserDto setUsername(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UsernameChangeRequest request
    ) {
        return userService.changeUsername(userPrincipal.getUserId(), request.username());
    }

    @PutMapping("/me/propic")
    public UserDto setProPic(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam("image") MultipartFile image
    ) {
        try {
            return userService.setProPic(userPrincipal.getUserId(), image.getBytes());
        }
        catch (IOException e) {
            throw new RuntimeException("Couldn't process image: ", e);
        }
    }

    @GetMapping("/me/crypto")
    public UserCryptoDto getCryptoSpecs(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return userService.getMyCryptoSpecs(userPrincipal.getUserId());
    }

    @GetMapping("/{contactId}/crypto")
    public ContactCryptoDto getUserCryptoSpecs(@PathVariable @NotNull UUID contactId) {
        return userService.getUserCryptoSpecs(contactId);
    }
}
