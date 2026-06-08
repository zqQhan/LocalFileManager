package com.nick.filemanager.model.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Custom file operation rule — auto-execute actions on matching files.
 * e.g. "delete all *.tmp files every hour"
 */
@Entity
@Table(name = "file_rule", indexes = {
    @Index(name = "idx_rule_enabled", columnList = "enabled")
})
public class FileRule extends PanacheEntity {

    @Column(nullable = false, length = 100)
    public String name;                    // Rule name, e.g. "清理临时文件"

    @Column(nullable = false, length = 200)
    public String pattern;                 // Glob pattern, e.g. "*.tmp" or "**/*.log"

    @Column(name = "root_path", length = 1024)
    public String rootPath;                // Scope directory, null = all indexed paths

    @Column(name = "action_type", nullable = false, length = 20)
    public String actionType;              // DELETE, COPY, MOVE

    @Column(name = "action_target", length = 1024)
    public String actionTarget;            // Destination path for COPY/MOVE

    public boolean enabled = true;

    @Column(name = "last_run_at")
    public LocalDateTime lastRunAt;

    @Column(name = "files_affected")
    public int filesAffected;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
