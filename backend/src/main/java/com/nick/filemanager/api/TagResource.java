package com.nick.filemanager.api;

import com.nick.filemanager.common.constant.AppConstants;
import com.nick.filemanager.common.dto.TagDTO;
import com.nick.filemanager.service.TagService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.Map;

/**
 * REST API for tag / category management.
 */
@Path(AppConstants.API_TAGS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Tags", description = "Tag and category management for files")
public class TagResource {

    @Inject
    TagService tagService;

    @GET
    @Operation(summary = "List all tags")
    public Uni<Response> listAll() {
        return tagService.listAll().map(tags -> Response.ok(tags).build());
    }

    @POST
    @Operation(summary = "Create a new tag")
    public Uni<Response> create(TagDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Tag name is required")).build());
        }
        return tagService.create(dto)
            .map(tag -> Response.created(URI.create(AppConstants.API_TAGS + "/" + tag.getId())).entity(tag).build());
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a tag")
    public Uni<Response> update(@PathParam("id") Long id, TagDTO dto) {
        return tagService.update(id, dto)
            .map(tag -> Response.ok(tag).build())
            .onFailure(IllegalArgumentException.class)
                .recoverWithItem(e -> Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage())).build());
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a tag")
    public Uni<Response> delete(@PathParam("id") Long id) {
        return tagService.delete(id)
            .map(deleted -> deleted
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build());
    }

    // ---- File-Tag binding ----

    @POST
    @Path("/{tagId}/files")
    @Operation(summary = "Bind a file to a tag")
    public Uni<Response> addFile(
            @PathParam("tagId") Long tagId,
            Map<String, String> body) {
        if (body == null) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Request body is required (JSON with filePath)")).build());
        }
        String filePath = body.get("filePath");
        if (filePath == null || filePath.isBlank()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "filePath is required")).build());
        }
        return tagService.addFileToTag(tagId, filePath)
            .map(tag -> Response.ok(tag).build())
            .onFailure(IllegalArgumentException.class)
                .recoverWithItem(e -> Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build())
            .onFailure().recoverWithItem(e -> Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage() != null ? e.getMessage() : e.toString())).build());
    }

    @DELETE
    @Path("/{tagId}/files/{fileId}")
    @Operation(summary = "Remove a file from a tag")
    public Uni<Response> removeFile(
            @PathParam("tagId") Long tagId,
            @PathParam("fileId") Long fileId) {
        return tagService.removeFileFromTag(tagId, fileId)
            .map(tag -> Response.ok(tag).build())
            .onFailure(IllegalArgumentException.class)
                .recoverWithItem(e -> Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build());
    }

    @GET
    @Path("/{tagId}/files")
    @Operation(summary = "List files bound to a tag")
    public Uni<Response> listFiles(@PathParam("tagId") Long tagId) {
        return tagService.getFilesForTag(tagId)
            .map(files -> Response.ok(files).build())
            .onFailure(IllegalArgumentException.class)
                .recoverWithItem(e -> Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage())).build());
    }
}
