package it.sdc.src.db.repositories;

import it.sdc.src.db.entities.UserDB;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserDBRepository extends JpaRepository<UserDB, UUID> {
}
