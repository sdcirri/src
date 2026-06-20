package it.sdc.src.db.repositories;

import it.sdc.src.db.entities.UserDB;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDBRepository extends JpaRepository<UserDB, UUID> {
    Optional<UserDB> findByUsername(String username);

    @Query("""
        SELECT u FROM UserDB u WHERE u.id != :currentUserId
            AND lower(u.username) LIKE concat(:query, '%')
            ORDER BY u.username ASC
    """)
    List<UserDB> searchByUsername(
            @Param("query") String query,
            @Param("currentUserId") UUID currentUserId,
            Pageable pageable
    );

    boolean existsByUsername(String username);
}
