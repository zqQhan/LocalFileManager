package com.nick.filemanager.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nick.filemanager.common.dto.BatchTaskDTO;
import com.nick.filemanager.model.entity.BatchTask;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.concurrent.CompletionStage;

/**
 * Consumes batch file-operation tasks from Kafka.
 */
@ApplicationScoped
public class BatchTaskConsumer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Incoming("batch-tasks-in")
    @Blocking
    public CompletionStage<Void> processBatchTask(String message) {
        try {
            BatchTaskDTO dto = MAPPER.readValue(message, BatchTaskDTO.class);

            // Create tracking entity
            BatchTask task = new BatchTask();
            task.type = dto.getType();
            task.status = "RUNNING";
            task.totalCount = dto.getTasks() != null ? dto.getTasks().size() : 0;
            task.processedCount = 0;
            task.failedCount = 0;

            task.persist().await().indefinitely();

            // Process each item
            if (dto.getTasks() != null) {
                for (BatchTaskDTO.TaskItem item : dto.getTasks()) {
                    try {
                        processItem(item);
                        task.processedCount++;
                    } catch (Exception e) {
                        task.failedCount++;
                    }
                    task.persist().await().indefinitely();
                }
            }

            task.status = task.failedCount > 0 ? "COMPLETED" : "COMPLETED";
            task.completedAt = LocalDateTime.now();
            return task.persist().subscribeAsCompletionStage().thenApply(v -> null);

        } catch (IOException e) {
            return Uni.createFrom().voidItem().subscribeAsCompletionStage();
        }
    }

    private void processItem(BatchTaskDTO.TaskItem item) throws IOException {
        Path src = Path.of(item.getSource()).toAbsolutePath().normalize();
        if (!Files.exists(src)) return;

        switch (item.getOperation().toUpperCase()) {
            case "COPY" -> {
                Path dst = Path.of(item.getDestination()).toAbsolutePath().normalize();
                if (dst.getParent() != null) Files.createDirectories(dst.getParent());
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
            case "MOVE" -> {
                Path dst = Path.of(item.getDestination()).toAbsolutePath().normalize();
                if (dst.getParent() != null) Files.createDirectories(dst.getParent());
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
            case "DELETE" -> Files.deleteIfExists(src);
        }
    }
}
