package it.sdc.src.db.repositories;

import it.sdc.src.db.entities.UserCryptoDB;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserCryptoDBRepository extends JpaRepository<UserCryptoDB, UUID> {
}
