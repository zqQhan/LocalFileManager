package com.nick.filemanager.model.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Indexed file entry — wraps a file-system file or directory.
 * Extends PanacheEntity (auto Long id).
 */
@Entity
@Table(name = "file_index", indexes = {
    @Index(name = "idx_file_path", columnList = "path"),
    @Index(name = "idx_file_name", columnList = "name"),
    @Index(name = "idx_file_extension", columnList = "extension"),
    @Index(name = "idx_file_content_hash", columnList = "contentHash")
})
public class FileIndex extends PanacheEntity {

    @Column(nullable = false, length = 1024)
    public String path;                    // Absolute filesystem path

    @Column(nullable = false, length = 255)
    public String name;                    // File name (without path)

    @Column(length = 50)
    public String extension;               // Lowercase, e.g. "java", "pdf"

    @Column(name = "size_bytes")
    public long sizeBytes;                 // File size in bytes

    @Column(name = "modified_at")
    public LocalDateTime modifiedAt;       // Last modification timestamp

    @Column(name = "mime_type", length = 100)
    public String mimeType;                // Detected MIME type

    @Column(name = "content_hash", length = 64)
    public String contentHash;             // SHA-256 hex digest

    @Column(name = "is_directory")
    public boolean isDirectory;            // true if directory

    @Column(name = "content_snippet", columnDefinition = "TEXT")
    public String contentSnippet;          // First N chars of text content (for search)

    @Column(name = "indexed_at")
    public LocalDateTime indexedAt = LocalDateTime.now();

    // ---- Factory methods (static, for use with Uni / reactive) ----

    public static FileIndex fromPath(java.nio.file.Path filePath, long size, LocalDateTime modified,
                                      String mimeType) {
        FileIndex fi = new FileIndex();
        fi.path = filePath.toAbsolutePath().toString();
        fi.name = filePath.getFileName().toString();
        fi.isDirectory = java.nio.file.Files.isDirectory(filePath);
        if (!fi.isDirectory) {
            String fname = fi.name;
            int dot = fname.lastIndexOf('.');
            fi.extension = dot > 0 ? fname.substring(dot + 1).toLowerCase() : "";
            fi.sizeBytes = size;
            fi.mimeType = mimeType;
        } else {
            fi.extension = "";
            fi.sizeBytes = 0;
        }
        fi.modifiedAt = modified;
        fi.indexedAt = LocalDateTime.now();
        return fi;
    }
}
