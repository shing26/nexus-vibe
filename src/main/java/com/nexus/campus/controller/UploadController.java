package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private static final Map<String, String> IMAGE_MAGIC_BYTES = Map.of(
            "jpg", "FFD8FF",
            "png", "89504E470D0A1A0A",
            "gif", "47494638",
            "webp", "52494646"
    );

    private static final Set<String> ALLOWED_ORIGINAL_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp");

    @Value("${campus.upload.dir:src/main/resources/static/uploads}")
    private String uploadDir;

    private Path uploadPath;

    @PostConstruct
    void init() {
        this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
            log.info("[NEXUS-UPLOAD] Upload directory initialized at {}", uploadPath);
        } catch (IOException e) {
            log.error("[NEXUS-UPLOAD] Failed to create upload directory", e);
        }
    }

    @PostMapping("/image")
    public ApiResponse<String> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "File is empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.error(400, "File size exceeds 5MB limit.");
        }

        String extension = detectImageExtension(file);
        if (extension == null || !originalNameAllowed(file.getOriginalFilename(), extension)) {
            return ApiResponse.error(400, "Only JPG, PNG, GIF, and WebP images are allowed.");
        }

        String filename = UUID.randomUUID() + "." + extension;
        Path targetPath = uploadPath.resolve(filename).normalize();
        if (!targetPath.startsWith(uploadPath)) {
            return ApiResponse.error(400, "Invalid upload path.");
        }

        try {
            file.transferTo(targetPath.toFile());
            log.info("[NEXUS-UPLOAD] File saved: {}", targetPath);
            return ApiResponse.success("Image uploaded.", "/uploads/" + filename);
        } catch (IOException e) {
            log.error("[NEXUS-UPLOAD] Upload failed", e);
            return ApiResponse.error(500, "File upload failed.");
        }
    }

    private String detectImageExtension(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(12);
            if (startsWith(header, IMAGE_MAGIC_BYTES.get("jpg"))) return "jpg";
            if (startsWith(header, IMAGE_MAGIC_BYTES.get("png"))) return "png";
            if (startsWith(header, IMAGE_MAGIC_BYTES.get("gif"))) return "gif";
            if (header.length >= 12
                    && startsWith(header, IMAGE_MAGIC_BYTES.get("webp"))
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return "webp";
            }
            return null;
        } catch (IOException e) {
            log.warn("[NEXUS-UPLOAD] Failed to inspect uploaded file", e);
            return null;
        }
    }

    private boolean startsWith(byte[] data, String hexPrefix) {
        if (data == null || data.length < hexPrefix.length() / 2) return false;
        for (int i = 0; i < hexPrefix.length(); i += 2) {
            int expected = Integer.parseInt(hexPrefix.substring(i, i + 2), 16);
            if ((data[i / 2] & 0xFF) != expected) return false;
        }
        return true;
    }

    private boolean originalNameAllowed(String originalName, String detectedExtension) {
        if (originalName == null || originalName.isBlank()) return true;
        String normalized = originalName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String leaf = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = leaf.lastIndexOf('.');
        if (dot < 0) return true;
        String extension = leaf.substring(dot + 1).toLowerCase();
        if (!ALLOWED_ORIGINAL_EXTENSIONS.contains(extension)) return false;
        return extension.equals(detectedExtension)
                || ("jpeg".equals(extension) && "jpg".equals(detectedExtension));
    }
}
