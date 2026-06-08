package com.nick.filemanager.common.dto;

/**
 * Search query parameters sent from the desktop client.
 */
public class SearchQuery {

    private String q;
    private SearchType type = SearchType.NAME;
    private int page = 0;
    private int size = 20;
    private String rootPath;         // Limit search scope
    private String extensionFilter;  // Filter by extension
    private Long tagId;              // Filter by tag
    private boolean regex;           // Use regex for name search
    private Long sizeMin;            // Minimum file size filter
    private Long sizeMax;            // Maximum file size filter
    private String dateFrom;         // Modified after (ISO date)
    private String dateTo;           // Modified before (ISO date)

    public enum SearchType {
        NAME,       // Search by filename only
        CONTENT,    // Full-text content search
        ALL         // Both
    }

    public SearchQuery() {}

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }

    public SearchType getType() { return type; }
    public void setType(SearchType type) { this.type = type; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public String getRootPath() { return rootPath; }
    public void setRootPath(String rootPath) { this.rootPath = rootPath; }

    public String getExtensionFilter() { return extensionFilter; }
    public void setExtensionFilter(String extensionFilter) { this.extensionFilter = extensionFilter; }

    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }

    public boolean isRegex() { return regex; }
    public void setRegex(boolean regex) { this.regex = regex; }

    public Long getSizeMin() { return sizeMin; }
    public void setSizeMin(Long sizeMin) { this.sizeMin = sizeMin; }

    public Long getSizeMax() { return sizeMax; }
    public void setSizeMax(Long sizeMax) { this.sizeMax = sizeMax; }

    public String getDateFrom() { return dateFrom; }
    public void setDateFrom(String dateFrom) { this.dateFrom = dateFrom; }

    public String getDateTo() { return dateTo; }
    public void setDateTo(String dateTo) { this.dateTo = dateTo; }
}
