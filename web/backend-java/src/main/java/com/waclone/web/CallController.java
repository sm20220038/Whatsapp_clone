package com.waclone.web;

import com.waclone.model.CallLog;
import com.waclone.model.Enums.CallStatus;
import com.waclone.model.Enums.CallType;
import com.waclone.model.User;
import com.waclone.repo.CallLogRepo;
import com.waclone.repo.UserRepo;
import com.waclone.security.AuthFilter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/calls")
public class CallController {

    private final CallLogRepo calls;
    private final UserRepo users;

    public CallController(CallLogRepo calls, UserRepo users) {
        this.calls = calls;
        this.users = users;
    }

    public record LogRequest(String peer, boolean outgoing, String type,
                             String status, Long durationMs) {}

    @PostMapping
    public Map<String, Object> log(@RequestAttribute(AuthFilter.USER_ATTR) String me,
                                   @RequestBody LogRequest req) {
        User self = load(me);
        User other = load(req.peer());

        CallLog entry = new CallLog(
                req.outgoing() ? self : other,
                req.outgoing() ? other : self,
                parse(CallType.class, req.type(), CallType.VOICE),
                parse(CallStatus.class, req.status(), CallStatus.ENDED));

        if (req.durationMs() != null && req.durationMs() > 0) {
            entry.setEndedAt(entry.getStartedAt() + req.durationMs());
        }
        return view(calls.save(entry), me);
    }

    @GetMapping
    public List<Map<String, Object>> history(@RequestAttribute(AuthFilter.USER_ATTR) String me) {
        User self = load(me);
        List<Map<String, Object>> out = new ArrayList<>();
        for (CallLog c : calls.findTop50ByCallerOrCalleeOrderByStartedAtDesc(self, self)) {
            out.add(view(c, me));
        }
        return out;
    }

    private User load(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "peer is required");
        }
        return users.findByUsername(username).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such user: " + username));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback) {
        if (raw == null) return fallback;
        try {
            return Enum.valueOf(type, raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static Map<String, Object> view(CallLog c, String me) {
        boolean outgoing = c.getCaller().getUsername().equals(me);
        Map<String, Object> out = new HashMap<>();
        out.put("id", c.getCallId());
        out.put("peer", outgoing ? c.getCallee().getUsername() : c.getCaller().getUsername());
        out.put("outgoing", outgoing);
        out.put("type", c.getType().name().toLowerCase());
        out.put("status", c.getStatus().name().toLowerCase());
        out.put("startedAt", c.getStartedAt());
        out.put("durationMs", c.getEndedAt() > 0 ? c.getEndedAt() - c.getStartedAt() : 0);
        return out;
    }
}
