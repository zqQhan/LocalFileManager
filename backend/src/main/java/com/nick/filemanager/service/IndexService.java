package com.nick.filemanager.service;

import com.nick.filemanager.model.entity.FileIndex;
import com.nick.filemanager.repository.FileIndexRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Sync file indexing service — walks a directory tree and indexes files into the database.
 */
@ApplicationScoped
public class IndexService {

    @Inject
    FileIndexRepository fileRepo;

    @Inject
    FileService fileService;

    /**
     * Walk directory, collect files, then index each one reactively.
     */
    @WithTransaction
    public Uni<IndexResult> indexDirectory(String rootPath) {
        Path root = Path.of(rootPath).toAbsolutePath().normalize();

        if (!Files.isDirectory(root)) {
            return Uni.createFrom().failure(
                new IllegalArgumentException("Not a directory: " + rootPath));
        }

        IndexResult result = new IndexResult();
        result.rootPath = root.toString();

        // Collect all regular files first (sync I/O, no DB)
        List<Path> files;
        try {
            files = collectFiles(root);
        } catch (IOException e) {
            return Uni.createFrom().failure(e);
        }
        result.totalScanned = files.size();

        if (files.isEmpty()) {
            return Uni.createFrom().item(result);
        }

        // Index each file reactively, one at a time (concatenate to avoid flooding DB)
        return Multi.createFrom().iterable(files)
            .onItem().transformToUniAndConcatenate(path ->
                indexFile(path)
                    .onFailure().invoke(e -> result.errors++)
                    .onFailure().recoverWithItem(false)
            )
            .collect().in(() -> new ArrayList<Boolean>(), (list, isNew) -> list.add(isNew))
            .map(results -> {
                for (Boolean isNew : results) {
                    if (isNew) result.newIndexed++;
                    else result.updated++;
                }
                return result;
            });
    }

    /**
     * Index a single file — full reactive chain.
     * Returns: true = new entry created, false = existing entry updated
     */
    private Uni<Boolean> indexFile(Path path) {
        String pathStr = path.toAbsolutePath().toString();

        return fileRepo.findByPath(pathStr)
            .flatMap(existing -> {
                // Build the new FileIndex metadata (sync I/O, fine for small ops)
                FileIndex fi;
                try {
                    fi = buildFileIndex(path);
                } catch (IOException e) {
                    return Uni.createFrom().<Boolean>failure(e);
                }
                fi.indexedAt = LocalDateTime.now();

                if (existing != null) {
                    // Update existing
                    existing.name = fi.name;
                    existing.extension = fi.extension;
                    existing.sizeBytes = fi.sizeBytes;
                    existing.modifiedAt = fi.modifiedAt;
                    existing.mimeType = fi.mimeType;
                    existing.contentHash = fi.contentHash;
                    existing.contentSnippet = fi.contentSnippet;
                    existing.indexedAt = fi.indexedAt;
                    return existing.<FileIndex>persist().map(e -> false);
                } else {
                    // New entry
                    return fi.<FileIndex>persist().map(e -> true);
                }
            });
    }

    // ---- Helpers ----

    private List<Path> collectFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(Files::isReadable)
                .toList();
        }
    }

    private FileIndex buildFileIndex(Path path) throws IOException {
        long size = Files.size(path);
        LocalDateTime modified = LocalDateTime.ofInstant(
            Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault());

        String mimeType;
        try {
            mimeType = Files.probeContentType(path);
        } catch (IOException e) {
            mimeType = "application/octet-stream";
        }

        FileIndex fi = FileIndex.fromPath(path, size, modified, mimeType);

        // Content hash + snippet
        if (size < 100 * 1024 * 1024 && size > 0) {
            try { fi.contentHash = fileService.computeContentHash(path); }
            catch (Exception ignored) {}
            try { fi.contentSnippet = fileService.readContentSnippet(path); }
            catch (Exception ignored) {}
        }
        return fi;
    }

    /**
     * Index result summary.
     */
    public static class IndexResult {
        public String rootPath;
        public int totalScanned;
        public int newIndexed;
        public int updated;
        public int errors;

        public Map<String, Object> toMap() {
            return Map.of(
                "rootPath", rootPath,
                "totalScanned", totalScanned,
                "newIndexed", newIndexed,
                "updated", updated,
                "errors", errors
            );
        }
    }
}
