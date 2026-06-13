package it.sdc.src.db.repositories;

import it.sdc.src.db.entities.ChatDB;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatDBRepository extends JpaRepository<ChatDB, UUID> {
}
