package it.sdc.src.controllers;

import it.sdc.src.auth.UserPrincipal;
import it.sdc.src.dto.ChatDto;
import it.sdc.src.dto.MessageDto;
import it.sdc.src.dto.requests.MessageRequest;
import it.sdc.src.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/chats")
public class ChatController {
    private final ChatService chatService;

    @GetMapping
    public List<ChatDto> getMyChats(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return chatService.getChats(userPrincipal.getUserId());
    }

    @GetMapping("/{contactId}")
    public List<MessageDto> getMessageHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID contactId,
            @RequestParam("n") @Min(1) @Max(20) Integer pageSize,
            @RequestParam("p") @PositiveOrZero Integer pageNumber
    ) {
        return chatService.getMessages(userPrincipal.getUserId(), contactId, pageNumber, pageSize);
    }

    @PostMapping("/{contactId}")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto sendMessage(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID contactId,
            @Valid @RequestBody MessageRequest request
    ) {
        return chatService.sendMessage(userPrincipal.getUserId(), contactId, request);
    }
}
