package com.example.ragingestor.ingestion;

import com.example.ragingestor.observability.EdifyMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.DeliveryOptions;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentIngestionEventPublisherTest {

    @SuppressWarnings("unchecked")
    @Test
    void dispatchesIngestionToVertxWhenKafkaIsDisabled() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        Vertx vertx = mock(Vertx.class);
        EventBus eventBus = mock(EventBus.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        AsyncIngestionService ingestion = mock(AsyncIngestionService.class);
        DocumentIngestionEventPublisher publisher = new DocumentIngestionEventPublisher(kafka, vertx, ingestion,
                new ObjectMapper().findAndRegisterModules(), "uploaded", "processed", "failed", false,
                new EdifyMetrics(new SimpleMeterRegistry()));
        UUID jobId = UUID.randomUUID();
        DocumentIngestionEvent event = new DocumentIngestionEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), jobId, null, "correlation-123", Instant.now());

        publisher.publishUploaded(event);

        verify(eventBus).request(eq(IngestionVerticle.ADDR_PROCESS_JOB), anyString(),
                any(DeliveryOptions.class), any());
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }
}
