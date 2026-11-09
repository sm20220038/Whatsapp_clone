package com.waclone.web;

import com.waclone.model.Conversation;
import com.waclone.model.Enums.MessageStatus;
import com.waclone.model.Enums.MessageType;
import com.waclone.model.Message;
import com.waclone.model.Reaction;
import com.waclone.security.AuthFilter;
import com.waclone.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final ChatService chat;

    public MessageController(ChatService chat) {
        this.chat = chat;
    }

    public record SaveRequest(String clientId, String to, String group, String groupName,
                              List<String> members, String text, String type,
                              String replyTo, String fileName, Long fileSizeBytes,
                              String storagePath, String sha256) {}

    @PostMapping
    public Map<String, Object> save(@RequestAttribute(AuthFilter.USER_ATTR) String me,
                                    @RequestBody SaveRequest req) {
        if (req.clientId() == null || req.clientId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientId is required");
        }
        Conversation c = resolve(me, req.to(), req.group(), req.groupName(), req.members());
        Message m = chat.record(c, me, req.clientId(), req.text());

        if (req.type() != null || req.fileName() != null) {
            if (req.type() != null) {
                m.setType(parseType(req.type()));
            }
            if (req.fileName() != null) {
                m.setFileName(req.fileName());
                m.setFileSizeBytes(req.fileSizeBytes());
                m.setStoragePath(req.storagePath());
                m.setSha256(req.sha256());
            }
            chat.save(m);
        }
        return view(m, chat.reactionsOf(m));
    }

    @GetMapping
    public List<Map<String, Object>> history(@RequestAttribute(AuthFilter.USER_ATTR) String me,
                                             @RequestParam(required = false) String peer,
                                             @RequestParam(required = false) String group,
                                             @RequestParam(required = false) Long before,
                                             @RequestParam(defaultValue = "50") int limit) {
        Conversation c = resolve(me, peer, group, null, null);
        if (!chat.isMember(c, me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not in this conversation");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Message m : chat.historyPage(c, before, limit)) {
            out.add(view(m, chat.reactionsOf(m)));
        }
        return out;
    }

    public record AmendRequest(String clientId, String peer, String group, String text) {}

    @PostMapping("/edit")
    public Map<String, Object> edit(@RequestAttribute(AuthFilter.USER_ATTR) String me,
                                    @RequestBody AmendRequest req) {
        Message m = own(me, req);
        if (m.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "message was deleted");
        }
        if (req.text() == null || req.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
        }
        m.setBody(req.text());
        m.setEditedAt(System.currentTimeMillis());
        chat.save(m);
        return view(m, chat.reactionsOf(m));
    }

    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestAttribute(AuthFilter.USER_ATTR) String me,
                                      @RequestBody AmendRequest req) {
        Message m = own(me, req);
        m.setDeleted(true);
        m.setBody(null);

        m.setFileName(null);
        m.setFileSizeBytes(null);
        m.setStoragePath(null);
        m.setSha256(null);
        chat.save(m);
        return view(m, List.of());
    }

    private Message own(String me, AmendRequest req) {
        Conversation c = resolve(me, req.peer(), req.group(), null, null);
        Message m = chat.message(c, req.clientId());
        if (!m.getSender().getUsername().equals(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your message");
        }
        return m;
    }

    public record StatusRequest(String clientId, String peer, String group, String status) {}

    @PostMapping("/status")
    public Map<String, String> status(@RequestAttribute(AuthFilter.USER_ATTR) String me,
                                      @RequestBody StatusRequest req) {
        Conversation c = resolve(me, req.peer(), req.group(), null, null);
        Message m = chat.message(c, req.clientId());
        if (!m.getSender().getUsername().equals(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your message");
        }
        MessageStatus next = parseStatus(req.status());
        if (next.ordinal() > m.getStatus().ordinal()) {
            m.setStatus(next);
            chat.save(m);
        }
        return Map.of("status", m.getStatus().name().toLowerCase());
    }

    private static MessageStatus parseStatus(String raw) {
        try {
            return MessageStatus.valueOf(raw.toUpperCase());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad status");
        }
    }

    public record ReactRequest(String clientId, String to, String group, String emoji) {}

    @PostMapping("/react")
    public Map<String, String> react(@RequestAttribute(AuthFilter.USER_ATTR) String me,
                                     @RequestBody ReactRequest req) {
        Conversation c = resolve(me, req.to(), req.group(), null, null);
        chat.react(c, me, req.clientId(), req.emoji());
        return Map.of("status", "ok");
    }

    private Conversation resolve(String me, String peer, String group,
                                 String groupName, List<String> members) {
        if (group != null && !group.isBlank()) {
            return chat.group(group, groupName, members, me);
        }
        if (peer != null && !peer.isBlank()) {
            return chat.direct(me, peer);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "need peer or group");
    }

    private static MessageType parseType(String raw) {
        try {
            return MessageType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MessageType.TEXT;
        }
    }

    private static Map<String, Object> view(Message m, List<Reaction> reactions) {
        Map<String, Object> out = new HashMap<>();
        out.put("id", m.getClientId());
        out.put("from", m.getSender().getUsername());
        out.put("text", m.getBody());
        out.put("type", m.getType().name().toLowerCase());
        out.put("status", m.getStatus().name().toLowerCase());
        out.put("ts", m.getSentAt());
        out.put("editedAt", m.getEditedAt());
        out.put("deleted", m.isDeleted());
        if (m.getFileName() != null) {
            out.put("fileName", m.getFileName());
            out.put("fileSizeBytes", m.getFileSizeBytes());
            out.put("storagePath", m.getStoragePath());
            out.put("sha256", m.getSha256());
        }
        if (!reactions.isEmpty()) {
            Map<String, String> byUser = new HashMap<>();
            for (Reaction r : reactions) {
                byUser.put(r.getUser().getUsername(), r.getEmoji());
            }
            out.put("reactions", byUser);
        }
        return out;
    }
}
