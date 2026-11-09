package com.waclone.model;

import com.waclone.model.Enums.MessageStatus;
import com.waclone.model.Enums.MessageType;
import jakarta.persistence.*;

@Entity
@Table(name = "message", indexes = {
        @Index(name = "idx_msg_conv", columnList = "conversationId,sentAt"),
        @Index(name = "idx_msg_client", columnList = "clientId")
})
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    @Column(length = 64)
    private String clientId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "conversationId",
                foreignKey = @ForeignKey(name = "fk_message_conversation",
                        foreignKeyDefinition = "FOREIGN KEY (conversation_id) REFERENCES conversation (conversation_id) ON UPDATE CASCADE"))
    private Conversation conversation;

    @ManyToOne(optional = false)
    @JoinColumn(name = "senderId",
                foreignKey = @ForeignKey(name = "fk_message_sender",
                        foreignKeyDefinition = "FOREIGN KEY (sender_id) REFERENCES users (user_id) ON UPDATE CASCADE"))
    private User sender;

    @ManyToOne
    @JoinColumn(name = "replyToId")
    private Message replyTo;

    @Lob
    @Column(length = 4000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageType type = MessageType.TEXT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageStatus status = MessageStatus.SENT;

    private boolean encrypted;

    private long sentAt;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long editedAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted;

    @Column(length = 255)
    private String fileName;
    private Long fileSizeBytes;
    @Column(length = 300)
    private String storagePath;
    @Column(length = 64)
    private String sha256;

    protected Message() {}

    public Message(String clientId, Conversation conversation, User sender, String body) {
        this.clientId = clientId;
        this.conversation = conversation;
        this.sender = sender;
        this.body = body;
        this.sentAt = System.currentTimeMillis();
    }

    public Long getMessageId() { return messageId; }
    public String getClientId() { return clientId; }
    public Conversation getConversation() { return conversation; }
    public User getSender() { return sender; }
    public Message getReplyTo() { return replyTo; }
    public void setReplyTo(Message m) { this.replyTo = m; }
    public String getBody() { return body; }
    public void setBody(String b) { this.body = b; }
    public MessageType getType() { return type; }
    public void setType(MessageType t) { this.type = t; }
    public MessageStatus getStatus() { return status; }
    public void setStatus(MessageStatus s) { this.status = s; }
    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean e) { this.encrypted = e; }
    public long getSentAt() { return sentAt; }
    public void setSentAt(long t) { this.sentAt = t; }
    public long getEditedAt() { return editedAt; }
    public void setEditedAt(long t) { this.editedAt = t; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean d) { this.deleted = d; }
    public String getFileName() { return fileName; }
    public void setFileName(String f) { this.fileName = f; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long s) { this.fileSizeBytes = s; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String p) { this.storagePath = p; }
    public String getSha256() { return sha256; }
    public void setSha256(String s) { this.sha256 = s; }
}
