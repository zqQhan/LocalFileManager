package com.nick.filemanager.api;

import com.nick.filemanager.common.constant.AppConstants;
import com.nick.filemanager.common.dto.FileInfo;
import com.nick.filemanager.messaging.FileIndexProducer;
import com.nick.filemanager.service.FileService;
import com.nick.filemanager.service.IndexService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * REST API for file browsing and basic file operations.
 * All blocking I/O is offloaded to worker threads by FileService.
 */
@Path(AppConstants.API_FILES)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Files", description = "File browsing, search, and CRUD operations")
public class FileResource {

    @Inject
    FileService fileService;

    @Inject
    IndexService indexService;

    @Inject
    FileIndexProducer fileIndexProducer;

    // ---- Browse directory ----

    @GET
    @Operation(summary = "Browse a directory")
    public Uni<Response> browseDirectory(
            @Parameter(description = "Absolute directory path") @QueryParam("path") String path) {
        if (path == null || path.isBlank()) {
            path = System.getProperty("user.home");
        }
        return fileService.browseDirectory(path)
            .map(files -> Response.ok(files).build())
            .onFailure().recoverWithItem(e -> Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage())).build());
    }

    // ---- Copy ----

    @POST
    @Path("/copy")
    @Operation(summary = "Copy a file or directory")
    public Uni<Response> copyFile(Map<String, String> body) {
        String source = body.get("source");
        String destination = body.get("destination");
        if (source == null || destination == null) {
            return Uni.createFrom().item(badRequest("source and destination are required"));
        }
        return fileService.copyFile(source, destination)
            .map(info -> Response.ok(info).build())
            .onFailure(SecurityException.class).recoverWithItem(e -> forbidden(e.getMessage()))
            .onFailure().recoverWithItem(e -> badRequest(e.getMessage()));
    }

    // ---- Move ----

    @POST
    @Path("/move")
    @Operation(summary = "Move or rename a file")
    public Uni<Response> moveFile(Map<String, String> body) {
        String source = body.get("source");
        String destination = body.get("destination");
        if (source == null || destination == null) {
            return Uni.createFrom().item(badRequest("source and destination are required"));
        }
        return fileService.moveFile(source, destination)
            .map(info -> Response.ok(info).build())
            .onFailure(SecurityException.class).recoverWithItem(e -> forbidden(e.getMessage()))
            .onFailure().recoverWithItem(e -> badRequest(e.getMessage()));
    }

    // ---- Delete ----

    @DELETE
    @Operation(summary = "Delete a file")
    public Uni<Response> deleteFile(
            @Parameter(description = "Absolute file path") @QueryParam("path") String path) {
        if (path == null || path.isBlank()) {
            return Uni.createFrom().item(badRequest("path is required"));
        }
        return fileService.deleteFile(path)
            .map(ok -> Response.noContent().build())
            .onFailure(SecurityException.class).recoverWithItem(e -> forbidden(e.getMessage()))
            .onFailure().recoverWithItem(e -> badRequest(e.getMessage()));
    }

    // ---- Rename ----

    @PUT
    @Path("/rename")
    @Operation(summary = "Rename a file")
    public Uni<Response> renameFile(Map<String, String> body) {
        String path = body.get("path");
        String newName = body.get("newName");
        if (path == null || newName == null) {
            return Uni.createFrom().item(badRequest("path and newName are required"));
        }
        return fileService.renameFile(path, newName)
            .map(info -> Response.ok(info).build())
            .onFailure(SecurityException.class).recoverWithItem(e -> forbidden(e.getMessage()))
            .onFailure().recoverWithItem(e -> badRequest(e.getMessage()));
    }

    // ---- Sync Indexing ----

    @POST
    @Path("/index")
    @Operation(summary = "Sync-index files from a directory into the search database (blocking)")
    public Uni<Response> indexDirectory(
            @Parameter(description = "Absolute directory path to index") @QueryParam("path") String path) {
        if (path == null || path.isBlank()) {
            return Uni.createFrom().item(badRequest("path is required"));
        }
        return indexService.indexDirectory(path)
            .map(result -> Response.ok(result.toMap()).build())
            .onFailure().recoverWithItem(e -> badRequest(e.getMessage()));
    }

    // ---- Async (Kafka) Indexing ----

    @POST
    @Path("/index/async")
    @Operation(summary = "Async-index via Kafka — queues files for background indexing")
    public Uni<Response> indexAsync(
            @Parameter(description = "Absolute directory path to index") @QueryParam("path") String path) {
        if (path == null || path.isBlank()) {
            return Uni.createFrom().item(badRequest("path is required"));
        }
        try {
            var files = java.nio.file.Files.walk(java.nio.file.Path.of(path))
                .filter(java.nio.file.Files::isRegularFile)
                .filter(java.nio.file.Files::isReadable)
                .limit(10)
                .map(p -> p.toAbsolutePath().toString())
                .toList();

            if (files.isEmpty()) {
                return Uni.createFrom().item(Response.ok(Map.of(
                    "status", "empty", "totalFiles", 0
                )).build());
            }

            // Send first file to Kafka to demonstrate connectivity
            return fileIndexProducer.sendForIndexing(files.get(0))
                .map(v -> Response.accepted(Map.of(
                    "status", "queued",
                    "sent", 1,
                    "totalFiles", files.size(),
                    "firstFile", files.get(0)
                )).build())
                .onFailure().recoverWithItem(e ->
                    Response.ok(Map.of(
                        "status", "kafka_unavailable",
                        "error", e.getMessage(),
                        "totalFiles", files.size()
                    )).build());
        } catch (Exception e) {
            return Uni.createFrom().item(badRequest(e.getMessage()));
        }
    }

    // ---- Health check ----

    @GET
    @Path("/ping")
    @Operation(summary = "Health ping")
    public Uni<Map<String, String>> ping() {
        return Uni.createFrom().item(Map.of(
            "status", "OK",
            "service", "file-manager-backend"
        ));
    }

    // ---- Helper ----

    private Response badRequest(String msg) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("error", msg)).build();
    }

    private Response forbidden(String msg) {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(Map.of("error", msg)).build();
    }
}
