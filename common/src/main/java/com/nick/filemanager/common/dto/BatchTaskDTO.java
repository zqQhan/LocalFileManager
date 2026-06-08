package com.nick.filemanager.common.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Batch task information for async bulk file operations.
 */
public class BatchTaskDTO {

    private Long id;
    private String type;               // COPY, MOVE, DELETE, RENAME
    private String status;             // PENDING, RUNNING, COMPLETED, FAILED
    private int totalCount;
    private int processedCount;
    private int failedCount;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private List<TaskItem> tasks;

    public static class TaskItem {
        private String operation;
        private String source;
        private String destination;
        private String status;

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getDestination() { return destination; }
        public void setDestination(String destination) { this.destination = destination; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public BatchTaskDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getProcessedCount() { return processedCount; }
    public void setProcessedCount(int processedCount) { this.processedCount = processedCount; }

    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public List<TaskItem> getTasks() { return tasks; }
    public void setTasks(List<TaskItem> tasks) { this.tasks = tasks; }
}
