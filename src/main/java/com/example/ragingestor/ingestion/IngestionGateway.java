package com.example.ragingestor.ingestion;

import com.example.ragingestor.ingestion.dto.IngestionDtos.*;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class IngestionGateway {

    private static final DeliveryOptions TIMEOUT = new DeliveryOptions().setSendTimeout(TimeUnit.MINUTES.toMillis(2));

    private final Vertx vertx;

    public IngestionGateway(Vertx vertx) {
        this.vertx = vertx;
    }

    public IngestionResult delete(DeleteDocumentCommand cmd) {
        return request(IngestionVerticle.ADDR_DELETE, cmd);
    }

    private <T> IngestionResult request(String address, T command) {
        CompletableFuture<IngestionResult> future = new CompletableFuture<>();
        vertx.eventBus().<IngestionResult>request(address, command, TIMEOUT, reply -> {
            if (reply.succeeded()) {
                future.complete(reply.result().body());
            } else {
                future.completeExceptionally(reply.cause());
            }
        });
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ingestion request was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ReplyException reply && reply.failureCode() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found", cause);
            }
            throw new IllegalStateException("ingestion request failed", cause);
        }
    }
}
