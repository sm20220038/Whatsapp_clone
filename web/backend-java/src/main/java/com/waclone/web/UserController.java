package com.waclone.web;

import com.waclone.model.User;
import com.waclone.repo.UserRepo;
import com.waclone.security.AuthFilter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserRepo users;

    public UserController(UserRepo users) {
        this.users = users;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestAttribute(AuthFilter.USER_ATTR) String username) {
        return view(load(username));
    }

    @GetMapping("/users")
    public List<Map<String, Object>> all(@RequestAttribute(AuthFilter.USER_ATTR) String username) {
        return users.findAll().stream()
                .filter(u -> !u.getUsername().equals(username))
                .map(UserController::view)
                .toList();
    }

    public record ProfileRequest(String displayName, String avatarId) {}

    @PutMapping("/me")
    public Map<String, Object> updateProfile(@RequestAttribute(AuthFilter.USER_ATTR) String username,
                                             @RequestBody ProfileRequest req) {
        User user = load(username);
        if (req.displayName() != null && !req.displayName().isBlank()) {
            user.setDisplayName(req.displayName().trim());
        }
        if (req.avatarId() != null) {
            if (req.avatarId().isBlank()) {
                user.setAvatarId(null);
            } else if (req.avatarId().matches("[a-f0-9]{32}")) {
                user.setAvatarId(req.avatarId());
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad avatar id");
            }
        }
        users.save(user);
        return view(user);
    }

    private User load(String username) {
        return users.findByUsername(username).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such user"));
    }

    private static Map<String, Object> view(User u) {
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("username", u.getUsername());
        out.put("displayName", u.getDisplayName());
        out.put("lastSeen", u.getLastSeen());
        out.put("avatarId", u.getAvatarId());
        return out;
    }
}
