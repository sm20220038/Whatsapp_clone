package com.waclone.web;

import com.waclone.service.ChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/internal")
public class InternalController {

    private final ChatService chat;
    private final byte[] secret;

    public InternalController(ChatService chat, @Value("${waclone.jwt.secret}") String secret) {
        this.chat = chat;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/groups")
    public List<Map<String, Object>> groups(
            @RequestHeader(value = "x-internal-auth", required = false) String auth) {
        if (auth == null || !MessageDigest.isEqual(secret, auth.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }
        return chat.allGroups();
    }
}
