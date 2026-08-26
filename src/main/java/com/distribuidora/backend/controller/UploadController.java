package com.distribuidora.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Upload de imagens de produto (ADMIN, ver SecurityConfig). O arquivo e
// salvo em ./uploads e servido de volta como arquivo estatico em /uploads/**
// (ver WebConfig).
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private static final Path UPLOAD_DIR = Paths.get("uploads").toAbsolutePath().normalize();
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    @PostMapping
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Arquivo vazio"));
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("error", "Arquivo maior que 5MB"));
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Envie uma imagem JPEG, PNG, WEBP ou GIF"));
        }

        Files.createDirectories(UPLOAD_DIR);

        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
        String filename = UUID.randomUUID() + extension;

        Path target = UPLOAD_DIR.resolve(filename).normalize();
        file.transferTo(target);

        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/")
                .path(filename)
                .toUriString();

        return ResponseEntity.ok(Map.of("url", url));
    }
}
