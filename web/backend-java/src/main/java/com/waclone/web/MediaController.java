package com.waclone.web;

import com.waclone.security.AuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final int CHUNK = 256 * 1024;

    private final Path root;

    public MediaController(@Value("${waclone.media.dir}") String dir) throws IOException {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    @PostMapping
    public Map<String, Object> upload(@RequestAttribute(AuthFilter.USER_ATTR) String username,
                                      @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty file");
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        Path target = root.resolve(id);

        MessageDigest digest = sha256();
        try (InputStream in = file.getInputStream();
             DigestInputStream hashing = new DigestInputStream(in, digest);
             OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            byte[] buf = new byte[CHUNK];
            int n;
            while ((n = hashing.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }

        String original = safeName(file.getOriginalFilename());
        Files.writeString(root.resolve(id + ".name"), original, StandardCharsets.UTF_8);

        return Map.of("id", id,
                      "fileName", original,
                      "size", Files.size(target),
                      "sha256", HexFormat.of().formatHex(digest.digest()),
                      "contentType", file.getContentType() == null
                              ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType(),
                      "uploadedBy", username);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id,
                                             @RequestAttribute(AuthFilter.USER_ATTR) String user)
            throws IOException {
        if (!id.matches("[a-f0-9]{32}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad media id");
        }
        Path file = root.resolve(id);
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such media");
        }
        Path nameFile = root.resolve(id + ".name");
        String name = Files.isRegularFile(nameFile)
                ? Files.readString(nameFile, StandardCharsets.UTF_8) : id;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8))
                .contentLength(Files.size(file))
                .contentType(guessType(name))
                .body(new FileSystemResource(file));
    }

    private static String safeName(String raw) {
        if (raw == null || raw.isBlank()) return "file";
        String leaf = Paths.get(raw).getFileName().toString();
        return leaf.replaceAll("[\\\\/\\r\\n]", "_");
    }

    private static MediaType guessType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".mp4")) return MediaType.parseMediaType("video/mp4");

        if (lower.startsWith("voice-") && lower.endsWith(".webm")) {
            return MediaType.parseMediaType("audio/webm");
        }
        if (lower.endsWith(".webm")) return MediaType.parseMediaType("video/webm");
        if (lower.endsWith(".mp3")) return MediaType.parseMediaType("audio/mpeg");
        if (lower.endsWith(".ogg") || lower.endsWith(".oga")) return MediaType.parseMediaType("audio/ogg");
        if (lower.endsWith(".wav")) return MediaType.parseMediaType("audio/wav");
        if (lower.endsWith(".m4a")) return MediaType.parseMediaType("audio/mp4");
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
