package com.waclone.model;

import com.waclone.model.Enums.Role;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true, length = 40)
    private String username;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 200)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.USER;

    private long lastSeen;

    private boolean online;

    @Column(length = 64)
    private String avatarId;

    protected User() {}

    public User(String username, String displayName, String passwordHash) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String d) { this.displayName = d; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String h) { this.passwordHash = h; }
    public Role getRole() { return role; }
    public void setRole(Role r) { this.role = r; }
    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long t) { this.lastSeen = t; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean o) { this.online = o; }
    public String getAvatarId() { return avatarId; }
    public void setAvatarId(String a) { this.avatarId = a; }
}
