package com.nick.filemanager.api;

import com.nick.filemanager.service.FileRuleService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * #9 Custom file operation rules.
 */
@Path("/api/rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Rules", description = "Custom file operation rules (auto-cleanup, auto-organize)")
public class FileRuleResource {

    @Inject
    FileRuleService ruleService;

    @GET
    @Operation(summary = "List all file rules")
    public Uni<Response> listAll() {
        return ruleService.listAll().map(rules -> Response.ok(rules).build());
    }

    @POST
    @Operation(summary = "Create a file rule")
    public Uni<Response> create(Map<String, Object> body) {
        return ruleService.create(body)
            .map(rule -> Response.ok(rule).build())
            .onFailure(IllegalArgumentException.class)
                .recoverWithItem(e -> Response.status(400).entity(Map.of("error", e.getMessage())).build());
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a file rule")
    public Uni<Response> update(@PathParam("id") Long id, Map<String, Object> body) {
        return ruleService.update(id, body)
            .map(rule -> Response.ok(rule).build())
            .onFailure(IllegalArgumentException.class)
                .recoverWithItem(e -> Response.status(400).entity(Map.of("error", e.getMessage())).build());
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a file rule")
    public Uni<Response> delete(@PathParam("id") Long id) {
        return ruleService.delete(id)
            .map(ok -> Response.noContent().build());
    }

    @POST
    @Path("/{id}/execute")
    @Operation(summary = "Execute a rule immediately")
    public Uni<Response> execute(@PathParam("id") Long id) {
        return ruleService.execute(id)
            .map(result -> Response.ok(result).build())
            .onFailure(IllegalArgumentException.class)
                .recoverWithItem(e -> Response.status(400).entity(Map.of("error", e.getMessage())).build());
    }

    @POST
    @Path("/execute-all")
    @Operation(summary = "Execute all enabled rules")
    public Uni<Response> executeAll() {
        return ruleService.executeAll()
            .map(results -> Response.ok(Map.of("executed", results.size(), "results", results)).build());
    }
}
