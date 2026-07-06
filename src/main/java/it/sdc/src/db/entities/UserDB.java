package it.sdc.src.db.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "src_users")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class UserDB {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter
    @Column(nullable = false, unique = true)
    private String username;

    @Setter
    private String displayName;

    @Setter
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private Instant registrationTimeUTC;

    @Setter
    private byte[] proPic;

    @OneToOne(mappedBy = "userDB", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserCryptoDB crypto;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserSessionDB> activeSessions = new ArrayList<>();
}
