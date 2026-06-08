package com.nick.filemanager.api;

import com.nick.filemanager.service.StatsService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * #5 File statistics dashboard.
 */
@Path("/api/stats")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Stats", description = "File statistics dashboard")
public class StatsResource {

    @Inject
    StatsService statsService;

    @GET
    @Path("/dashboard")
    @Operation(summary = "Get file statistics dashboard including top types, sizes, and trends")
    public Uni<Response> dashboard() {
        return statsService.getDashboard()
            .map(d -> Response.ok(d).build())
            .onFailure().recoverWithItem(e ->
                Response.status(500).entity(Map.of("error", e.getMessage())).build());
    }
}
