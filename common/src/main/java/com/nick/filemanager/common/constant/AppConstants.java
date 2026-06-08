package com.nick.filemanager.common.constant;

/**
 * Application-wide constants shared across modules.
 */
public final class AppConstants {

    private AppConstants() {}

    // API paths
    public static final String API_BASE = "/api";
    public static final String API_FILES = API_BASE + "/files";
    public static final String API_TAGS = API_BASE + "/tags";
    public static final String API_BATCH = API_BASE + "/batch";
    public static final String API_DUPLICATES = API_BASE + "/duplicates";

    // WebSocket paths
    public static final String WS_FILE_MONITOR = "/ws/file-monitor";
    public static final String WS_BATCH_PROGRESS = "/ws/batch-progress";

    // Pagination defaults
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    // File processing
    public static final int CONTENT_PREVIEW_MAX_CHARS = 2000;
    public static final long MAX_FILE_SIZE_FOR_PREVIEW = 10 * 1024 * 1024; // 10 MB
    public static final String HASH_ALGORITHM = "SHA-256";

    // Batch status
    public static final String BATCH_PENDING = "PENDING";
    public static final String BATCH_RUNNING = "RUNNING";
    public static final String BATCH_COMPLETED = "COMPLETED";
    public static final String BATCH_FAILED = "FAILED";
}
