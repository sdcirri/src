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
        check = {@CheckConstraint(
                name = "src_sessions_access_ne_refresh",
                constraint = "access_token != refresh_token"
        )}
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

    @Column(name = "access_token_expires", nullable = false)
    private Instant accessTokenExpires;

    @Column(name = "refresh_token", nullable = false, unique = true)
    private byte[] refreshToken;

    @Column(name = "refresh_token_expires", nullable = false)
    private Instant refreshTokenExpires;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_session_user_id")
    )
    private UserDB user;
}
