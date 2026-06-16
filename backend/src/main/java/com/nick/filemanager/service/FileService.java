package com.nick.filemanager.service;

import com.nick.filemanager.common.constant.AppConstants;
import com.nick.filemanager.common.dto.FileInfo;
import com.nick.filemanager.model.entity.FileIndex;
import com.nick.filemanager.repository.FileIndexRepository;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.zip.ZipInputStream;

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

    /** Browse a local directory — runs blocking I/O on worker thread. */
    public Uni<List<FileInfo>> browseDirectory(String rawPath) {
        return Uni.createFrom().item(() -> {
            Path dir;
            try {
                dir = Path.of(rawPath).toAbsolutePath().normalize();
            } catch (Exception e) {
                throw new IllegalArgumentException("无效的路径格式: " + rawPath);
            }
            if (!Files.exists(dir)) {
                throw new IllegalArgumentException("目录不存在: " + dir);
            }
            if (!Files.isDirectory(dir)) {
                throw new IllegalArgumentException("不是一个目录: " + dir);
            }
            if (!Files.isReadable(dir)) {
                throw new SecurityException("没有读取权限: " + dir);
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
            } catch (java.nio.file.AccessDeniedException e) {
                throw new SecurityException("没有权限访问目录: " + dir + "\n请选择一个有读取权限的目录。");
            } catch (IOException e) {
                throw new RuntimeException("读取目录失败: " + e.getMessage());
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    // ---- COPY ----

    /** Copy a file or directory — runs blocking I/O on worker thread. */
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
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
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
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
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
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
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
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
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

    /**
     * Read a text snippet from a file for content indexing.
     * Handles text files, Office Open XML (xlsx/docx/pptx), and binary files.
     */
    public String readContentSnippet(Path filePath) {
        try {
            long size = Files.size(filePath);
            if (size > AppConstants.MAX_FILE_SIZE_FOR_PREVIEW) {
                return "[File too large for preview]";
            }
            if (size == 0) {
                return "[Empty file]";
            }
            byte[] bytes = Files.readAllBytes(filePath);

            // Detect binary content by checking for null bytes
            if (isBinaryContent(bytes)) {
                // Try Office Open XML formats (xlsx/docx/pptx are ZIP files)
                String officeText = tryExtractOfficeXml(bytes);
                if (officeText != null && !officeText.isBlank()) {
                    return truncate(officeText, AppConstants.CONTENT_PREVIEW_MAX_CHARS);
                }
                return "[Binary file]";
            }

            // Plain text file — read as UTF-8
            String text = new String(bytes, StandardCharsets.UTF_8);
            return truncate(text, AppConstants.CONTENT_PREVIEW_MAX_CHARS);
        } catch (IOException e) {
            return "[Unreadable file]";
        }
    }

    // ---- Binary detection & Office text extraction ----

    /** Check if content appears to be binary (contains null bytes). */
    private boolean isBinaryContent(byte[] bytes) {
        int checkLen = Math.min(bytes.length, 512);
        for (int i = 0; i < checkLen; i++) {
            if (bytes[i] == 0) return true;
        }
        return false;
    }

    /**
     * Try to extract readable text from Office Open XML formats.
     * xlsx/docx/pptx are all ZIP archives containing XML files.
     * Returns null if not a supported Office format.
     */
    private String tryExtractOfficeXml(byte[] bytes) {
        // Check ZIP magic bytes
        if (bytes.length < 4 || bytes[0] != 0x50 || bytes[1] != 0x4B) {
            return null;
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // xlsx: shared strings + first worksheet
                if (name.equals("xl/sharedStrings.xml") || name.equals("xl/worksheets/sheet1.xml")
                    || name.equals("word/document.xml")  // docx
                    || (name.startsWith("ppt/slides/slide") && name.endsWith(".xml"))) { // pptx
                    String text = extractXmlText(zis);
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
        } catch (Exception ignored) {
            // Not a valid ZIP or can't read — treat as generic binary
        }
        return null;
    }

    /**
     * Extract all text content from Office XML using simple regex.
     * Targets text containers: &lt;t&gt; (xlsx), &lt;w:t&gt; (docx), &lt;a:t&gt; (pptx).
     * Uses strict tag-name matching to avoid false positives on attribute values.
     */
    private String extractXmlText(java.io.InputStream xmlStream) throws IOException {
        String xml = new String(xmlStream.readAllBytes(), StandardCharsets.UTF_8);
        // Match <t ...>, <w:t ...>, or <a:t ...> (strict: tag name IS "t" with optional ns prefix)
        java.util.regex.Pattern tagPattern = java.util.regex.Pattern.compile(
            "<(?:w:|a:)?t(?:\\s[^>]*)?>([^<]*)</(?:w:|a:)?t\\s*>");
        java.util.regex.Matcher m = tagPattern.matcher(xml);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String text = m.group(1).trim();
            if (!text.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(text);
            }
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        int len = Math.min(text.length(), maxLen);
        return text.substring(0, len);
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
