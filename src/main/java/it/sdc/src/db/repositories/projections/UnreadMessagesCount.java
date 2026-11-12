package it.sdc.src.db.repositories.projections;

import java.util.UUID;

public interface UnreadMessagesCount {
    UUID getChatId();
    Long getUnreadCount();
}
