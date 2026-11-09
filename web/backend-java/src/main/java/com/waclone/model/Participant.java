package com.waclone.model;

import com.waclone.model.Enums.MemberRole;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "participant")
@IdClass(Participant.Key.class)
public class Participant {

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "userId",
                foreignKey = @ForeignKey(name = "fk_participant_user",
                        foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users (user_id) ON UPDATE CASCADE"))
    private User user;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "conversationId",
                foreignKey = @ForeignKey(name = "fk_participant_conversation",
                        foreignKeyDefinition = "FOREIGN KEY (conversation_id) REFERENCES conversation (conversation_id) ON UPDATE CASCADE"))
    private Conversation conversation;

    private long joinedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MemberRole role = MemberRole.MEMBER;

    private int unreadCount;

    protected Participant() {}

    public Participant(User user, Conversation conversation, MemberRole role) {
        this.user = user;
        this.conversation = conversation;
        this.role = role;
        this.joinedAt = System.currentTimeMillis();
    }

    public User getUser() { return user; }
    public Conversation getConversation() { return conversation; }
    public long getJoinedAt() { return joinedAt; }
    public MemberRole getRole() { return role; }
    public void setRole(MemberRole r) { this.role = r; }
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int c) { this.unreadCount = c; }

    public static class Key implements Serializable {
        private Long user;
        private Long conversation;

        public Key() {}

        public Key(Long user, Long conversation) {
            this.user = user;
            this.conversation = conversation;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(user, k.user) && Objects.equals(conversation, k.conversation);
        }

        @Override public int hashCode() { return Objects.hash(user, conversation); }
    }
}
