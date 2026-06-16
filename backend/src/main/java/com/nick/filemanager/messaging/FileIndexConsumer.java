package com.nick.filemanager.messaging;

import com.nick.filemanager.common.constant.AppConstants;
import com.nick.filemanager.model.entity.FileIndex;
import com.nick.filemanager.repository.FileIndexRepository;
import com.nick.filemanager.service.FileService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CompletionStage;

/**
 * Consumes file-indexing tasks from Kafka and processes them.
 * Blocking I/O is offloaded to worker thread; DB ops run reactively.
 * Fixes the HR000068 error caused by mixing @Blocking with Panache.withTransaction().
 */
@ApplicationScoped
public class FileIndexConsumer {

    @Inject
    FileIndexRepository fileRepo;

    @Inject
    FileService fileService;

    @Incoming("file-index-in")
    public CompletionStage<Void> processIndexTask(String filePath) {
        // Offload all blocking I/O to worker thread
        return Uni.createFrom().item(() -> {
                try {
                    Path path = Path.of(filePath);
                    if (!Files.exists(path) || Files.isDirectory(path)) {
                        return null; // skip — not a regular file
                    }

                    long size = Files.size(path);
                    LocalDateTime modified = LocalDateTime.ofInstant(
                        Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault());

                    String mimeType;
                    try { mimeType = Files.probeContentType(path); }
                    catch (IOException e) { mimeType = "application/octet-stream"; }

                    FileIndex fi = FileIndex.fromPath(path, size, modified, mimeType);

                    // Content hash + snippet (best effort for text-ish files)
                    if (size < AppConstants.MAX_FILE_SIZE_FOR_HASH) {
                        try {
                            fi.contentHash = fileService.computeContentHash(path);
                        } catch (Exception ignored) {}
                        try {
                            fi.contentSnippet = fileService.readContentSnippet(path);
                        } catch (Exception ignored) {}
                    }
                    return fi;
                } catch (IOException e) {
                    return null; // skip unreadable files
                }
            })
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
            .emitOn(Infrastructure.getDefaultExecutor())
            .flatMap(fi -> {
                if (fi == null) {
                    return Uni.createFrom().<Void>voidItem();
                }
                // Reactive DB transaction on event-loop thread
                return Panache.withTransaction(() ->
                    fileRepo.findByPath(filePath)
                        .flatMap(existing -> {
                            if (existing != null) {
                                existing.name = fi.name;
                                existing.extension = fi.extension;
                                existing.sizeBytes = fi.sizeBytes;
                                existing.modifiedAt = fi.modifiedAt;
                                existing.mimeType = fi.mimeType;
                                existing.contentHash = fi.contentHash;
                                existing.contentSnippet = fi.contentSnippet;
                                existing.indexedAt = LocalDateTime.now();
                                return existing.persist();
                            } else {
                                return fi.persist();
                            }
                        })
                ).replaceWithVoid();
            })
            .onFailure().recoverWithNull()
            .subscribeAsCompletionStage()
            .thenApply(v -> null);
    }
}
