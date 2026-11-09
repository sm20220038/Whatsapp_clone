package com.waclone.model;

import com.waclone.model.Enums.ConversationType;
import com.waclone.model.Enums.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParticipantTest {

    private final User user = new User("pera", "Pera", "hes");
    private final Conversation group = new Conversation(ConversationType.GROUP, "Ekipa", "g:1");

    @Test
    @DisplayName("konstruktor povezuje korisnika i razgovor i belezi trenutak ulaska")
    void constructorLinksUserAndConversation() {
        long before = System.currentTimeMillis();
        Participant p = new Participant(user, group, MemberRole.OWNER);

        assertThat(p.getUser()).isSameAs(user);
        assertThat(p.getConversation()).isSameAs(group);
        assertThat(p.getRole()).isEqualTo(MemberRole.OWNER);
        assertThat(p.getJoinedAt()).isGreaterThanOrEqualTo(before);
        assertThat(p.getUnreadCount()).isZero();
    }

    @Test
    @DisplayName("uloga clana u grupi moze da se promeni")
    void roleCanBeChanged() {
        Participant p = new Participant(user, group, MemberRole.MEMBER);
        p.setRole(MemberRole.ADMIN);
        assertThat(p.getRole()).isEqualTo(MemberRole.ADMIN);
    }

    @Test
    @DisplayName("slozeni kljuc je jednak kada su isti i korisnik i razgovor")
    void compositeKeyEqualsOnBothParts() {
        assertThat(new Participant.Key(1L, 2L)).isEqualTo(new Participant.Key(1L, 2L));
        assertThat(new Participant.Key(1L, 2L)).hasSameHashCodeAs(new Participant.Key(1L, 2L));
    }

    @Test
    @DisplayName("slozeni kljuc nije jednak kada se razlikuje bilo koji deo")
    void compositeKeyDiffersOnEitherPart() {
        assertThat(new Participant.Key(1L, 2L)).isNotEqualTo(new Participant.Key(1L, 3L));
        assertThat(new Participant.Key(1L, 2L)).isNotEqualTo(new Participant.Key(9L, 2L));
        assertThat(new Participant.Key(1L, 2L)).isNotEqualTo("nije kljuc");
    }
}
