package com.nick.filemanager.model.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

/**
 * A single file entry in a duplicate group.
 */
@Entity
@Table(name = "duplicate_file")
public class DuplicateFile extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    public DuplicateGroup group;

    @Column(name = "file_index_id")
    public Long fileIndexId;

    @Column(nullable = false, length = 1024)
    public String path;

    @Column(name = "size_bytes")
    public long sizeBytes;
}
