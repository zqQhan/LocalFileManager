package com.nick.filemanager.common.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Duplicate file group information.
 */
public class DuplicateGroupDTO {

    private Long id;
    private String contentHash;
    private int fileCount;
    private long totalSize;
    private LocalDateTime detectedAt;
    private List<FileInfo> duplicates;

    public DuplicateGroupDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public int getFileCount() { return fileCount; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }

    public long getTotalSize() { return totalSize; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }

    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }

    public List<FileInfo> getDuplicates() { return duplicates; }
    public void setDuplicates(List<FileInfo> duplicates) { this.duplicates = duplicates; }
}
