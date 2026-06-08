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
            @QueryParam("tagId") Long tagId,
            @QueryParam("regex") @DefaultValue("false") boolean regex,
            @QueryParam("sizeMin") Long sizeMin,
            @QueryParam("sizeMax") Long sizeMax,
            @QueryParam("dateFrom") String dateFrom,
            @QueryParam("dateTo") String dateTo) {

        if (query == null || query.isBlank()) {
            return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Query parameter 'q' is required")).build());
        }

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
        sq.setRegex(regex);
        sq.setSizeMin(sizeMin);
        sq.setSizeMax(sizeMax);
        sq.setDateFrom(dateFrom);
        sq.setDateTo(dateTo);

        return searchService.search(sq)
            .map(result -> Response.ok(result).build())
            .onFailure(IllegalArgumentException.class)
                .recoverWithItem(e -> Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build())
            .onFailure().recoverWithItem(e ->
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage())).build());
    }

    // ---- Export (#8) ----

    @GET
    @Path("/export")
    @Produces("text/csv")
    @Operation(summary = "Export search results as CSV")
    public Uni<Response> export(
            @QueryParam("q") String query,
            @QueryParam("type") @DefaultValue("NAME") String type,
            @QueryParam("regex") @DefaultValue("false") boolean regex,
            @QueryParam("extension") String extension,
            @QueryParam("sizeMin") Long sizeMin,
            @QueryParam("sizeMax") Long sizeMax,
            @QueryParam("dateFrom") String dateFrom,
            @QueryParam("dateTo") String dateTo,
            @QueryParam("format") @DefaultValue("csv") String format) {

        if (query == null || query.isBlank()) {
            return Uni.createFrom().item(Response.status(400)
                .entity(Map.of("error", "Query 'q' is required")).build());
        }

        SearchQuery.SearchType searchType;
        try { searchType = SearchQuery.SearchType.valueOf(type.toUpperCase()); }
        catch (IllegalArgumentException e) {
            return Uni.createFrom().item(Response.status(400)
                .entity(Map.of("error", "Invalid type")).build());
        }

        SearchQuery sq = new SearchQuery();
        sq.setQ(query.trim());
        sq.setType(searchType);
        sq.setPage(0);
        sq.setSize(1000);
        sq.setRegex(regex);
        sq.setExtensionFilter(extension);
        sq.setSizeMin(sizeMin);
        sq.setSizeMax(sizeMax);
        sq.setDateFrom(dateFrom);
        sq.setDateTo(dateTo);

        return searchService.exportResults(sq)
            .map(files -> {
                if ("json".equalsIgnoreCase(format)) {
                    return Response.ok(files).build();
                }
                // CSV format
                StringBuilder csv = new StringBuilder("Name,Path,Extension,Size(Bytes),Modified,MIME\n");
                for (var f : files) {
                    csv.append(String.format("\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\"\n",
                        f.getName(), f.getPath(), f.getExtension(),
                        f.getSizeBytes(), f.getModifiedAt(), f.getMimeType()));
                }
                return Response.ok(csv.toString())
                    .header("Content-Disposition", "attachment; filename=search-results.csv")
                    .build();
            })
            .onFailure().recoverWithItem(e ->
                Response.status(500).entity(Map.of("error", e.getMessage())).build());
    }
}
