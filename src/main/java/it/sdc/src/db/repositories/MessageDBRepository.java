package it.sdc.src.db.repositories;

import it.sdc.src.db.entities.MessageDB;
import it.sdc.src.db.repositories.projections.UnreadMessagesCount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageDBRepository extends JpaRepository<MessageDB, UUID> {
    // First page only!
    List<MessageDB> findByChatIdOrderByTimestampDescIdDesc(UUID chatId, Pageable pageable);

    @Query(value = """
        SELECT * FROM src_messages
        WHERE chat_id = :chatId AND (timestamp, id) < (:beforeTimestamp, :beforeId)
        ORDER BY timestamp DESC, id DESC
    """, nativeQuery = true)
    List<MessageDB> findBefore(UUID chatId, Instant beforeTimestamp, UUID beforeId, Pageable pageable);

    @Query(value = """
        SELECT chat_id AS "chatId", COUNT(*) AS "unreadCount" FROM src_messages
           WHERE chat_id IN (:chatIds) AND sender_id != :userId AND read = false
           GROUP BY chat_id
    """, nativeQuery = true)
    List<UnreadMessagesCount> countUnreadByChatIds(Collection<UUID> chatIds, UUID userId);

    @Query(value = """
        UPDATE src_messages AS m SET read = true FROM src_messages AS boundary
            WHERE boundary.id = :untilMessageId
                AND boundary.chat_id = :chatId AND m.chat_id = :chatId AND m.sender_id != :userId
                AND m.timestamp >= boundary.timestamp AND m.read = false
    """, nativeQuery = true)
    @Modifying
    void readAllMessagesAfterId(UUID chatId, UUID userId, UUID untilMessageId);

    Optional<MessageDB> findByIdAndChatId(UUID id, UUID chatId);

    Optional<MessageDB> findFirstByChatIdOrderByTimestampDescIdDesc(UUID chatId);
}
