package com.nick.filemanager.service;

import com.nick.filemanager.common.constant.AppConstants;
import com.nick.filemanager.common.dto.DuplicateGroupDTO;
import com.nick.filemanager.common.dto.FileInfo;
import com.nick.filemanager.model.entity.DuplicateFile;
import com.nick.filemanager.model.entity.DuplicateGroup;
import com.nick.filemanager.repository.FileIndexRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.mutiny.redis.client.RedisAPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Duplicate file detection via SHA-256 content hashing.
 */
@ApplicationScoped
public class DuplicateService {

    @Inject
    FileIndexRepository fileRepo;

    @Inject
    RedisAPI redisAPI;

    @Inject
    io.vertx.mutiny.core.Vertx vertx;

    /** List all duplicate groups */
    @WithTransaction
    public Uni<List<DuplicateGroupDTO>> listGroups() {
        return DuplicateGroup.listAll()
            .map(groups -> groups.stream()
                .map(g -> (DuplicateGroup) g)
                .map(this::toDTO)
                .toList());
    }

    /** Scan files in a directory for duplicates — blocking I/O on worker thread,
     *  then persists the discovered groups so cleanGroup can operate on them. */
    public Uni<List<DuplicateGroupDTO>> scanDirectory(String rootPath) {
        // Capture the Vert.x event-loop context so we can return to it
        // after the blocking file I/O on the worker thread.
        var ctx = vertx.getOrCreateContext();

        // Phase 1: walk files & compute hashes on worker thread (no DB access)
        return Uni.createFrom().item(() -> {
            Map<String, List<Path>> hashMap = new HashMap<>();
            try {
                Path root = Path.of(rootPath);
                if (!Files.isDirectory(root)) {
                    throw new IllegalArgumentException("不是目录: " + rootPath);
                }
                Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<>() {
                    @Override
                    public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && Files.isReadable(file)) {
                            try {
                                if (Files.size(file) >= AppConstants.MAX_FILE_SIZE_FOR_PREVIEW * 10) {
                                    return java.nio.file.FileVisitResult.CONTINUE;
                                }
                                String hash = computeHash(file);
                                hashMap.computeIfAbsent(hash, k -> new ArrayList<>()).add(file);
                            } catch (Exception ignored) {}
                        }
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    @Override
                    public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("扫描失败: " + e.getMessage(), e);
            }

            // Build DTOs for groups with 2+ identical files
            return hashMap.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(entry -> {
                    DuplicateGroupDTO dto = new DuplicateGroupDTO();
                    dto.setContentHash(entry.getKey());
                    dto.setFileCount(entry.getValue().size());
                    long totalSize = entry.getValue().stream().mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException ex) { return 0; }
                    }).sum();
                    dto.setTotalSize(totalSize);
                    dto.setDetectedAt(LocalDateTime.now());
                    List<FileInfo> dups = entry.getValue().stream().map(p -> {
                        FileInfo fi = new FileInfo();
                        fi.setPath(p.toAbsolutePath().toString());
                        fi.setName(p.getFileName().toString());
                        try { fi.setSizeBytes(Files.size(p)); } catch (IOException ignored) {}
                        return fi;
                    }).toList();
                    dto.setDuplicates(dups);
                    return dto;
                })
                .toList();
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
        .emitOn(ctx::runOnContext)
        // Phase 2: persist discovered groups on event loop in a transaction
        .flatMap(dtos -> persistGroups(dtos));
    }

    /** Persist scanned duplicate groups so listGroups() and cleanGroup() work.
     *  Uses programmatic Panache.withTransaction() (not the annotation) because
     *  this method can be reached from a worker-thread chain. */
    Uni<List<DuplicateGroupDTO>> persistGroups(List<DuplicateGroupDTO> dtos) {
        if (dtos.isEmpty()) return Uni.createFrom().item(dtos);

        return Panache.withTransaction(() -> {
            @SuppressWarnings("unchecked")
            Uni<DuplicateGroupDTO>[] unis = dtos.stream().map(dto -> {
                DuplicateGroup group = new DuplicateGroup();
                group.contentHash = dto.getContentHash();
                group.fileCount = dto.getFileCount();
                group.detectedAt = dto.getDetectedAt();

                // Build DuplicateFile children — cascade will persist them with the group
                for (FileInfo fi : dto.getDuplicates()) {
                    DuplicateFile df = new DuplicateFile();
                    df.group = group;
                    df.path = fi.getPath();
                    df.sizeBytes = fi.getSizeBytes();
                    group.duplicates.add(df);
                }

                return group.<DuplicateGroup>persist().map(persisted -> {
                    dto.setId(persisted.id);
                    return dto;
                });
            }).toArray(Uni[]::new);

            return Uni.join().all(unis).andFailFast()
                .map(results -> dtos);
        });
    }

    /** Delete duplicate files in a group, keeping one — reactive DB ops, blocking disk ops offloaded. */
    @WithTransaction
    public Uni<Integer> cleanGroup(Long groupId, String keepPolicy) {
        var ctx = vertx.getOrCreateContext();
        return DuplicateGroup.<DuplicateGroup>findById(groupId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException("Duplicate group not found: " + groupId))
            .flatMap(group -> {
                List<DuplicateFile> files = new ArrayList<>(group.duplicates);
                if (files.isEmpty()) return Uni.createFrom().item(0);

                // Sort by path length as heuristic for age
                files.sort(Comparator.comparing(d -> d.path.length()));

                DuplicateFile keeper = "newest".equalsIgnoreCase(keepPolicy)
                    ? files.get(files.size() - 1)
                    : files.get(0);

                List<DuplicateFile> toDelete = files.stream()
                    .filter(df -> !df.equals(keeper))
                    .toList();

                // Delete files from disk on worker thread, then remove from
                // group so that orphanRemoval + CascadeType.ALL handles DB delete.
                return Multi.createFrom().iterable(toDelete)
                    .onItem().transformToUniAndConcatenate(df ->
                        Uni.createFrom().item(() -> {
                            try { Files.deleteIfExists(Path.of(df.path)); }
                            catch (IOException ignored) {}
                            return df;
                        })
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                        .emitOn(ctx::runOnContext)
                        .map(d -> {
                            group.duplicates.remove(d);  // orphanRemoval will cascade-delete from DB
                            return d;
                        })
                    )
                    .collect().asList()
                    .flatMap(results -> {
                        int count = results.size();
                        if (count > 0) {
                            group.fileCount -= count;
                            return group.<DuplicateGroup>persist().map(g -> count);
                        }
                        return Uni.createFrom().item(0);
                    });
            });
    }

    // ---- Helpers ----

    private String computeHash(Path filePath) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance(AppConstants.HASH_ALGORITHM);
            byte[] digest = md.digest(Files.readAllBytes(filePath));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private DuplicateGroupDTO toDTO(DuplicateGroup group) {
        DuplicateGroupDTO dto = new DuplicateGroupDTO();
        dto.setId(group.id);
        dto.setContentHash(group.contentHash);
        dto.setFileCount(group.fileCount);
        dto.setDetectedAt(group.detectedAt);

        List<FileInfo> dups = group.duplicates.stream().map(df -> {
            FileInfo fi = new FileInfo();
            fi.setId(df.fileIndexId);
            fi.setPath(df.path);
            fi.setName(Path.of(df.path).getFileName().toString());
            fi.setSizeBytes(df.sizeBytes);
            return fi;
        }).toList();
        dto.setDuplicates(dups);

        long totalSize = dups.stream().mapToLong(FileInfo::getSizeBytes).sum();
        dto.setTotalSize(totalSize);

        return dto;
    }
}
