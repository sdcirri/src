package it.sdc.src.db.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "src_sessions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSessionDB {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private byte[] accessToken;

    @Column(nullable = false)
    private Instant accessTokenExpires;

    @Column(nullable = false, unique = true)
    private byte[] refreshToken;

    @Column(nullable = false)
    private Instant refreshTokenExpires;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private UserDB user;
}
