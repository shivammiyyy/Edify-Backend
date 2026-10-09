package com.example.ragingestor.ingestion;

import com.example.ragingestor.ingestion.dto.IngestionDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import org.springframework.stereotype.Component;

@Component
public class IngestionVerticle extends AbstractVerticle {

    public static final String ADDR_DELETE = "ingestion.delete";
    public static final String ADDR_PROCESS_JOB = "ingestion.process-job";

    private final DocumentIngestionService ingestionService;
    private final IngestionProcessor ingestionProcessor;
    private final ObjectMapper objectMapper;

    public IngestionVerticle(DocumentIngestionService ingestionService, IngestionProcessor ingestionProcessor,
                             ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.ingestionProcessor = ingestionProcessor;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start() {
        // Kafka delivery is handed to a blocking worker so extraction, embeddings, and JDBC
        // never block the Vert.x event loop.
        vertx.eventBus().<DeleteDocumentCommand>consumer(ADDR_DELETE, this::handleDelete);
        vertx.eventBus().<String>consumer(ADDR_PROCESS_JOB, this::handleProcessJob);
    }

    private void handleProcessJob(Message<String> msg) {
        vertx.executeBlocking(() -> {
            try {
                ingestionProcessor.process(objectMapper.readValue(msg.body(), DocumentIngestionEvent.class));
                return "processed";
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid ingestion event on Vert.x event bus", e);
            }
        }).onSuccess(msg::reply)
                .onFailure(error -> msg.fail(500, error.getMessage()));
    }

    private void handleDelete(Message<DeleteDocumentCommand> msg) {
        vertx.executeBlocking(() -> ingestionService.delete(msg.body()))
                .onSuccess(msg::reply)
                .onFailure(err -> msg.fail(failureCode(err), err.getMessage()));
    }

    private static int failureCode(Throwable error) {
        return error instanceof DocumentNotFoundException ? 404 : 500;
    }
}
