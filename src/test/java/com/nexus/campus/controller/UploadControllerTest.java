package com.nexus.campus.controller;

import com.nexus.campus.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class UploadControllerTest {

    @TempDir
    Path tempDir;

    private UploadController controller;

    @BeforeEach
    void setUp() {
        controller = new UploadController();
        ReflectionTestUtils.setField(controller, "uploadDir", tempDir.toString());
        controller.init();
    }

    @Test
    @DisplayName("Valid PNG is stored under /uploads with a generated UUID name")
    void validPng_shouldBeStored() {
        byte[] png = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.png/../../outside.png", "image/png", png);

        ApiResponse<String> result = controller.uploadImage(file);

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertTrue(result.getData().matches("^/uploads/[0-9a-fA-F-]{36}\\.png$"));
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(1, files.count());
        } catch (Exception e) {
            fail("Unexpected filesystem error", e);
        }
    }

    @Test
    @DisplayName("HTML file disguised as image/png is rejected")
    void htmlDisguisedAsPng_shouldBeRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.html", "image/png",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

        ApiResponse<String> result = controller.uploadImage(file);

        assertEquals(400, result.getCode());
        assertEmptyUploadDir();
    }

    @Test
    @DisplayName("Magic bytes that do not match the original extension are rejected")
    void jpegBytesNamedPng_shouldBeRejected() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", jpeg);

        ApiResponse<String> result = controller.uploadImage(file);

        assertEquals(400, result.getCode());
        assertEmptyUploadDir();
    }

    @Test
    @DisplayName("Non-image file is rejected")
    void textFile_shouldBeRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        ApiResponse<String> result = controller.uploadImage(file);

        assertEquals(400, result.getCode());
        assertEmptyUploadDir();
    }

    @Test
    @DisplayName("Files larger than 5MB are rejected")
    void oversizedFile_shouldBeRejected() {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.png", "image/png", big);

        ApiResponse<String> result = controller.uploadImage(file);

        assertEquals(400, result.getCode());
        assertEmptyUploadDir();
    }

    private void assertEmptyUploadDir() {
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(0, files.count());
        } catch (Exception e) {
            fail("Unexpected filesystem error", e);
        }
    }
}
