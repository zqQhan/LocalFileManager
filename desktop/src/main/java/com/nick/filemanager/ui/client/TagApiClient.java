package com.nick.filemanager.ui.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nick.filemanager.common.dto.TagDTO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP REST client for tag management.
 */
public class TagApiClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public TagApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** List all tags */
    public CompletableFuture<List<TagDTO>> listTags() {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/tags"))
            .GET()
            .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenApply(r -> {
                try {
                    return mapper.readValue(r.body(), new TypeReference<List<TagDTO>>() {});
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }
}
