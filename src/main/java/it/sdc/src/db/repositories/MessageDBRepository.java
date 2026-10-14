package it.sdc.src.db.repositories;

import it.sdc.src.db.entities.MessageDB;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
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

    Optional<MessageDB> findByIdAndChatId(UUID id, UUID chatId);

    Optional<MessageDB> findFirstByChatIdOrderByTimestampDescIdDesc(UUID chatId);
}
