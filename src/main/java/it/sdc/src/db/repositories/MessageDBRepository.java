package it.sdc.src.db.repositories;

import it.sdc.src.db.entities.MessageDB;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageDBRepository extends JpaRepository<MessageDB, UUID> {
    List<MessageDB> findByChatId(UUID chatId);
}
