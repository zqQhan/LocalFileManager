package com.nick.filemanager.api;

import com.nick.filemanager.common.constant.AppConstants;
import com.nick.filemanager.service.DuplicateService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * REST API for duplicate file detection and cleanup.
 */
@Path(AppConstants.API_DUPLICATES)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Duplicates", description = "Duplicate file detection and cleanup")
public class DuplicateResource {

    @Inject
    DuplicateService duplicateService;

    @GET
    @Operation(summary = "List all duplicate file groups")
    public Uni<Response> listGroups() {
        return duplicateService.listGroups()
            .map(groups -> Response.ok(groups).build());
    }

    @POST
    @Path("/scan")
    @Operation(summary = "Scan a directory for duplicate files")
    public Uni<Response> scanDirectory(@QueryParam("rootPath") String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            rootPath = System.getProperty("user.home");
        }
        String finalPath = rootPath;
        return duplicateService.scanDirectory(finalPath)
            .map(results -> {
                if (results.isEmpty()) {
                    return Response.ok(Map.of(
                        "message", "No duplicates found",
                        "rootPath", finalPath
                    )).build();
                }
                return Response.ok(results).build();
            })
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage())).build());
    }

    @DELETE
    @Path("/{groupId}")
    @Operation(summary = "Clean up duplicate files in a group, keeping one copy")
    public Uni<Response> cleanGroup(
            @PathParam("groupId") Long groupId,
            @QueryParam("keepPolicy") @DefaultValue("oldest") String keepPolicy) {
        return duplicateService.cleanGroup(groupId, keepPolicy)
            .map(deleted -> Response.ok(Map.of(
                "deleted", deleted,
                "message", "Cleaned " + deleted + " duplicate file(s)"
            )).build())
            .onFailure(IllegalArgumentException.class)
                .recoverWithItem(e -> Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage())).build());
    }
}
