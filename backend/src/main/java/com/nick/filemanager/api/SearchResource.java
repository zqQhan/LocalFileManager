package com.nick.filemanager.api;

import com.nick.filemanager.common.constant.AppConstants;
import com.nick.filemanager.common.dto.SearchQuery;
import com.nick.filemanager.service.SearchService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * REST API for file search (filename + full-text content).
 */
@Path(AppConstants.API_FILES + "/search")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Search", description = "File name and content search with Redis caching")
public class SearchResource {

    @Inject
    SearchService searchService;

    @GET
    @Operation(summary = "Search files by name or content")
    public Uni<Response> search(
            @QueryParam("q") String query,
            @QueryParam("type") @DefaultValue("NAME") String type,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("rootPath") String rootPath,
            @QueryParam("extension") String extension,
            @QueryParam("tagId") Long tagId) {

        if (query == null || query.isBlank()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Query parameter 'q' is required")).build());
        }

        // Validate search type with safe fallback
        SearchQuery.SearchType searchType;
        try {
            searchType = SearchQuery.SearchType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid search type: '" + type + "'. Use NAME, CONTENT, or ALL")).build());
        }

        SearchQuery sq = new SearchQuery();
        sq.setQ(query.trim());
        sq.setType(searchType);
        sq.setPage(page);
        sq.setSize(Math.min(size, AppConstants.MAX_PAGE_SIZE));
        sq.setRootPath(rootPath);
        sq.setExtensionFilter(extension);
        sq.setTagId(tagId);

        return searchService.search(sq)
            .map(result -> Response.ok(result).build())
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage())).build());
    }
}
