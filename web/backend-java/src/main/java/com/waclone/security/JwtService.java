package com.waclone.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

@Service
public class JwtService {

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final byte[] secret;
    private final long ttlSeconds;

    public JwtService(@Value("${waclone.jwt.secret}") String secret,
                      @Value("${waclone.jwt.ttlHours:72}") long ttlHours) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlHours * 3600;
    }

    public String issue(String username) {
        long now = System.currentTimeMillis() / 1000;
        String payload = "{\"sub\":\"" + escape(username) + "\",\"iat\":" + now
                + ",\"exp\":" + (now + ttlSeconds) + "}";
        String signing = b64(HEADER_JSON.getBytes(StandardCharsets.UTF_8)) + "."
                + b64(payload.getBytes(StandardCharsets.UTF_8));
        return signing + "." + b64(hmac(signing));
    }

    public Optional<String> verify(String token) {
        if (token == null) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length != 3) return Optional.empty();

        byte[] expected = hmac(parts[0] + "." + parts[1]);
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(expected, actual)) return Optional.empty();

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        long exp = longField(payload, "exp");
        if (exp <= System.currentTimeMillis() / 1000) return Optional.empty();

        String sub = stringField(payload, "sub");
        return sub.isEmpty() ? Optional.empty() : Optional.of(sub);
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    private static String b64(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String stringField(String json, String name) {
        int i = json.indexOf("\"" + name + "\":\"");
        if (i < 0) return "";
        int start = i + name.length() + 4;
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end);
    }

    private static long longField(String json, String name) {
        int i = json.indexOf("\"" + name + "\":");
        if (i < 0) return 0;
        int start = i + name.length() + 3;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return end == start ? 0 : Long.parseLong(json.substring(start, end));
    }
}
