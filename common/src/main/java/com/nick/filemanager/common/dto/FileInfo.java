package com.nick.filemanager.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

/**
 * Lightweight file information for API responses and desktop client.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileInfo {

    private Long id;
    private String path;
    private String name;
    private String extension;
    private long sizeBytes;
    private LocalDateTime modifiedAt;
    private String mimeType;
    private String contentHash;
    private String contentSnippet;
    private LocalDateTime indexedAt;

    // For directory browsing — populated when path is a directory
    private Boolean isDirectory;
    private Integer fileCount;
    private Long totalSize;

    public FileInfo() {}

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public LocalDateTime getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(LocalDateTime modifiedAt) { this.modifiedAt = modifiedAt; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getContentSnippet() { return contentSnippet; }
    public void setContentSnippet(String contentSnippet) { this.contentSnippet = contentSnippet; }

    public LocalDateTime getIndexedAt() { return indexedAt; }
    public void setIndexedAt(LocalDateTime indexedAt) { this.indexedAt = indexedAt; }

    public Boolean getIsDirectory() { return isDirectory; }
    public void setIsDirectory(Boolean isDirectory) { this.isDirectory = isDirectory; }

    public Integer getFileCount() { return fileCount; }
    public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }

    public Long getTotalSize() { return totalSize; }
    public void setTotalSize(Long totalSize) { this.totalSize = totalSize; }
}
