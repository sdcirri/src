package it.sdc.src.db.repositories;

import it.sdc.src.db.entities.ChatDB;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ChatDBRepository extends JpaRepository<ChatDB, UUID> {
    Optional<ChatDB> findByUser1_IdAndUser2_Id(UUID user1Id, UUID user2Id);
}
