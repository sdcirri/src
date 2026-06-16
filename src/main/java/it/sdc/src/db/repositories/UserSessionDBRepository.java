package it.sdc.src.db.repositories;

import it.sdc.src.db.entities.UserSessionDB;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserSessionDBRepository extends JpaRepository<UserSessionDB, UUID> {
    Optional<UserSessionDB> findByUser_IdAndRefreshToken(UUID userId, byte[] refreshToken);

    void deleteAllByUser_Id(UUID userId);
}

