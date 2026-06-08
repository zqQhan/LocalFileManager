package com.nick.filemanager.model.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

/**
 * User-defined tag / category for files.
 * Supports hierarchical tags via parentId self-reference.
 */
@Entity
@Table(name = "tag", indexes = {
    @Index(name = "idx_tag_name", columnList = "name", unique = true)
})
public class Tag extends PanacheEntity {

    @Column(nullable = false, length = 100)
    public String name;

    @Column(length = 7)
    public String color = "#3B82F6";       // Default blue, hex color

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "parent_id")
    public Long parentId;                  // Nullable — for hierarchical categories

    /** FK to file_tag junction table */
    @ManyToMany
    @JoinTable(name = "file_tag",
        joinColumns = @JoinColumn(name = "tag_id"),
        inverseJoinColumns = @JoinColumn(name = "file_id"))
    public java.util.Set<FileIndex> files = new java.util.HashSet<>();

    @Override
    public String toString() {
        return String.format("Tag[id=%d, name='%s', color='%s']", id, name, color);
    }
}
