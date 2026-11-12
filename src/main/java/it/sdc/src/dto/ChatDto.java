package it.sdc.src.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record ChatDto(
        @NotNull UUID chatId,
        @NotNull UUID contactId,
        @Valid MessageDto lastMessage,
        @PositiveOrZero long unreadCount
) {}
