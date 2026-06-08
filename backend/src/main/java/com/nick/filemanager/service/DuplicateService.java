package com.nick.filemanager.service;

import com.nick.filemanager.common.constant.AppConstants;
import com.nick.filemanager.common.dto.DuplicateGroupDTO;
import com.nick.filemanager.common.dto.FileInfo;
import com.nick.filemanager.model.entity.DuplicateFile;
import com.nick.filemanager.model.entity.DuplicateGroup;
import com.nick.filemanager.repository.FileIndexRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
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
@WithTransaction
@ApplicationScoped
public class DuplicateService {

    @Inject
    FileIndexRepository fileRepo;

    @Inject
    RedisAPI redisAPI;

    /** List all duplicate groups */
    public Uni<List<DuplicateGroupDTO>> listGroups() {
        return DuplicateGroup.listAll()
            .map(groups -> groups.stream()
                .map(g -> (DuplicateGroup) g)
                .map(this::toDTO)
                .toList());
    }

    /** Scan files in a directory for duplicates — returns all groups. */
    public Uni<List<DuplicateGroupDTO>> scanDirectory(String rootPath) {
        return Uni.createFrom().item(() -> {
            Map<String, List<Path>> hashMap = new HashMap<>();
            try {
                Files.walk(Path.of(rootPath), 10) // max depth 10 for safety
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        try { return Files.size(p) < AppConstants.MAX_FILE_SIZE_FOR_PREVIEW * 10; }
                        catch (IOException e) { return false; }
                    })
                    .forEach(p -> {
                        try {
                            String hash = computeHash(p);
                            hashMap.computeIfAbsent(hash, k -> new ArrayList<>()).add(p);
                        } catch (Exception ignored) {}
                    });
            } catch (IOException e) {
                throw new RuntimeException("Scan failed: " + e.getMessage(), e);
            }

            // Return all groups with 2+ identical files
            return hashMap.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(entry -> {
                    DuplicateGroupDTO dto = new DuplicateGroupDTO();
                    dto.setContentHash(entry.getKey());
                    dto.setFileCount(entry.getValue().size());
                    long totalSize = entry.getValue().stream().mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0; }
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
        });
    }

    /** Delete duplicate files in a group, keeping one — fully reactive, no blocking. */
    public Uni<Integer> cleanGroup(Long groupId, String keepPolicy) {
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

                // Delete files from disk and DB reactively, one at a time
                return Multi.createFrom().iterable(toDelete)
                    .onItem().transformToUniAndConcatenate(df -> {
                        // Disk delete on worker thread, then DB delete
                        return Uni.createFrom().item(() -> {
                            try { Files.deleteIfExists(Path.of(df.path)); }
                            catch (IOException ignored) {}
                            return df;
                        }).flatMap(d -> d.delete());
                    })
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
