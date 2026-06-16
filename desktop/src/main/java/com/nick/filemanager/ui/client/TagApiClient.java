package com.nick.filemanager.ui.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nick.filemanager.common.dto.FileInfo;
import com.nick.filemanager.common.dto.TagDTO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

    /** List files bound to a specific tag */
    public CompletableFuture<List<FileInfo>> getFilesForTag(long tagId) {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/tags/" + tagId + "/files"))
            .GET()
            .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(r -> {
                if (r.statusCode() >= 400) {
                    throw new RuntimeException("HTTP " + r.statusCode() + ": " + r.body());
                }
                try {
                    return mapper.readValue(r.body(), new TypeReference<List<FileInfo>>() {});
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    /** Bind a file to a tag by tag ID and file path */
    public CompletableFuture<TagDTO> bindFileToTag(Long tagId, String filePath) {
        String body = String.format("{\"filePath\":\"%s\"}",
            filePath.replace("\\", "\\\\").replace("\"", "\\\""));
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/tags/" + tagId + "/files"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(r -> {
                if (r.statusCode() >= 400) {
                    throw new RuntimeException("HTTP " + r.statusCode() + ": " + r.body());
                }
                try {
                    return mapper.readValue(r.body(), TagDTO.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    /** Unbind a file from a tag by tag ID and file index ID */
    public CompletableFuture<TagDTO> unbindFileFromTag(Long tagId, Long fileId) {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/tags/" + tagId + "/files/" + fileId))
            .DELETE()
            .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(r -> {
                if (r.statusCode() >= 400) {
                    throw new RuntimeException("HTTP " + r.statusCode() + ": " + r.body());
                }
                try {
                    return mapper.readValue(r.body(), TagDTO.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    /** Create a new tag */
    public CompletableFuture<TagDTO> createTag(String name, String color, String description) {
        StringBuilder body = new StringBuilder("{\"name\":\"")
            .append(name.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        if (color != null && !color.isBlank()) {
            body.append(",\"color\":\"").append(color.replace("\"", "\\\"")).append("\"");
        }
        if (description != null && !description.isBlank()) {
            body.append(",\"description\":\"").append(description.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        body.append("}");
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/tags"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(r -> {
                if (r.statusCode() >= 400) {
                    throw new RuntimeException("HTTP " + r.statusCode() + ": " + r.body());
                }
                try {
                    return mapper.readValue(r.body(), TagDTO.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    /** Update an existing tag */
    public CompletableFuture<TagDTO> updateTag(Long id, String name, String color, String description) {
        StringBuilder body = new StringBuilder("{");
        boolean first = true;
        if (name != null && !name.isBlank()) {
            body.append("\"name\":\"").append(name.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            first = false;
        }
        if (color != null) {
            if (!first) body.append(",");
            body.append("\"color\":\"").append(color.replace("\"", "\\\"")).append("\"");
            first = false;
        }
        if (description != null) {
            if (!first) body.append(",");
            body.append("\"description\":\"").append(description.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        body.append("}");
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/tags/" + id))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(r -> {
                if (r.statusCode() >= 400) {
                    throw new RuntimeException("HTTP " + r.statusCode() + ": " + r.body());
                }
                try {
                    return mapper.readValue(r.body(), TagDTO.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    /** Delete a tag by ID */
    public CompletableFuture<Boolean> deleteTag(Long id) {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/tags/" + id))
            .DELETE()
            .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(r -> r.statusCode() == 204 || r.statusCode() == 200);
    }
}
