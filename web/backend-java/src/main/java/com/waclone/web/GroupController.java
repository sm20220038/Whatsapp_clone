package com.waclone.web;

import com.waclone.model.Conversation;
import com.waclone.model.Enums.MemberRole;
import com.waclone.security.AuthFilter;
import com.waclone.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final ChatService chat;

    public GroupController(ChatService chat) {
        this.chat = chat;
    }

    public record SyncRequest(String id, String name, List<String> members,
                              List<String> admins, String owner) {}

    @PostMapping
    public Map<String, Object> sync(@RequestAttribute(AuthFilter.USER_ATTR) String me,
                                    @RequestBody SyncRequest req) {
        if (req.id() == null || req.id().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "group id is required");
        }
        if (req.members() == null || !req.members().contains(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not in this group");
        }
        Conversation c = chat.syncGroupSafely(req.id(), req.name(), req.members(),
                                              req.admins(), req.owner());
        return view(c, chat.rolesOf(c));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@RequestAttribute(AuthFilter.USER_ATTR) String me,
                                   @PathVariable String id) {
        Conversation c = chat.conversationByExternalId(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such group"));
        if (!chat.isMember(c, me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not in this group");
        }
        return view(c, chat.rolesOf(c));
    }

    private static Map<String, Object> view(Conversation c, Map<String, MemberRole> roles) {
        return Map.of(
                "id", c.getExternalId(),
                "name", c.getTitle() == null ? c.getExternalId() : c.getTitle(),
                "members", List.copyOf(roles.keySet()),
                "roles", roles.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey, e -> e.getValue().name().toLowerCase())));
    }
}
