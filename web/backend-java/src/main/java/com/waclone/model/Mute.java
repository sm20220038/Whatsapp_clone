package com.waclone.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "mute")
@IdClass(Mute.Key.class)
public class Mute {

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "muterId",
                foreignKey = @ForeignKey(name = "fk_mute_muter",
                        foreignKeyDefinition = "FOREIGN KEY (muter_id) REFERENCES users (user_id) ON UPDATE CASCADE"))
    private User muter;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "mutedId",
                foreignKey = @ForeignKey(name = "fk_mute_muted",
                        foreignKeyDefinition = "FOREIGN KEY (muted_id) REFERENCES users (user_id) ON UPDATE CASCADE"))
    private User muted;

    private long mutedAt;

    protected Mute() {}

    public Mute(User muter, User muted) {
        this.muter = muter;
        this.muted = muted;
        this.mutedAt = System.currentTimeMillis();
    }

    public User getMuter() { return muter; }
    public User getMuted() { return muted; }
    public long getMutedAt() { return mutedAt; }

    public static class Key implements Serializable {
        private Long muter;
        private Long muted;

        public Key() {}

        public Key(Long muter, Long muted) {
            this.muter = muter;
            this.muted = muted;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(muter, k.muter) && Objects.equals(muted, k.muted);
        }

        @Override public int hashCode() { return Objects.hash(muter, muted); }
    }
}
