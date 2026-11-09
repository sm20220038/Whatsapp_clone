package com.waclone.service;

import com.waclone.model.Conversation;
import com.waclone.model.Enums.ConversationType;
import com.waclone.model.Enums.MemberRole;
import com.waclone.model.Message;
import com.waclone.model.Participant;
import com.waclone.model.Reaction;
import com.waclone.model.User;
import com.waclone.repo.ConversationRepo;
import com.waclone.repo.MessageRepo;
import com.waclone.repo.ParticipantRepo;
import com.waclone.repo.ReactionRepo;
import com.waclone.repo.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private UserRepo users;
    @Mock private ConversationRepo conversations;
    @Mock private ParticipantRepo participants;
    @Mock private MessageRepo messages;
    @Mock private ReactionRepo reactions;

    private ChatService chat;

    private User pera;
    private User mika;
    private Conversation group;

    @BeforeEach
    void setUp() {
        chat = new ChatService(users, conversations, participants, messages, reactions);
        pera = new User("pera", "Pera", "hes");
        mika = new User("mika", "Mika", "hes");
        group = new Conversation(ConversationType.GROUP, "Ekipa", "g:1");
    }

    @Test
    @DisplayName("trazeni korisnik se vraca iz repozitorijuma")
    void userIsReturnedFromRepository() {
        when(users.findByUsername("pera")).thenReturn(Optional.of(pera));

        assertThat(chat.user("pera")).isSameAs(pera);
    }

    @Test
    @DisplayName("za nepostojeceg korisnika se prijavljuje greska 404")
    void unknownUserRaisesNotFound() {
        when(users.findByUsername("niko")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chat.user("niko"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no such user");

        ResponseStatusException greska = (ResponseStatusException)
                org.assertj.core.api.Assertions.catchThrowable(() -> chat.user("niko"));
        assertThat(greska.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("postojeci razgovor dvoje korisnika se pronalazi, a ne pravi ponovo")
    void existingDirectConversationIsReused() {
        Conversation existing = new Conversation(ConversationType.DIRECT, null, "d:mika:pera");
        when(conversations.findByExternalId("d:mika:pera")).thenReturn(Optional.of(existing));

        assertThat(chat.direct("pera", "mika")).isSameAs(existing);
        verify(conversations, never()).save(any());
        verify(participants, never()).save(any());
    }

    @Test
    @DisplayName("novi razgovor dvoje korisnika dobija oba ucesnika")
    void newDirectConversationGetsBothParticipants() {
        when(conversations.findByExternalId("d:mika:pera")).thenReturn(Optional.empty());
        when(conversations.save(any(Conversation.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findByUsername("pera")).thenReturn(Optional.of(pera));
        when(users.findByUsername("mika")).thenReturn(Optional.of(mika));

        Conversation c = chat.direct("pera", "mika");

        assertThat(c.getType()).isEqualTo(ConversationType.DIRECT);
        assertThat(c.getExternalId()).isEqualTo("d:mika:pera");

        ArgumentCaptor<Participant> saved = ArgumentCaptor.forClass(Participant.class);
        verify(participants, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(p -> p.getUser().getUsername())
                .containsExactlyInAnyOrder("pera", "mika");
        assertThat(saved.getAllValues())
                .allMatch(p -> p.getRole() == MemberRole.MEMBER);
    }

    @Test
    @DisplayName("poruka sa vec upisanom oznakom se ne upisuje drugi put")
    void recordIsIdempotentForSameClientId() {
        Message existing = new Message("m1", group, pera, "zdravo");
        when(messages.findFirstByConversationAndClientId(group, "m1"))
                .thenReturn(Optional.of(existing));

        assertThat(chat.record(group, "pera", "m1", "zdravo")).isSameAs(existing);
        verify(messages, never()).save(any());
    }

    @Test
    @DisplayName("poruka sa novom oznakom se upisuje")
    void recordSavesNewMessage() {
        when(messages.findFirstByConversationAndClientId(group, "m2")).thenReturn(Optional.empty());
        when(users.findByUsername("pera")).thenReturn(Optional.of(pera));
        when(messages.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));

        Message m = chat.record(group, "pera", "m2", "nova poruka");

        assertThat(m.getClientId()).isEqualTo("m2");
        assertThat(m.getBody()).isEqualTo("nova poruka");
        assertThat(m.getSender()).isSameAs(pera);
        verify(messages).save(any(Message.class));
    }

    @Test
    @DisplayName("strana istorije se okrece, da poruke idu hronoloski")
    void historyPageIsReversedIntoChronologicalOrder() {
        Message stara = new Message("m1", group, pera, "prva");
        Message srednja = new Message("m2", group, pera, "druga");
        Message nova = new Message("m3", group, pera, "treca");
        when(messages.findByConversationOrderBySentAtDesc(eq(group), any(Pageable.class)))
                .thenReturn(List.of(nova, srednja, stara));

        assertThat(chat.historyPage(group, null, 50))
                .extracting(Message::getBody)
                .containsExactly("prva", "druga", "treca");
    }

    @Test
    @DisplayName("kada je zadata granica vremena, cita se starija strana istorije")
    void historyPageBeforeUsesTheBoundedQuery() {
        when(messages.findByConversationAndSentAtLessThanOrderBySentAtDesc(
                eq(group), eq(1_700_000_000_000L), any(Pageable.class))).thenReturn(List.of());

        assertThat(chat.historyPage(group, 1_700_000_000_000L, 50)).isEmpty();
        verify(messages, never()).findByConversationOrderBySentAtDesc(any(), any());
    }

    @Test
    @DisplayName("trazeni broj poruka se ogranicava na najvise 200 i najmanje 1")
    void historyPageLimitIsClamped() {
        when(messages.findByConversationOrderBySentAtDesc(eq(group), any(Pageable.class)))
                .thenReturn(List.of());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);

        chat.historyPage(group, null, 1000);
        chat.historyPage(group, null, 0);
        chat.historyPage(group, null, -5);
        chat.historyPage(group, null, 30);

        verify(messages, times(4)).findByConversationOrderBySentAtDesc(eq(group), page.capture());
        assertThat(page.getAllValues())
                .extracting(Pageable::getPageSize)
                .containsExactly(200, 1, 1, 30);
    }

    @Test
    @DisplayName("clan razgovora se prepoznaje, a onaj ko nije clan ne")
    void membershipIsCheckedAgainstParticipants() {
        when(participants.findByConversation(group))
                .thenReturn(List.of(new Participant(pera, group, MemberRole.OWNER)));

        assertThat(chat.isMember(group, "pera")).isTrue();
        assertThat(chat.isMember(group, "mika")).isFalse();
    }

    @Test
    @DisplayName("uloge clanova se vracaju po korisnickom imenu")
    void rolesOfReturnsRolePerUsername() {
        when(participants.findByConversation(group)).thenReturn(List.of(
                new Participant(pera, group, MemberRole.OWNER),
                new Participant(mika, group, MemberRole.MEMBER)));

        assertThat(chat.rolesOf(group))
                .containsEntry("pera", MemberRole.OWNER)
                .containsEntry("mika", MemberRole.MEMBER);
    }

    @Test
    @DisplayName("clanovi grupe dobijaju uloge vlasnika, administratora i clana")
    void syncGroupAssignsRoles() {
        User zoran = new User("zoran", "Zoran", "hes");
        when(conversations.findByExternalId("g:1")).thenReturn(Optional.of(group));
        when(participants.findByConversation(group)).thenReturn(List.of());
        when(users.findByUsername("pera")).thenReturn(Optional.of(pera));
        when(users.findByUsername("mika")).thenReturn(Optional.of(mika));
        when(users.findByUsername("zoran")).thenReturn(Optional.of(zoran));

        chat.syncGroup("g:1", "Ekipa", List.of("pera", "mika", "zoran"), List.of("mika"), "pera");

        ArgumentCaptor<Participant> saved = ArgumentCaptor.forClass(Participant.class);
        verify(participants, times(3)).save(saved.capture());

        Map<String, MemberRole> roles = new HashMap<>();
        for (Participant p : saved.getAllValues()) {
            roles.put(p.getUser().getUsername(), p.getRole());
        }
        assertThat(roles)
                .containsEntry("pera", MemberRole.OWNER)
                .containsEntry("mika", MemberRole.ADMIN)
                .containsEntry("zoran", MemberRole.MEMBER);
    }

    @Test
    @DisplayName("clan koga vise nema na spisku uklanja se iz grupe")
    void syncGroupRemovesMemberNoLongerListed() {
        Participant mikaInGroup = new Participant(mika, group, MemberRole.MEMBER);
        Participant peraInGroup = new Participant(pera, group, MemberRole.OWNER);
        when(conversations.findByExternalId("g:1")).thenReturn(Optional.of(group));
        when(participants.findByConversation(group)).thenReturn(List.of(peraInGroup, mikaInGroup));

        chat.syncGroup("g:1", "Ekipa", List.of("pera"), List.of(), "pera");

        verify(participants).delete(mikaInGroup);
        verify(participants, never()).delete(peraInGroup);
    }

    @Test
    @DisplayName("ponovljeno uskladjivanje sa istim podacima ne menja nista")
    void syncGroupIsIdempotent() {
        when(conversations.findByExternalId("g:1")).thenReturn(Optional.of(group));
        when(participants.findByConversation(group))
                .thenReturn(List.of(new Participant(pera, group, MemberRole.OWNER)));

        chat.syncGroup("g:1", "Ekipa", List.of("pera"), List.of(), "pera");

        verify(participants, never()).save(any());
        verify(participants, never()).delete(any());
        verify(conversations, never()).save(any());
    }

    @Test
    @DisplayName("promenjen naziv grupe se upisuje")
    void syncGroupSavesChangedTitle() {
        when(conversations.findByExternalId("g:1")).thenReturn(Optional.of(group));

        chat.syncGroup("g:1", "Nova ekipa", List.of(), List.of(), "pera");

        assertThat(group.getTitle()).isEqualTo("Nova ekipa");
        verify(conversations).save(group);
    }

    @Test
    @DisplayName("grupa koja jos ne postoji se pravi")
    void syncGroupCreatesMissingConversation() {
        when(conversations.findByExternalId("g:2")).thenReturn(Optional.empty());
        when(conversations.save(any(Conversation.class))).thenAnswer(i -> i.getArgument(0));

        Conversation c = chat.syncGroup("g:2", "Nova", List.of(), List.of(), "pera");

        assertThat(c.getType()).isEqualTo(ConversationType.GROUP);
        assertThat(c.getExternalId()).isEqualTo("g:2");
    }

    @Test
    @DisplayName("kada dva klijenta istovremeno upisu grupu, upis se ponavlja i uspeva")
    void syncGroupSafelyRetriesAfterUniqueKeyClash() {
        when(conversations.findByExternalId("g:1"))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenReturn(Optional.of(group));

        assertThat(chat.syncGroupSafely("g:1", "Ekipa", List.of(), List.of(), "pera"))
                .isSameAs(group);
        verify(conversations, times(2)).findByExternalId("g:1");
    }

    @Test
    @DisplayName("prva reakcija na poruku se upisuje")
    void reactSavesNewReaction() {
        Message m = new Message("m1", group, pera, "zdravo");
        when(messages.findFirstByConversationAndClientId(group, "m1")).thenReturn(Optional.of(m));
        when(users.findByUsername("mika")).thenReturn(Optional.of(mika));
        when(reactions.findByMessageAndUser(m, mika)).thenReturn(Optional.empty());

        chat.react(group, "mika", "m1", "OK");

        ArgumentCaptor<Reaction> saved = ArgumentCaptor.forClass(Reaction.class);
        verify(reactions).save(saved.capture());
        assertThat(saved.getValue().getEmoji()).isEqualTo("OK");
        assertThat(saved.getValue().getUser()).isSameAs(mika);
    }

    @Test
    @DisplayName("ista reakcija po drugi put se uklanja")
    void reactRemovesReactionWhenRepeated() {
        Message m = new Message("m1", group, pera, "zdravo");
        Reaction existing = new Reaction(m, mika, "OK");
        when(messages.findFirstByConversationAndClientId(group, "m1")).thenReturn(Optional.of(m));
        when(users.findByUsername("mika")).thenReturn(Optional.of(mika));
        when(reactions.findByMessageAndUser(m, mika)).thenReturn(Optional.of(existing));

        chat.react(group, "mika", "m1", "OK");

        verify(reactions).delete(existing);
        verify(reactions, never()).save(any());
    }

    @Test
    @DisplayName("druga reakcija istog korisnika menja postojecu")
    void reactReplacesReactionWhenDifferent() {
        Message m = new Message("m1", group, pera, "zdravo");
        Reaction existing = new Reaction(m, mika, "OK");
        when(messages.findFirstByConversationAndClientId(group, "m1")).thenReturn(Optional.of(m));
        when(users.findByUsername("mika")).thenReturn(Optional.of(mika));
        when(reactions.findByMessageAndUser(m, mika)).thenReturn(Optional.of(existing));

        chat.react(group, "mika", "m1", "NE");

        assertThat(existing.getEmoji()).isEqualTo("NE");
        verify(reactions).save(existing);
        verify(reactions, never()).delete(any(Reaction.class));
    }

    @Test
    @DisplayName("reakcija na poruku koja ne postoji prijavljuje gresku 404")
    void reactOnMissingMessageRaisesNotFound() {
        when(messages.findFirstByConversationAndClientId(group, "nema")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chat.react(group, "mika", "nema", "OK"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no such message");
        verify(users, never()).findByUsername(anyString());
    }

    @Test
    @DisplayName("spisak grupa preskace razgovore dvoje korisnika i prazne grupe")
    void allGroupsSkipsDirectAndEmptyConversations() {
        Conversation direct = new Conversation(ConversationType.DIRECT, null, "d:mika:pera");
        Conversation prazna = new Conversation(ConversationType.GROUP, "Prazna", "g:9");
        when(conversations.findAll()).thenReturn(List.of(direct, prazna, group));
        when(participants.findByConversation(prazna)).thenReturn(List.of());
        when(participants.findByConversation(group)).thenReturn(List.of(
                new Participant(pera, group, MemberRole.OWNER),
                new Participant(mika, group, MemberRole.ADMIN)));

        List<Map<String, Object>> groups = chat.allGroups();

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).containsEntry("id", "g:1")
                                 .containsEntry("name", "Ekipa")
                                 .containsEntry("owner", "pera");
        assertThat(groups.get(0).get("admins")).isEqualTo(List.of("pera", "mika"));
    }
}
