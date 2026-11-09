package com.waclone.model;

import com.waclone.model.Enums.ConversationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTest {

    @Test
    @DisplayName("konstruktor postavlja vrstu, naziv, spoljnu oznaku i trenutak nastanka")
    void constructorSetsFields() {
        long before = System.currentTimeMillis();
        Conversation c = new Conversation(ConversationType.GROUP, "Ekipa", "g:123");

        assertThat(c.getType()).isEqualTo(ConversationType.GROUP);
        assertThat(c.getTitle()).isEqualTo("Ekipa");
        assertThat(c.getExternalId()).isEqualTo("g:123");
        assertThat(c.getCreatedAt()).isGreaterThanOrEqualTo(before);
    }

    @Test
    @DisplayName("oznaka razgovora dvoje korisnika ne zavisi od redosleda imena")
    void directKeyIsTheSameInBothDirections() {
        assertThat(Conversation.directKey("pera", "mika"))
                .isEqualTo(Conversation.directKey("mika", "pera"));
    }

    @Test
    @DisplayName("oznaka razgovora dvoje korisnika slaze imena po abecedi")
    void directKeyIsSorted() {
        assertThat(Conversation.directKey("pera", "mika")).isEqualTo("d:mika:pera");
        assertThat(Conversation.directKey("ana", "zoran")).isEqualTo("d:ana:zoran");
    }

    @Test
    @DisplayName("razliciti sagovornici daju razlicite oznake razgovora")
    void differentPeersGiveDifferentKeys() {
        assertThat(Conversation.directKey("pera", "mika"))
                .isNotEqualTo(Conversation.directKey("pera", "zoran"));
    }

    @Test
    @DisplayName("naziv grupe moze naknadno da se promeni")
    void titleCanBeRenamed() {
        Conversation c = new Conversation(ConversationType.GROUP, "Ekipa", "g:123");
        c.setTitle("Nova ekipa");
        assertThat(c.getTitle()).isEqualTo("Nova ekipa");
    }
}
