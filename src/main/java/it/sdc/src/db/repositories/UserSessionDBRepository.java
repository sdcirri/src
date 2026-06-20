package it.sdc.src.db.repositories;

import it.sdc.src.db.entities.UserSessionDB;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionDBRepository extends JpaRepository<UserSessionDB, UUID> {
    @Query("""
        SELECT s FROM UserSessionDB s
        JOIN FETCH s.user
        WHERE s.accessToken = :token
    """)
    Optional<UserSessionDB> findByAccessToken(@Param("token") byte[] token);

    @Query("""
        SELECT s FROM UserSessionDB s
        JOIN FETCH s.user
        WHERE s.refreshToken = :token
    """)
    Optional<UserSessionDB> findByRefreshToken(@Param("token") byte[] token);

    List<UserSessionDB> findAllByUser_Id(UUID userId);

    void deleteAllByUser_Id(UUID userId);
}
