package it.sdc.src.controllers;

import it.sdc.src.dto.ChatDto;
import it.sdc.src.dto.MessageDto;
import it.sdc.src.dto.requests.accountedits.MessageRequest;
import it.sdc.src.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
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
    public List<ChatDto> getMyChats() {
        // TODO: Bearer auth
        return chatService.getChats(UUID.randomUUID());
    }

    @GetMapping("/{contactId}")
    public List<MessageDto> getMessageHistory(@PathVariable UUID contactId) {
        // TODO: Bearer auth
        return chatService.getMessages(UUID.randomUUID(), contactId);
    }

    @PostMapping("/{contactId}")
    public ChatDto sendMessage(@PathVariable @NotNull UUID contactId, @Valid @RequestBody MessageRequest request) {
        // TODO: Bearer auth
        return chatService.sendMessage(UUID.randomUUID(), contactId, request);
    }
}
