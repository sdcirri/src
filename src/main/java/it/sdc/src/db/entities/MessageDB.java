package it.sdc.src.db.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "src_messages")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class MessageDB {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private byte[] iv;

    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private Instant timestamp;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "chat_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_message_chat_id")
    )
    private ChatDB chat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "sender_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_message_sender_id")
    )
    private UserDB sender;
}
