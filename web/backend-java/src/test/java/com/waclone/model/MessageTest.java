package com.waclone.model;

import com.waclone.model.Enums.ConversationType;
import com.waclone.model.Enums.MessageStatus;
import com.waclone.model.Enums.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {

    private User sender;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        sender = new User("pera", "Pera", "hes");
        conversation = new Conversation(ConversationType.DIRECT, null, "d:mika:pera");
    }

    @Test
    @DisplayName("konstruktor postavlja oznaku, razgovor, posiljaoca i sadrzaj")
    void constructorSetsFields() {
        Message m = new Message("m1", conversation, sender, "zdravo");

        assertThat(m.getClientId()).isEqualTo("m1");
        assertThat(m.getConversation()).isSameAs(conversation);
        assertThat(m.getSender()).isSameAs(sender);
        assertThat(m.getBody()).isEqualTo("zdravo");
    }

    @Test
    @DisplayName("nova poruka je tekstualna, u stanju SENT i nije obrisana")
    void defaultsAreTextSentAndNotDeleted() {
        Message m = new Message("m1", conversation, sender, "zdravo");

        assertThat(m.getType()).isEqualTo(MessageType.TEXT);
        assertThat(m.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(m.isDeleted()).isFalse();
        assertThat(m.getEditedAt()).isZero();
        assertThat(m.getReplyTo()).isNull();
        assertThat(m.getFileName()).isNull();
    }

    @Test
    @DisplayName("trenutak slanja se postavlja pri stvaranju poruke")
    void sentAtIsSetOnCreation() {
        long before = System.currentTimeMillis();
        assertThat(new Message("m1", conversation, sender, "zdravo").getSentAt())
                .isGreaterThanOrEqualTo(before);
    }

    @Test
    @DisplayName("stanja poruke idu redom PENDING, SENT, DELIVERED, READ")
    void statusOrderMatchesDeliveryFlow() {
        assertThat(MessageStatus.PENDING.ordinal()).isLessThan(MessageStatus.SENT.ordinal());
        assertThat(MessageStatus.SENT.ordinal()).isLessThan(MessageStatus.DELIVERED.ordinal());
        assertThat(MessageStatus.DELIVERED.ordinal()).isLessThan(MessageStatus.READ.ordinal());
    }

    @Test
    @DisplayName("izmena poruke belezi novi sadrzaj i trenutak izmene")
    void editRecordsBodyAndTime() {
        Message m = new Message("m1", conversation, sender, "zdravo");
        m.setBody("izmenjeno");
        m.setEditedAt(1_700_000_000_000L);

        assertThat(m.getBody()).isEqualTo("izmenjeno");
        assertThat(m.getEditedAt()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    @DisplayName("poruka moze da bude odgovor na drugu poruku")
    void messageCanReplyToAnother() {
        Message first = new Message("m1", conversation, sender, "pitanje");
        Message second = new Message("m2", conversation, sender, "odgovor");
        second.setReplyTo(first);

        assertThat(second.getReplyTo()).isSameAs(first);
    }

    @Test
    @DisplayName("poruka sa datotekom cuva zapis o njoj, a ne i njen sadrzaj")
    void fileMessageKeepsOnlyTheRecord() {
        Message m = new Message("m1", conversation, sender, null);
        m.setType(MessageType.FILE);
        m.setFileName("izvestaj.pdf");
        m.setFileSizeBytes(5000L);
        m.setStoragePath("media/abc123");

        assertThat(m.getType()).isEqualTo(MessageType.FILE);
        assertThat(m.getFileName()).isEqualTo("izvestaj.pdf");
        assertThat(m.getFileSizeBytes()).isEqualTo(5000L);
        assertThat(m.getStoragePath()).isEqualTo("media/abc123");
        assertThat(m.getBody()).isNull();
    }
}
