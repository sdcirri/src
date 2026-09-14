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
    private UUID id;

    @Column(name = "kek_salt", nullable = false)
    @Setter
    private byte[] kekSalt;

    @Column(name = "iv_ed25519", nullable = false)
    @Setter
    private byte[] ivEd25519;

    @Column(name = "private_ed25519", nullable = false)
    @Setter
    private byte[] privateEd25519;

    @Column(name = "public_ed25519", nullable = false)
    private byte[] publicEd25519;

    @Column(name = "iv_x25519", nullable = false)
    @Setter
    private byte[] ivX25519;

    @Column(name = "private_x25519", nullable = false)
    @Setter
    private byte[] privateX25519;

    @Column(name = "public_x25519", nullable = false)
    private byte[] publicX25519;

    @OneToOne
    @MapsId
    @JoinColumn(name = "id", foreignKey = @ForeignKey(name = "fk_users_crypto_user"))
    private UserDB userDB;
}
