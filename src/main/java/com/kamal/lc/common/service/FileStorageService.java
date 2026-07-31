package com.kamal.lc.common.service;

import com.kamal.lc.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * Stores uploaded files on the local filesystem under {@code app.file-storage.base-path}.
 *
 * <p>Files are renamed to a random UUID on write, so the original name never reaches the
 * filesystem. Every resolved path is verified to stay inside the base directory.
 */
@Slf4j
@Service
public class FileStorageService {

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf");

    private final Path basePath;

    public FileStorageService(@Value("${app.file-storage.base-path:./uploads}") String basePath) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new IllegalStateException("تعذّر إنشاء مجلد التخزين: " + this.basePath, e);
        }
    }

    /**
     * Writes the file under {@code subFolder} and returns the path relative to the base directory
     * (the value persisted in {@code captain_document.stored_path}).
     */
    public String store(MultipartFile file, String subFolder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "لم يتم اختيار ملف");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("UNSUPPORTED_TYPE",
                    "نوع الملف غير مدعوم — المسموح: صور (JPG, PNG, WEBP, GIF) أو PDF");
        }

        Path targetDir = resolveInsideBase(subFolder);
        String storedName = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        Path target = targetDir.resolve(storedName).normalize();
        assertInsideBase(target);

        try {
            Files.createDirectories(targetDir);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to store file in {}", targetDir, e);
            throw new BusinessException("STORAGE_ERROR", "فشل حفظ الملف");
        }

        return basePath.relativize(target).toString().replace('\\', '/');
    }

    /** Loads a previously stored file for download. */
    public Resource load(String storedPath) {
        Path file = resolveInsideBase(storedPath);
        if (!Files.exists(file) || !Files.isReadable(file)) {
            throw new BusinessException("FILE_NOT_FOUND", "الملف غير موجود على الخادم");
        }
        try {
            return new UrlResource(file.toUri());
        } catch (MalformedURLException e) {
            throw new BusinessException("FILE_NOT_FOUND", "الملف غير موجود على الخادم");
        }
    }

    /** Best-effort delete — a missing file must not block deleting its database row. */
    public void delete(String storedPath) {
        try {
            Files.deleteIfExists(resolveInsideBase(storedPath));
        } catch (IOException | BusinessException e) {
            log.warn("Could not delete stored file {}: {}", storedPath, e.getMessage());
        }
    }

    private Path resolveInsideBase(String relativePath) {
        Path resolved = basePath.resolve(relativePath == null ? "" : relativePath).normalize();
        assertInsideBase(resolved);
        return resolved;
    }

    private void assertInsideBase(Path path) {
        if (!path.startsWith(basePath)) {
            throw new BusinessException("INVALID_PATH", "مسار الملف غير صالح");
        }
    }

    private String extensionOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        String ext = originalName.substring(dot).toLowerCase();
        // Guard against separators smuggled into the extension.
        return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
    }
}
