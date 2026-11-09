package com.waclone.service;

import com.waclone.model.*;
import com.waclone.model.Enums.ConversationType;
import com.waclone.model.Enums.MemberRole;
import com.waclone.repo.*;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final UserRepo users;
    private final ConversationRepo conversations;
    private final ParticipantRepo participants;
    private final MessageRepo messages;
    private final ReactionRepo reactions;

    public ChatService(UserRepo users, ConversationRepo conversations,
                       ParticipantRepo participants, MessageRepo messages,
                       ReactionRepo reactions) {
        this.users = users;
        this.conversations = conversations;
        this.participants = participants;
        this.messages = messages;
        this.reactions = reactions;
    }

    public User user(String username) {
        return users.findByUsername(username).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such user: " + username));
    }

    @Transactional
    public Conversation direct(String a, String b) {
        String key = Conversation.directKey(a, b);
        return conversations.findByExternalId(key).orElseGet(() -> {
            Conversation c = conversations.save(
                    new Conversation(ConversationType.DIRECT, null, key));
            join(c, user(a), MemberRole.MEMBER);
            join(c, user(b), MemberRole.MEMBER);
            return c;
        });
    }

    public Conversation group(String groupId, String name, List<String> members, String owner) {
        return syncGroupSafely(groupId, name, members, List.of(), owner);
    }

    public Conversation syncGroupSafely(String groupId, String name, List<String> members,
                                        List<String> admins, String owner) {
        try {
            return syncGroup(groupId, name, members, admins, owner);
        } catch (DataIntegrityViolationException first) {
            return syncGroup(groupId, name, members, admins, owner);
        }
    }

    public Conversation syncGroup(String groupId, String name, List<String> members,
                                  List<String> admins, String owner) {
        Conversation c = conversations.findByExternalId(groupId)
                .orElseGet(() -> conversations.save(
                        new Conversation(ConversationType.GROUP, name, groupId)));

        if (name != null && !name.isBlank() && !name.equals(c.getTitle())) {
            c.setTitle(name);
            conversations.save(c);
        }
        if (members == null || members.isEmpty()) return c;

        Set<String> adminSet = admins == null ? Set.of() : Set.copyOf(admins);
        Map<String, Participant> current = participants.findByConversation(c).stream()
                .collect(Collectors.toMap(p -> p.getUser().getUsername(), p -> p));

        for (Map.Entry<String, Participant> e : current.entrySet()) {
            if (!members.contains(e.getKey())) {
                participants.delete(e.getValue());
            }
        }

        for (String username : members) {
            MemberRole role = username.equals(owner) ? MemberRole.OWNER
                    : adminSet.contains(username) ? MemberRole.ADMIN
                    : MemberRole.MEMBER;

            Participant existing = current.get(username);
            if (existing == null) {
                users.findByUsername(username).ifPresent(u -> joinIfAbsent(c, u, role));
            } else if (existing.getRole() != role) {
                existing.setRole(role);
                participants.save(existing);
            }
        }
        return c;
    }

    private void join(Conversation c, User u, MemberRole role) {
        participants.save(new Participant(u, c, role));
    }

    private void joinIfAbsent(Conversation c, User u, MemberRole role) {
        try {
            participants.save(new Participant(u, c, role));
        } catch (DataIntegrityViolationException alreadyThere) {
        }
    }

    public Map<String, MemberRole> rolesOf(Conversation c) {
        Map<String, MemberRole> out = new LinkedHashMap<>();
        for (Participant p : participants.findByConversation(c)) {
            out.put(p.getUser().getUsername(), p.getRole());
        }
        return out;
    }

    public List<Map<String, Object>> allGroups() {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Conversation c : conversations.findAll()) {
            if (c.getType() != ConversationType.GROUP) continue;
            Map<String, MemberRole> roles = rolesOf(c);
            if (roles.isEmpty()) continue;

            String owner = null;
            List<String> admins = new java.util.ArrayList<>();
            for (Map.Entry<String, MemberRole> e : roles.entrySet()) {
                if (e.getValue() == MemberRole.OWNER) {
                    owner = e.getKey();
                    admins.add(e.getKey());
                } else if (e.getValue() == MemberRole.ADMIN) {
                    admins.add(e.getKey());
                }
            }
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("id", c.getExternalId());
            g.put("name", c.getTitle() == null ? c.getExternalId() : c.getTitle());
            g.put("members", List.copyOf(roles.keySet()));
            g.put("admins", admins);
            if (owner != null) g.put("owner", owner);
            out.add(g);
        }
        return out;
    }

    public Optional<Conversation> conversationByExternalId(String externalId) {
        return conversations.findByExternalId(externalId);
    }

    public boolean isMember(Conversation c, String username) {
        return participants.findByConversation(c).stream()
                .anyMatch(p -> p.getUser().getUsername().equals(username));
    }

    @Transactional
    public Message record(Conversation c, String sender, String clientId, String body) {
        Optional<Message> existing = messages.findFirstByConversationAndClientId(c, clientId);
        if (existing.isPresent()) return existing.get();
        return messages.save(new Message(clientId, c, user(sender), body));
    }

    public List<Message> history(Conversation c) {
        return messages.findByConversationOrderBySentAtAsc(c);
    }

    public List<Message> historyPage(Conversation c, Long before, int limit) {
        Pageable page = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        List<Message> newestFirst = before == null
                ? messages.findByConversationOrderBySentAtDesc(c, page)
                : messages.findByConversationAndSentAtLessThanOrderBySentAtDesc(c, before, page);

        List<Message> out = new java.util.ArrayList<>(newestFirst);
        java.util.Collections.reverse(out);
        return out;
    }

    public Message message(Conversation c, String clientId) {
        return messages.findFirstByConversationAndClientId(c, clientId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such message"));
    }

    public void save(Message m) {
        messages.save(m);
    }

    public List<Reaction> reactionsOf(Message m) {
        return reactions.findByMessage(m);
    }

    @Transactional
    public void react(Conversation c, String username, String clientId, String emoji) {
        Message m = messages.findFirstByConversationAndClientId(c, clientId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such message"));
        User u = user(username);
        Optional<Reaction> existing = reactions.findByMessageAndUser(m, u);
        if (existing.isPresent()) {
            Reaction r = existing.get();
            if (r.getEmoji().equals(emoji)) {
                reactions.delete(r);
            } else {
                r.setEmoji(emoji);
                reactions.save(r);
            }
        } else {
            reactions.save(new Reaction(m, u, emoji));
        }
    }
}
