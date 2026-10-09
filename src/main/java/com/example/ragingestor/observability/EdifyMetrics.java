package com.example.ragingestor.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;
import java.util.function.Function;

@Component
public class EdifyMetrics {

    private final MeterRegistry registry;

    public EdifyMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> T time(String name, Supplier<T> operation) {
        return time(name, operation, result -> "success");
    }

    public <T> T time(String name, Supplier<T> operation, Function<T, String> outcomeSelector) {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "success";
        try {
            T result = operation.get();
            outcome = outcomeSelector.apply(result);
            return result;
        } catch (RuntimeException failure) {
            outcome = "failure";
            throw failure;
        } finally {
            sample.stop(registry.timer(name, "outcome", outcome));
        }
    }

    public void time(String name, Runnable operation) {
        time(name, () -> {
            operation.run();
            return null;
        });
    }

    public void increment(String name, String... tags) {
        registry.counter(name, tags).increment();
    }
}
