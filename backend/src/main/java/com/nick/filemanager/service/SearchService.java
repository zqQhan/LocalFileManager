package com.nick.filemanager.service;

import com.nick.filemanager.common.dto.FileInfo;
import com.nick.filemanager.common.dto.SearchQuery;
import com.nick.filemanager.model.entity.FileIndex;
import com.nick.filemanager.model.entity.SearchHistory;
import com.nick.filemanager.repository.FileIndexRepository;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.redis.client.RedisAPI;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Hybrid search: filename search via PostgreSQL ILIKE,
 * full-text search via PostgreSQL tsvector, with Redis caching.
 */
@ApplicationScoped
public class SearchService {

    @Inject
    FileIndexRepository fileRepo;

    @Inject
    RedisAPI redisAPI;

    @ConfigProperty(name = "filemanager.redis.search-cache-ttl", defaultValue = "300")
    long cacheTtlSeconds;

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    /**
     * Execute a search query with Redis caching and history logging.
     */
    @WithTransaction
    public Uni<SearchResult> search(SearchQuery query) {
        String cacheKey = buildCacheKey(query);

        return checkCache(cacheKey)
            .onItem().ifNotNull().transformToUni(cached ->
                Uni.createFrom().item(cached))
            .onItem().ifNull().switchTo(() -> executeDbSearch(query))
            .call(result -> cacheResult(cacheKey, result))
            .call(result -> logSearchHistory(query, result));
    }

    private Uni<SearchResult> executeDbSearch(SearchQuery query) {
        Uni<List<FileIndex>> resultsUni;
        Uni<Long> countUni;

        if (query.getType() == SearchQuery.SearchType.CONTENT) {
            resultsUni = fileRepo.fullTextSearch(query.getQ(),
                query.getPage() * query.getSize(), query.getSize());
            countUni = fileRepo.countFullTextSearch(query.getQ());
        } else if (query.isRegex()) {
            resultsUni = fileRepo.regexSearch(query.getQ(),
                query.getPage() * query.getSize(), query.getSize());
            countUni = fileRepo.countRegexSearch(query.getQ());
        } else {
            resultsUni = fileRepo.searchByName(query.getQ(),
                query.getPage() * query.getSize(), query.getSize());
            countUni = fileRepo.countByName(query.getQ());
        }

        return resultsUni.flatMap(results -> {
            // Apply advanced filters in-memory
            List<FileIndex> filtered = applyFilters(results, query);
            return Uni.createFrom().item(() -> {
                SearchResult sr = new SearchResult();
                sr.query = query.getQ();
                sr.files = filtered.stream().map(this::toFileInfo).collect(Collectors.toList());
                sr.total = filtered.size();
                sr.page = query.getPage();
                sr.size = query.getSize();
                return sr;
            });
        });
    }

    /** Apply size/date/extension filters in-memory (post-DB query) */
    private List<FileIndex> applyFilters(List<FileIndex> files, SearchQuery query) {
        // Pre-validate dates — fail fast with clear error
        var from = parseDate(query.getDateFrom(), "dateFrom");
        var to = parseDate(query.getDateTo(), "dateTo");

        var stream = files.stream();
        if (query.getExtensionFilter() != null && !query.getExtensionFilter().isBlank()) {
            stream = stream.filter(f -> f.extension != null
                && f.extension.equalsIgnoreCase(query.getExtensionFilter()));
        }
        if (query.getSizeMin() != null) {
            stream = stream.filter(f -> f.sizeBytes >= query.getSizeMin());
        }
        if (query.getSizeMax() != null) {
            stream = stream.filter(f -> f.sizeBytes <= query.getSizeMax());
        }
        if (from != null) {
            stream = stream.filter(f -> f.modifiedAt != null && !f.modifiedAt.isBefore(from));
        }
        if (to != null) {
            stream = stream.filter(f -> f.modifiedAt != null && !f.modifiedAt.isAfter(to));
        }
        return stream.toList();
    }

    private java.time.LocalDateTime parseDate(String value, String fieldName) {
        if (value == null) return null;
        try {
            return java.time.LocalDateTime.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Invalid " + fieldName + " value '" + value + "'. Use ISO format: 2026-01-01T00:00:00");
        }
    }

    /** Export all matching results (no pagination, no cache) — for CSV/JSON download */
    @WithTransaction
    public Uni<List<FileInfo>> exportResults(SearchQuery query) {
        Uni<List<FileIndex>> resultsUni;
        if (query.getType() == SearchQuery.SearchType.CONTENT) {
            resultsUni = fileRepo.fullTextSearch(query.getQ(), 0, 1000);
        } else if (query.isRegex()) {
            resultsUni = fileRepo.regexSearch(query.getQ(), 0, 1000);
        } else {
            resultsUni = fileRepo.searchByName(query.getQ(), 0, 1000);
        }
        return resultsUni.map(results ->
            applyFilters(results, query).stream()
                .map(this::toFileInfo)
                .collect(Collectors.toList()));
    }

    private Uni<Void> cacheResult(String key, SearchResult result) {
        try {
            return redisAPI.setex(key, String.valueOf(cacheTtlSeconds), MAPPER.writeValueAsString(result))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
        } catch (Exception e) {
            return Uni.createFrom().voidItem();
        }
    }

    private Uni<Void> logSearchHistory(SearchQuery query, SearchResult result) {
        SearchHistory sh = new SearchHistory();
        sh.query = query.getQ();
        sh.resultCount = result.total;
        return sh.persist().onFailure().recoverWithNull().replaceWithVoid();
    }

    // ---- Redis cache helpers ----

    private Uni<SearchResult> checkCache(String key) {
        return redisAPI.get(key)
            .map(r -> {
                if (r == null || r.toString() == null) return null;
                try { return MAPPER.readValue(r.toString(), SearchResult.class); }
                catch (Exception e) { return null; }
            })
            .onFailure().recoverWithItem((SearchResult) null);
    }

    private static String buildCacheKey(SearchQuery q) {
        return String.format("search:%s:%s:%d:%d", q.getType().name(), q.getQ(), q.getPage(), q.getSize());
    }

    private FileInfo toFileInfo(FileIndex fi) {
        FileInfo info = new FileInfo();
        info.setId(fi.id);
        info.setPath(fi.path);
        info.setName(fi.name);
        info.setExtension(fi.extension);
        info.setSizeBytes(fi.sizeBytes);
        info.setModifiedAt(fi.modifiedAt);
        info.setMimeType(fi.mimeType);
        info.setContentHash(fi.contentHash);
        info.setContentSnippet(fi.contentSnippet);
        info.setIndexedAt(fi.indexedAt);
        info.setIsDirectory(fi.isDirectory);
        return info;
    }

    /**
     * Search result container.
     */
    public static class SearchResult {
        public String query;
        public List<FileInfo> files;
        public int total;
        public int page;
        public int size;

        public int getTotalPages() {
            return size > 0 ? (int) Math.ceil((double) total / size) : 0;
        }
    }
}
