package com.nick.filemanager.repository;

import com.nick.filemanager.model.entity.FileIndex;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Comparator;
import java.util.List;

/**
 * Reactive repository for FileIndex entity.
 * Provides common query methods via Panache active-record / repository pattern.
 */
@ApplicationScoped
public class FileIndexRepository implements PanacheRepositoryBase<FileIndex, Long> {

    /** Find by exact path */
    public Uni<FileIndex> findByPath(String path) {
        return find("path", path).firstResult();
    }

    /** Search files by name pattern (ILIKE for case-insensitive).
     *  Results are ranked: exact match → starts-with → alphabetical. */
    public Uni<List<FileIndex>> searchByName(String query, int offset, int limit) {
        String pattern = "%" + query + "%";
        String qLower = query.toLowerCase();
        return find(
            "LOWER(name) LIKE LOWER(?1) ORDER BY " +
            "CASE WHEN LOWER(name) = ?2 THEN 0 WHEN LOWER(name) LIKE ?3 THEN 1 ELSE 2 END, name",
            pattern, qLower, qLower + "%")
            .range(offset, offset + limit - 1)
            .list();
    }

    /** Count files matching name pattern */
    public Uni<Long> countByName(String query) {
        return count("LOWER(name) LIKE LOWER(?1)", "%" + query + "%");
    }

    /** Files by extension */
    public Uni<List<FileIndex>> findByExtension(String extension, int offset, int limit) {
        return find("extension = ?1", extension.toLowerCase())
            .range(offset, offset + limit - 1)
            .list();
    }

    /** Files tagged with a given tag */
    public Uni<List<FileIndex>> findByTagId(Long tagId, int offset, int limit) {
        return find("select f from FileIndex f join f.tags t where t.id = ?1", tagId)
            .range(offset, offset + limit - 1)
            .list();
    }

    /** Find entries having the same content hash (duplicates) */
    public Uni<List<FileIndex>> findByContentHash(String hash) {
        return find("contentHash = ?1 AND contentHash IS NOT NULL AND contentHash <> ''", hash)
            .list();
    }

    /** Delete entry by path */
    public Uni<Long> deleteByPath(String path) {
        return delete("path", path);
    }

    /** Find all files in a directory (LIKE path prefix) */
    public Uni<List<FileIndex>> findChildrenOf(String parentPath, int offset, int limit) {
        String prefix = parentPath.endsWith(java.io.File.separator) ? parentPath : parentPath + java.io.File.separator;
        return find("path LIKE ?1", prefix + "%")
            .range(offset, offset + limit - 1)
            .list();
    }

    /** Regex search — uses PostgreSQL native ~* operator for server-side
     *  case-insensitive regex matching. This avoids loading all records into
     *  memory and sidesteps Hibernate Reactive internal state issues. */
    public Uni<List<FileIndex>> regexSearch(String pattern, int offset, int limit) {
        return getSession().flatMap(session ->
            session.createNativeQuery(
                "SELECT * FROM file_index WHERE name ~* ?1 ORDER BY name OFFSET ?2 LIMIT ?3",
                FileIndex.class)
                .setParameter(1, pattern)
                .setParameter(2, offset)
                .setParameter(3, limit)
                .getResultList());
    }

    public Uni<Long> countRegexSearch(String pattern) {
        return getSession().flatMap(session ->
            session.createNativeQuery(
                "SELECT count(*) FROM file_index WHERE name ~* ?1")
                .setParameter(1, pattern)
                .getSingleResult()
                .map(r -> ((Number) r).longValue()));
    }

    /** Full-text search on content snippet (ILIKE for HQL compatibility), ordered by name */
    public Uni<List<FileIndex>> fullTextSearch(String query, int offset, int limit) {
        return find("LOWER(contentSnippet) LIKE LOWER(?1) ORDER BY name", "%" + query + "%")
            .range(offset, offset + limit - 1)
            .list();
    }

    public Uni<Long> countFullTextSearch(String query) {
        return count("LOWER(contentSnippet) LIKE LOWER(?1)", "%" + query + "%");
    }
}
