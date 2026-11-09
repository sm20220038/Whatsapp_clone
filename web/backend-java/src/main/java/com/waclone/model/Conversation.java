package com.waclone.model;

import com.waclone.model.Enums.ConversationType;
import jakarta.persistence.*;

@Entity
@Table(name = "conversation",
       uniqueConstraints = @UniqueConstraint(columnNames = "externalId"))
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConversationType type;

    @Column(length = 120)
    private String title;

    @Column(nullable = false, length = 120)
    private String externalId;

    private long createdAt;

    protected Conversation() {}

    public Conversation(ConversationType type, String title, String externalId) {
        this.type = type;
        this.title = title;
        this.externalId = externalId;
        this.createdAt = System.currentTimeMillis();
    }

    public Long getConversationId() { return conversationId; }
    public ConversationType getType() { return type; }
    public String getTitle() { return title; }
    public void setTitle(String t) { this.title = t; }
    public String getExternalId() { return externalId; }
    public long getCreatedAt() { return createdAt; }

    public static String directKey(String a, String b) {
        return a.compareTo(b) <= 0 ? "d:" + a + ":" + b : "d:" + b + ":" + a;
    }
}
