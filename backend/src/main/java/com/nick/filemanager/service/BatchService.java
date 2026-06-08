package com.nick.filemanager.service;

import com.nick.filemanager.model.entity.BatchTask;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Sync batch file operations — processes tasks without Kafka.
 */
@WithTransaction
@ApplicationScoped
public class BatchService {

    /** Submit and execute a batch task synchronously (in-memory). */
    public Uni<BatchTask> submit(Map<String, Object> body) {
        String type = (String) body.getOrDefault("type", "COPY");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> tasks = (List<Map<String, String>>) body.get("tasks");
        if (tasks == null || tasks.isEmpty()) {
            return Uni.createFrom().failure(
                new IllegalArgumentException("tasks list is required"));
        }

        BatchTask bt = new BatchTask();
        bt.type = type;
        bt.status = "RUNNING";
        bt.totalCount = tasks.size();
        bt.processedCount = 0;
        bt.failedCount = 0;
        bt.createdAt = LocalDateTime.now();

        return bt.<BatchTask>persist().flatMap(saved ->
            Uni.createFrom().item(() -> {
                for (Map<String, String> task : tasks) {
                    try {
                        String op = task.getOrDefault("operation", "COPY").toUpperCase();
                        String src = task.get("source");
                        String dst = task.get("destination");
                        if (src == null) { saved.failedCount++; continue; }
                        Path srcPath = Path.of(src);
                        if (!Files.exists(srcPath)) { saved.failedCount++; continue; }
                        switch (op) {
                            case "COPY" -> {
                                Path dstPath = Path.of(dst);
                                if (dstPath.getParent() != null) Files.createDirectories(dstPath.getParent());
                                Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING);
                            }
                            case "MOVE" -> {
                                Path dstPath = Path.of(dst);
                                if (dstPath.getParent() != null) Files.createDirectories(dstPath.getParent());
                                Files.move(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING);
                            }
                            case "DELETE" -> Files.deleteIfExists(srcPath);
                        }
                        saved.processedCount++;
                    } catch (IOException e) {
                        saved.failedCount++;
                    }
                }
                saved.status = "COMPLETED";
                saved.completedAt = LocalDateTime.now();
                return saved;
            }).flatMap(s -> s.<BatchTask>persist())
        );
    }

    /** Get batch task status */
    public Uni<BatchTask> getStatus(Long id) {
        return BatchTask.<BatchTask>findById(id);
    }
}
