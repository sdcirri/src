package it.sdc.src.db.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "src_chats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_users",
                columnNames = {"user1_id", "user2_id"}
        ),
        check = {
                @CheckConstraint(
                        name = "ck_chat_user_order_and_not_self",
                        constraint = "user1_id < user2_id"
                )
        }
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class ChatDB {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MessageDB> messages = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user1_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_chat_user1_id")
    )
    private UserDB user1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user2_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_chat_user2_id")
    )
    private UserDB user2;
}
