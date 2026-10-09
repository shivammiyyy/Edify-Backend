package com.example.ragingestor.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import com.example.ragingestor.observability.EdifyMetrics;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class DocumentIngestionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionEventPublisher.class);
    private static final DeliveryOptions DIRECT_INGESTION_TIMEOUT =
            new DeliveryOptions().setSendTimeout(TimeUnit.MINUTES.toMillis(30));
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Vertx vertx;
    private final AsyncIngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final String uploadedTopic;
    private final String processedTopic;
    private final String failedTopic;
    private final EdifyMetrics metrics;
    private final boolean kafkaEnabled;

    public DocumentIngestionEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                           Vertx vertx,
                                           AsyncIngestionService ingestionService,
                                           ObjectMapper objectMapper,
                                           @Value("${app.kafka.topics.uploaded}") String uploadedTopic,
                                           @Value("${app.kafka.topics.processed}") String processedTopic,
                                           @Value("${app.kafka.topics.failed}") String failedTopic,
                                           @Value("${app.kafka.enabled:true}") boolean kafkaEnabled,
                                           EdifyMetrics metrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.vertx = vertx;
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
        this.uploadedTopic = uploadedTopic;
        this.processedTopic = processedTopic;
        this.failedTopic = failedTopic;
        this.kafkaEnabled = kafkaEnabled;
        this.metrics = metrics;
    }

    public DocumentIngestionEvent createEvent(AsyncIngestionService.IngestionInput input) {
        return new DocumentIngestionEvent(UUID.randomUUID(), input.documentId(), input.documentVersionId(),
                input.jobId(), null, input.correlationId(), Instant.now());
    }

    public void publishUploaded(DocumentIngestionEvent event) {
        if (kafkaEnabled) {
            publish(uploadedTopic, event);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            vertx.eventBus().request(IngestionVerticle.ADDR_PROCESS_JOB, json, DIRECT_INGESTION_TIMEOUT, reply -> {
                if (reply.failed()) {
                    metrics.increment("edify.ingestion.dispatch.failures", "transport", "vertx");
                    log.error("Could not dispatch ingestion job {} over Vert.x", event.jobId(), reply.cause());
                    ingestionService.markSubmissionFailed(event.jobId(), "Document could not be processed");
                }
            });
        } catch (JsonProcessingException e) {
            metrics.increment("edify.ingestion.dispatch.failures", "transport", "vertx");
            ingestionService.markSubmissionFailed(event.jobId(), "Ingestion event could not be encoded");
            throw new IllegalStateException("Could not serialize document ingestion event", e);
        }
    }

    public void publishUploaded(AsyncIngestionService.IngestionInput input) {
        publishUploaded(createEvent(input));
    }

    public void publishProcessed(DocumentIngestionEvent event) {
        if (kafkaEnabled) publish(processedTopic, event);
    }

    public void publishFailed(DocumentIngestionEvent event) {
        if (kafkaEnabled) publish(failedTopic, event);
    }

    private void publish(String topic, DocumentIngestionEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.documentId().toString(), json).get(10, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            metrics.increment("edify.kafka.publish.failures", "topic", topic);
            throw new IllegalStateException("Could not serialize document ingestion event", e);
        } catch (InterruptedException e) {
            metrics.increment("edify.kafka.publish.failures", "topic", topic);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka event publish was interrupted", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            metrics.increment("edify.kafka.publish.failures", "topic", topic);
            throw new IllegalStateException("Could not publish document ingestion event", e);
        }
    }
}
