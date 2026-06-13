package it.sdc.src.db.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "src_users_crypto")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class UserCryptoDB {
    @Id
    @Column
    private UUID id;

    @Column(nullable = false)
    private byte[] kekSalt;

    @Column(nullable = false)
    private byte[] ivEd25519;

    @Column(nullable = false)
    private byte[] privateEd25519;

    @Column(nullable = false)
    private byte[] publicEd25519;

    @Column(nullable = false)
    private byte[] ivX25519;

    @Column(nullable = false)
    private byte[] privateX25519;

    @Column(nullable = false)
    private byte[] publicX25519;

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private UserDB userDB;
}
