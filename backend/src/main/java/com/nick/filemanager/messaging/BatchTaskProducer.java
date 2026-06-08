package com.nick.filemanager.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nick.filemanager.common.dto.BatchTaskDTO;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

/**
 * Sends batch file-operation tasks to Kafka.
 */
@ApplicationScoped
public class BatchTaskProducer {

    @Inject
    @Channel("batch-tasks-out")
    MutinyEmitter<String> emitter;

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    /**
     * Submit a batch task for async processing.
     */
    public Uni<Void> submitBatchTask(BatchTaskDTO task) {
        try {
            String json = MAPPER.writeValueAsString(task);
            return emitter.send(json).onFailure().retry().atMost(3);
        } catch (JsonProcessingException e) {
            return Uni.createFrom().failure(e);
        }
    }
}
