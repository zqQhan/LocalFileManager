package com.nick.filemanager.api;

import com.nick.filemanager.common.constant.AppConstants;
import com.nick.filemanager.service.BatchService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * REST API for batch file operations.
 */
@Path(AppConstants.API_BATCH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Batch", description = "Batch file operations (copy, move, delete)")
public class BatchResource {

    @Inject
    BatchService batchService;

    @POST
    @Operation(summary = "Submit and execute a batch task")
    public Uni<Response> submitBatch(Map<String, Object> body) {
        return batchService.submit(body)
            .map(task -> Response.ok(Map.of(
                "id", task.id,
                "type", task.type,
                "status", task.status,
                "total", task.totalCount,
                "processed", task.processedCount,
                "failed", task.failedCount
            )).build())
            .onFailure(IllegalArgumentException.class)
                .recoverWithItem(e -> Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build());
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get batch task status")
    public Uni<Response> getStatus(@PathParam("id") Long id) {
        return batchService.getStatus(id)
            .onItem().ifNotNull().transform(task -> Response.ok(Map.of(
                "id", task.id,
                "type", task.type,
                "status", task.status,
                "total", task.totalCount,
                "processed", task.processedCount,
                "failed", task.failedCount,
                "createdAt", task.createdAt,
                "completedAt", task.completedAt
            )).build())
            .onItem().ifNull().continueWith(() ->
                Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Batch task not found: " + id)).build());
    }
}
