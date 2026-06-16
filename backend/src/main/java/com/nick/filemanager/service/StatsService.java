package com.nick.filemanager.service;

import com.nick.filemanager.model.entity.FileIndex;
import com.nick.filemanager.repository.FileIndexRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.stream.Collectors;

@WithTransaction
@ApplicationScoped
public class StatsService {

    @Inject
    FileIndexRepository fileRepo;

    public Uni<Map<String, Object>> getDashboard() {
        return FileIndex.<FileIndex>listAll()
            .map(all -> {
                List<FileIndex> files = all.stream()
                    .filter(f -> !f.isDirectory).toList();

                Map<String, Object> dash = new LinkedHashMap<>();
                dash.put("totalFiles", files.size());

                long totalSize = files.stream().mapToLong(f -> f.sizeBytes).sum();
                dash.put("totalSizeBytes", totalSize);
                dash.put("totalSizeFormatted", formatSize(totalSize));

                // By extension
                Map<String, Long> byExt = files.stream()
                    .collect(Collectors.groupingBy(
                        f -> f.extension == null || f.extension.isBlank() ? "(no ext)" : f.extension,
                        Collectors.counting()));
                Map<String, Long> topExt = byExt.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
                dash.put("byExtension", topExt);

                // By MIME type
                Map<String, Long> byMime = files.stream()
                    .collect(Collectors.groupingBy(
                        f -> f.mimeType != null ? f.mimeType : "unknown", Collectors.counting()));
                dash.put("byMimeType", byMime);

                // Largest files (top 5)
                List<Map<String, Object>> largest = new ArrayList<>();
                files.stream()
                    .sorted(Comparator.comparingLong((FileIndex f) -> f.sizeBytes).reversed())
                    .limit(5).forEach(f -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", f.name); m.put("path", f.path);
                        m.put("size", formatSize(f.sizeBytes)); largest.add(m);
                    });
                dash.put("largestFiles", largest);

                // Recently modified (top 5)
                List<Map<String, Object>> recent = new ArrayList<>();
                files.stream()
                    .filter(f -> f.modifiedAt != null)
                    .sorted(Comparator.comparing((FileIndex f) -> f.modifiedAt).reversed())
                    .limit(5).forEach(f -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", f.name); m.put("path", f.path);
                        m.put("modified", f.modifiedAt.toString()); recent.add(m);
                    });
                dash.put("recentlyModified", recent);

                // Oldest files
                List<Map<String, Object>> oldest = new ArrayList<>();
                files.stream()
                    .filter(f -> f.modifiedAt != null)
                    .sorted(Comparator.comparing((FileIndex f) -> f.modifiedAt))
                    .limit(5).forEach(f -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", f.name); m.put("path", f.path);
                        m.put("modified", f.modifiedAt.toString()); oldest.add(m);
                    });
                dash.put("oldestFiles", oldest);

                // Duplicate info
                long uniqueHashes = files.stream()
                    .map(f -> f.contentHash)
                    .filter(h -> h != null && !h.isBlank()).distinct().count();
                dash.put("uniqueContentHashes", uniqueHashes);
                dash.put("potentialDuplicates", files.size() - uniqueHashes);

                return dash;
            });
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
