package com.nick.filemanager.messaging;

import com.nick.filemanager.model.entity.FileIndex;
import com.nick.filemanager.repository.FileIndexRepository;
import com.nick.filemanager.service.FileService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.annotations.Blocking;
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
 * Indexes: file metadata, content hash, and text snippet.
 */
@ApplicationScoped
public class FileIndexConsumer {

    @Inject
    FileIndexRepository fileRepo;

    @Inject
    FileService fileService;

    @Incoming("file-index-in")
    @Blocking
    public CompletionStage<Void> processIndexTask(String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path) || Files.isDirectory(path)) {
                return Uni.createFrom().voidItem().subscribeAsCompletionStage();
            }

            long size = Files.size(path);
            LocalDateTime modified = LocalDateTime.ofInstant(
                Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault());

            String mimeType;
            try { mimeType = Files.probeContentType(path); }
            catch (IOException e) { mimeType = "application/octet-stream"; }

            FileIndex fi = FileIndex.fromPath(path, size, modified, mimeType);

            // Content hash + snippet (best effort for text-ish files)
            if (size < 100 * 1024 * 1024) { // 100 MB limit
                try {
                    fi.contentHash = fileService.computeContentHash(path);
                } catch (Exception ignored) {}
                try {
                    fi.contentSnippet = fileService.readContentSnippet(path);
                } catch (Exception ignored) {}
            }

            // Check if already indexed; upsert (manual transaction)
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
            ).subscribeAsCompletionStage().thenApply(v -> null);

        } catch (Exception e) {
            return Uni.createFrom().voidItem().subscribeAsCompletionStage();
        }
    }
}
