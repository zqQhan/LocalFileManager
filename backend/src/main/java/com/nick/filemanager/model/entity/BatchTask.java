package com.nick.filemanager.model.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks a batch file operation task (e.g. bulk copy, move, delete).
 */
@Entity
@Table(name = "batch_task", indexes = {
    @Index(name = "idx_bt_status", columnList = "status")
})
public class BatchTask extends PanacheEntity {

    @Column(nullable = false, length = 20)
    public String type;                    // COPY, MOVE, DELETE, RENAME

    @Column(nullable = false, length = 20)
    public String status = "PENDING";      // PENDING, RUNNING, COMPLETED, FAILED

    @Column(name = "total_count")
    public int totalCount;

    @Column(name = "processed_count")
    public int processedCount;

    @Column(name = "failed_count")
    public int failedCount;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    public LocalDateTime completedAt;
}
