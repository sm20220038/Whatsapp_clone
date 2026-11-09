package com.waclone.model;

import jakarta.persistence.*;

@Entity
@Table(name = "reaction",
       uniqueConstraints = @UniqueConstraint(columnNames = {"messageId", "userId"}))
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reactionId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "messageId")
    private Message message;

    @ManyToOne(optional = false)
    @JoinColumn(name = "userId",
                foreignKey = @ForeignKey(name = "fk_reaction_user",
                        foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users (user_id) ON UPDATE CASCADE"))
    private User user;

    @Column(nullable = false, length = 16)
    private String emoji;

    private long reactedAt;

    protected Reaction() {}

    public Reaction(Message message, User user, String emoji) {
        this.message = message;
        this.user = user;
        this.emoji = emoji;
        this.reactedAt = System.currentTimeMillis();
    }

    public Long getReactionId() { return reactionId; }
    public Message getMessage() { return message; }
    public User getUser() { return user; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String e) { this.emoji = e; }
    public long getReactedAt() { return reactedAt; }
}
