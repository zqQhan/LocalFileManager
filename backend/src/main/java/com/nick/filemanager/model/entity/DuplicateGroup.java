package com.nick.filemanager.model.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Group of duplicate files sharing the same SHA-256 content hash.
 */
@Entity
@Table(name = "duplicate_group", indexes = {
    @Index(name = "idx_dg_hash", columnList = "contentHash")
})
public class DuplicateGroup extends PanacheEntity {

    @Column(name = "content_hash", nullable = false, length = 64)
    public String contentHash;

    @Column(name = "file_count")
    public int fileCount;

    @Column(name = "detected_at")
    public LocalDateTime detectedAt = LocalDateTime.now();

    /** Individual duplicate file records */
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    public java.util.List<DuplicateFile> duplicates = new java.util.ArrayList<>();
}
