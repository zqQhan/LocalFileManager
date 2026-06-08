package com.nick.filemanager.messaging;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

/**
 * Sends file-indexing tasks to Kafka for async processing.
 */
@ApplicationScoped
public class FileIndexProducer {

    @Inject
    @Channel("file-index-out")
    MutinyEmitter<String> emitter;

    /**
     * Queue a file path for async indexing.
     */
    public Uni<Void> sendForIndexing(String filePath) {
        return emitter.send(filePath).onFailure().retry().atMost(3);
    }
}
