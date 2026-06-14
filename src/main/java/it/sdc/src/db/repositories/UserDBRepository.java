package it.sdc.src.db.repositories;

import it.sdc.src.db.entities.UserDB;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserDBRepository extends JpaRepository<UserDB, UUID> {
    Optional<UserDB> findByUsername(String username);

    Optional<UserDB> findByUsernameAndPasswordHash(String username, String passwordHash);

    boolean existsByUsername(String username);
}
