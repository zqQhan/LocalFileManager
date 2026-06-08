package com.nick.filemanager.service;

import com.nick.filemanager.common.constant.AppConstants;
import com.nick.filemanager.common.dto.FileInfo;
import com.nick.filemanager.model.entity.FileIndex;
import com.nick.filemanager.repository.FileIndexRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core file operations: browse, copy, move, delete, rename, hash.
 * All blocking I/O runs on worker threads via {@code Uni.createFrom().item(() -> ...)}.
 */
@ApplicationScoped
public class FileService {

    @Inject
    FileIndexRepository fileRepo;

    @Inject
    PathGuard guard;

    // ---- BROWSE ----

    /** Browse a local directory — returns both files and subdirectories. */
    public Uni<List<FileInfo>> browseDirectory(String rawPath) {
        return Uni.createFrom().item(() -> {
            Path dir = Path.of(rawPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(dir)) {
                throw new IllegalArgumentException("Not a directory: " + dir);
            }
            try (var stream = Files.list(dir)) {
                return stream
                    .sorted((a, b) -> {
                        boolean aDir = Files.isDirectory(a);
                        boolean bDir = Files.isDirectory(b);
                        if (aDir && !bDir) return -1;
                        if (!aDir && bDir) return 1;
                        return a.getFileName().toString().compareToIgnoreCase(
                               b.getFileName().toString());
                    })
                    .map(p -> {
                        FileInfo info = new FileInfo();
                        info.setPath(p.toAbsolutePath().toString());
                        info.setName(p.getFileName().toString());
                        info.setIsDirectory(Files.isDirectory(p));
                        try {
                            if (!info.getIsDirectory()) info.setSizeBytes(Files.size(p));
                            info.setModifiedAt(LocalDateTime.ofInstant(
                                Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault()));
                        } catch (IOException ignored) {}
                        return info;
                    })
                    .collect(Collectors.toList());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ---- COPY ----

    /** Copy a file or directory — runs on worker thread. */
    public Uni<FileInfo> copyFile(String source, String destination) {
        return Uni.createFrom().item(() -> {
            try {
                Path src = Path.of(source).toAbsolutePath().normalize();
                Path dst = Path.of(destination).toAbsolutePath().normalize();
                guard.checkSafe(dst.toString(), "write");
                if (!Files.exists(src)) {
                    throw new IllegalArgumentException("Source not found: " + src);
                }
                if (dst.getParent() != null) {
                    Files.createDirectories(dst.getParent());
                }
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                return buildFileInfo(dst);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ---- MOVE ----

    public Uni<FileInfo> moveFile(String source, String destination) {
        return Uni.createFrom().item(() -> {
            try {
                Path src = Path.of(source).toAbsolutePath().normalize();
                Path dst = Path.of(destination).toAbsolutePath().normalize();
                guard.checkSafe(src.toString(), "write");
                guard.checkSafe(dst.toString(), "write");
                if (!Files.exists(src)) {
                    throw new IllegalArgumentException("Source not found: " + src);
                }
                if (dst.getParent() != null) {
                    Files.createDirectories(dst.getParent());
                }
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
                return buildFileInfo(dst);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ---- DELETE ----

    public Uni<Boolean> deleteFile(String path) {
        return Uni.createFrom().item(() -> {
            try {
                Path p = Path.of(path).toAbsolutePath().normalize();
                guard.checkSafe(p.toString(), "delete");
                if (!Files.exists(p)) {
                    throw new IllegalArgumentException("File not found: " + p);
                }
                Files.delete(p);
                return true;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ---- RENAME ----

    public Uni<FileInfo> renameFile(String path, String newName) {
        return Uni.createFrom().item(() -> {
            try {
                Path src = Path.of(path).toAbsolutePath().normalize();
                guard.checkSafe(src.toString(), "write");
                if (!Files.exists(src)) {
                    throw new IllegalArgumentException("File not found: " + src);
                }
                Path dst = src.resolveSibling(newName);
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
                return buildFileInfo(dst);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ---- HASH ----

    /** Compute SHA-256 hash of a file. */
    public String computeContentHash(Path filePath) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance(AppConstants.HASH_ALGORITHM);
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] digest = md.digest(fileBytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /** Read a text snippet from a file for content indexing. */
    public String readContentSnippet(Path filePath) {
        try {
            long size = Files.size(filePath);
            if (size > AppConstants.MAX_FILE_SIZE_FOR_PREVIEW) {
                return "[File too large for preview]";
            }
            byte[] bytes = Files.readAllBytes(filePath);
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            int len = Math.min(text.length(), AppConstants.CONTENT_PREVIEW_MAX_CHARS);
            return text.substring(0, len);
        } catch (IOException e) {
            return "[Binary or unreadable file]";
        }
    }

    // ---- HELPERS ----

    private FileInfo buildFileInfo(Path p) {
        FileInfo info = new FileInfo();
        info.setPath(p.toAbsolutePath().toString());
        info.setName(p.getFileName().toString());
        info.setIsDirectory(Files.isDirectory(p));
        try {
            if (!info.getIsDirectory()) info.setSizeBytes(Files.size(p));
            info.setModifiedAt(LocalDateTime.ofInstant(
                Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault()));
        } catch (IOException ignored) {}
        return info;
    }
}
