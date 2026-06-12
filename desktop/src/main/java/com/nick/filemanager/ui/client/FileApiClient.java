package com.nick.filemanager.ui.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nick.filemanager.common.dto.FileInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP REST client for file operations.
 * Uses java.net.http.HttpClient (Java 11+ built-in).
 */
public class FileApiClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public FileApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** Browse a local directory */
    public CompletableFuture<List<FileInfo>> browseDirectory(String path) {
        String url = baseUrl + "/api/files?path=" + urlEncode(path);
        return getAsync(url, new TypeReference<List<FileInfo>>() {});
    }

    /** Search files by name, content, or both. */
    public CompletableFuture<List<FileInfo>> search(String query, String type, int page, int size) {
        return search(query, type, page, size, false, null);
    }

    /** Search files with advanced filters supported by the backend. */
    public CompletableFuture<List<FileInfo>> search(
            String query,
            String type,
            int page,
            int size,
            boolean regex,
            String extension) {
        StringBuilder url = new StringBuilder(String.format(
            "%s/api/files/search?q=%s&type=%s&page=%d&size=%d&regex=%s",
            baseUrl, urlEncode(query), urlEncode(type), page, size, regex));
        if (extension != null && !extension.isBlank()) {
            url.append("&extension=").append(urlEncode(extension.trim().replaceFirst("^\\.", "")));
        }
        return getAsync(url.toString(), new TypeReference<Map<String, Object>>() {})
            .thenApply(map -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> files = (List<Map<String, Object>>) map.get("files");
                if (files == null) return new ArrayList<>();
                return files.stream().map(f -> mapper.convertValue(f, FileInfo.class)).toList();
            });
    }

    /** Export search results as CSV or JSON text. */
    public CompletableFuture<String> exportSearch(String query, String type, boolean regex, String extension, String format) {
        StringBuilder url = new StringBuilder(String.format(
            "%s/api/files/search/export?q=%s&type=%s&regex=%s&format=%s",
            baseUrl, urlEncode(query), urlEncode(type), regex, urlEncode(format)));
        if (extension != null && !extension.isBlank()) {
            url.append("&extension=").append(urlEncode(extension.trim().replaceFirst("^\\.", "")));
        }
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url.toString()))
            .GET()
            .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(this::bodyOrThrow);
    }

    /** Sync-index a directory so search has fresh data. */
    public CompletableFuture<Map<String, Object>> indexDirectory(String path) {
        String url = baseUrl + "/api/files/index?path=" + urlEncode(path);
        return postUrlAsync(url, "", new TypeReference<Map<String, Object>>() {});
    }

    /** Async-index via Kafka when the infrastructure is available. */
    public CompletableFuture<Map<String, Object>> indexDirectoryAsync(String path) {
        String url = baseUrl + "/api/files/index/async?path=" + urlEncode(path);
        return postUrlAsync(url, "", new TypeReference<Map<String, Object>>() {});
    }

    /** Backend dashboard statistics. */
    public CompletableFuture<Map<String, Object>> getDashboard() {
        return getAsync(baseUrl + "/api/stats/dashboard", new TypeReference<Map<String, Object>>() {});
    }

    /** Copy a file */
    public CompletableFuture<FileInfo> copyFile(String source, String destination) {
        String body = String.format("{\"source\":\"%s\",\"destination\":\"%s\"}",
            escapeJson(source), escapeJson(destination));
        return postAsync("/api/files/copy", body, new TypeReference<FileInfo>() {});
    }

    /** Move a file */
    public CompletableFuture<FileInfo> moveFile(String source, String destination) {
        String body = String.format("{\"source\":\"%s\",\"destination\":\"%s\"}",
            escapeJson(source), escapeJson(destination));
        return postAsync("/api/files/move", body, new TypeReference<FileInfo>() {});
    }

    /** Rename a file */
    public CompletableFuture<FileInfo> renameFile(String path, String newName) {
        String body = String.format("{\"path\":\"%s\",\"newName\":\"%s\"}",
            escapeJson(path), escapeJson(newName));
        return putAsync("/api/files/rename", body, new TypeReference<FileInfo>() {});
    }

    /** Delete a file */
    public CompletableFuture<Void> deleteFile(String path) {
        String url = baseUrl + "/api/files?path=" + urlEncode(path);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .DELETE()
            .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(r -> {
                bodyOrThrow(r);
                return null;
            });
    }

    /** Scan for duplicates. The backend may return either a list or an informational object. */
    public CompletableFuture<Object> scanDuplicates(String rootPath) {
        String url = baseUrl + "/api/duplicates/scan?rootPath=" + urlEncode(rootPath);
        return postUrlAsync(url, "", new TypeReference<Object>() {});
    }

    // ---- Internal HTTP helpers ----

    private <T> CompletableFuture<T> getAsync(String url, TypeReference<T> typeRef) {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        return sendAsync(req, typeRef);
    }

    private <T> CompletableFuture<T> postAsync(String path, String body, TypeReference<T> typeRef) {
        return postUrlAsync(baseUrl + path, body, typeRef);
    }

    private <T> CompletableFuture<T> postUrlAsync(String url, String body, TypeReference<T> typeRef) {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        return sendAsync(req, typeRef);
    }

    private <T> CompletableFuture<T> putAsync(String path, String body, TypeReference<T> typeRef) {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        return sendAsync(req, typeRef);
    }

    private <T> CompletableFuture<T> sendAsync(HttpRequest req, TypeReference<T> typeRef) {
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply(r -> {
                try {
                    String body = bodyOrThrow(r);
                    if (body == null || body.isEmpty()) return null;
                    return mapper.readValue(body, typeRef);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    private String bodyOrThrow(HttpResponse<String> response) {
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    // ---- Utilities ----

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
