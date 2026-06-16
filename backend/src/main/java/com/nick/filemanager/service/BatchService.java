package com.nick.filemanager.service;

import com.nick.filemanager.model.entity.BatchTask;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Sync batch file operations — processes tasks without Kafka.
 * DB operations are kept in separate @WithTransaction helpers so that
 * blocking file I/O can run on the worker pool without holding a session.
 */
@ApplicationScoped
public class BatchService {

    @Inject
    Vertx vertx;

    /** Submit and execute a batch task.
     *  Three-phase design to keep DB ops and blocking I/O on separate threads:
     *  1. Persist initial PENDING/RUNNING status in a transaction (event loop)
     *  2. Run file operations on the worker pool (no DB session held)
     *  3. Persist final COMPLETED/FAILED status in a new transaction (event loop)
     */
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

        // Capture Vert.x event-loop context so we can return to it after
        // offloading to the worker thread for blocking file I/O.
        var ctx = vertx.getOrCreateContext();

        // Phase 1: persist initial state inside a transaction, return the managed entity id
        return persistInitial(bt)
            .flatMap(savedId ->
                // Phase 2: blocking I/O on worker thread — no DB interaction here
                Uni.createFrom().item(() -> {
                    int processed = 0;
                    int failed = 0;
                    for (Map<String, String> task : tasks) {
                        try {
                            String op = task.getOrDefault("operation", "COPY").toUpperCase();
                            String src = task.get("source");
                            String dst = task.get("destination");
                            if (src == null) { failed++; continue; }
                            Path srcPath = Path.of(src);
                            if (!Files.exists(srcPath)) { failed++; continue; }
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
                            processed++;
                        } catch (IOException e) {
                            failed++;
                        }
                    }
                    return new int[]{processed, failed};
                }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .emitOn(ctx::runOnContext)
                // Phase 3: persist final state in a fresh transaction
                .flatMap(counts -> finalizeTask(savedId, counts[0], counts[1]))
            );
    }

    @WithTransaction
    Uni<Long> persistInitial(BatchTask bt) {
        return bt.<BatchTask>persist().map(saved -> saved.id);
    }

    @WithTransaction
    Uni<BatchTask> finalizeTask(Long taskId, int processed, int failed) {
        return BatchTask.<BatchTask>findById(taskId)
            .onItem().ifNull().failWith(() ->
                new IllegalStateException("Batch task disappeared: " + taskId))
            .flatMap(task -> {
                task.processedCount = processed;
                task.failedCount = failed;
                task.status = "COMPLETED";
                task.completedAt = LocalDateTime.now();
                return task.<BatchTask>persist();
            });
    }

    /** Get batch task status */
    @WithTransaction
    public Uni<BatchTask> getStatus(Long id) {
        return BatchTask.<BatchTask>findById(id);
    }
}
