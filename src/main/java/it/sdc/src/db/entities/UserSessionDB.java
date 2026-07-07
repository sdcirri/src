package it.sdc.src.db.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "src_sessions",
        check = {@CheckConstraint(constraint = "access_token != refresh_token")}
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSessionDB {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "access_token", nullable = false, unique = true)
    private byte[] accessToken;

    @Column(nullable = false)
    private Instant accessTokenExpires;

    @Column(name = "refresh_token", nullable = false, unique = true)
    private byte[] refreshToken;

    @Column(nullable = false)
    private Instant refreshTokenExpires;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserDB user;
}
