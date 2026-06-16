package com.nick.filemanager.model.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persistent search history record.
 */
@Entity
@Table(name = "search_history", indexes = {
    @Index(name = "idx_sh_searched_at", columnList = "searched_at")
})
public class SearchHistory extends PanacheEntity {

    @Column(nullable = false, length = 500)
    public String query;

    @Column(name = "filters_json", columnDefinition = "TEXT")
    public String filtersJson;              // JSON-serialized filter params

    @Column(name = "result_count")
    public int resultCount;

    @Column(name = "searched_at")
    public LocalDateTime searchedAt = LocalDateTime.now();
}
