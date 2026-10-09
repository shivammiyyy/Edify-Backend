package com.example.ragingestor.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaIngestionListener {

    private static final DeliveryOptions PROCESS_TIMEOUT =
            new DeliveryOptions().setSendTimeout(TimeUnit.MINUTES.toMillis(30));

    private final Vertx vertx;
    private final ObjectMapper objectMapper;
    private final String processAddress;

    public KafkaIngestionListener(Vertx vertx, ObjectMapper objectMapper,
                                 @Value("${app.kafka.consumer-address}") String processAddress) {
        this.vertx = vertx;
        this.objectMapper = objectMapper;
        this.processAddress = processAddress;
    }

    @KafkaListener(topics = "${app.kafka.topics.uploaded}", groupId = "${app.kafka.consumer-group}")
    public void onDocumentUploaded(String json) {
        DocumentIngestionEvent event;
        try {
            event = objectMapper.readValue(json, DocumentIngestionEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid document-ingestion Kafka event", e);
        }

        try {
            vertx.eventBus().<String>request(processAddress, json, PROCESS_TIMEOUT)
                    .toCompletionStage().toCompletableFuture().get(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka ingestion handoff was interrupted", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            Throwable cause = e instanceof java.util.concurrent.ExecutionException ? e.getCause() : e;
            if (cause instanceof ReplyException reply) {
                throw new IllegalStateException("Vert.x ingestion worker failed: " + reply.getMessage(), reply);
            }
            throw new IllegalStateException("Could not process Kafka ingestion event", cause);
        }
    }
}
