package com.nick.filemanager.service;

import com.nick.filemanager.common.constant.AppConstants;
import com.nick.filemanager.model.entity.FileIndex;
import com.nick.filemanager.repository.FileIndexRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sync file indexing service — walks a directory tree and indexes files into the database.
 *
 * Phase 1 (blocking file I/O) runs via Vert.x executeBlocking on worker thread pool,
 * keeping the event loop free for browsing/search requests.
 * Phase 2 (reactive DB) runs on event loop, one file at a time for session safety.
 *
 * Inaccessible directories (e.g. "System Volume Information") are silently skipped
 * via walkFileTree + visitFileFailed → SKIP_SUBTREE.
 */
@ApplicationScoped
public class IndexService {

    @Inject
    FileIndexRepository fileRepo;

    @Inject
    FileService fileService;

    @Inject
    Vertx vertx;

    /**
     * Index all regular files under rootPath.
     * Returns a Uni — the caller subscribes on the event loop, file I/O is offloaded,
     * and DB work comes back to the event loop automatically.
     */
    public Uni<IndexResult> indexDirectory(String rootPath) {
        // Phase 1: Blocking file I/O on Vert.x worker pool; result emits on event loop
        return vertx.executeBlocking(() -> {
                Path root = Path.of(rootPath).toAbsolutePath().normalize();
                if (!Files.isDirectory(root)) {
                    throw new IllegalArgumentException("Not a directory: " + rootPath);
                }

                List<FileIndex> fileIndices = new ArrayList<>();
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && Files.isReadable(file)) {
                            try {
                                FileIndex fi = buildFileIndex(file);
                                fi.indexedAt = LocalDateTime.now();
                                fileIndices.add(fi);
                            } catch (IOException ignored) {
                                // skip unreadable files
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        // Silently skip inaccessible files/directories
                        // (e.g. "System Volume Information", permission-protected folders)
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                });
                return fileIndices;
            })
            // Phase 2: Reactive DB on event loop (executeBlocking guarantees event-loop context here)
            .flatMap(fileIndices -> {
                IndexResult result = new IndexResult();
                result.rootPath = Path.of(rootPath).toAbsolutePath().normalize().toString();
                result.totalScanned = fileIndices.size();

                if (fileIndices.isEmpty()) {
                    return Uni.createFrom().item(result);
                }

                return Multi.createFrom().iterable(fileIndices)
                    .onItem().transformToUniAndConcatenate(fi ->
                        Panache.withTransaction(() ->
                            fileRepo.findByPath(fi.path)
                                .flatMap(existing -> {
                                    if (existing != null) {
                                        existing.name = fi.name;
                                        existing.extension = fi.extension;
                                        existing.sizeBytes = fi.sizeBytes;
                                        existing.modifiedAt = fi.modifiedAt;
                                        existing.mimeType = fi.mimeType;
                                        existing.contentHash = fi.contentHash;
                                        existing.contentSnippet = fi.contentSnippet;
                                        existing.indexedAt = fi.indexedAt;
                                        return existing.<FileIndex>persist()
                                            .map(e -> "updated");
                                    } else {
                                        return fi.<FileIndex>persist()
                                            .map(e -> "new");
                                    }
                                })
                        )
                        .onFailure().invoke(e -> result.errors++)
                        .onFailure().recoverWithItem("error")
                    )
                    .collect().in(ArrayList<String>::new, List::add)
                    .map(results -> {
                        for (String s : results) {
                            if ("new".equals(s)) result.newIndexed++;
                            else if ("updated".equals(s)) result.updated++;
                        }
                        return result;
                    });
            });
    }

    // ---- Private helpers (all blocking I/O, called on worker thread) ----

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

        if (size < AppConstants.MAX_FILE_SIZE_FOR_HASH && size > 0) {
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
