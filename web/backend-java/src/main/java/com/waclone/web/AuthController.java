package com.waclone.web;

import com.waclone.model.User;
import com.waclone.repo.UserRepo;
import com.waclone.security.JwtService;
import com.waclone.security.PasswordService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepo users;
    private final PasswordService passwords;
    private final JwtService jwt;

    public AuthController(UserRepo users, PasswordService passwords, JwtService jwt) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 20) String username,
            @Size(max = 60) String displayName,
            @NotBlank @Size(min = 4, max = 100) String password) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        String username = req.username().trim().toLowerCase();
        if (!username.matches("[a-z0-9_.-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "username may only contain letters, digits, _ . -");
        }
        if (users.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        String display = (req.displayName() == null || req.displayName().isBlank())
                ? username : req.displayName().trim();

        User user = users.save(new User(username, display, passwords.hash(req.password())));
        return ResponseEntity.ok(session(user));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByUsername(req.username().trim().toLowerCase())
                .filter(u -> passwords.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Wrong username or password"));
        return session(user);
    }

    private Map<String, Object> session(User user) {
        return Map.of("token", jwt.issue(user.getUsername()),
                      "user", Map.of("username", user.getUsername(),
                                     "displayName", user.getDisplayName()));
    }
}
