package com.example.ragingestor.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EdifyMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final EdifyMetrics metrics = new EdifyMetrics(registry);

    @Test
    void recordsSuccessfulAndFailedOperationsSeparately() {
        assertEquals("ok", metrics.time("edify.test.duration", () -> "ok"));
        assertThrows(IllegalStateException.class, () ->
                metrics.time("edify.test.duration", () -> {
                    throw new IllegalStateException("provider unavailable");
                }));

        assertEquals(1, registry.get("edify.test.duration").tag("outcome", "success").timer().count());
        assertEquals(1, registry.get("edify.test.duration").tag("outcome", "failure").timer().count());
    }
}
