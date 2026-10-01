package com.example.ragingestor.ingestion;

import com.example.ragingestor.ingestion.dto.IngestionDtos.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import org.springframework.stereotype.Component;

@Component
public class IngestionVerticle extends AbstractVerticle {

    public static final String ADDR_CREATE = "ingestion.create";
    public static final String ADDR_UPDATE = "ingestion.update";
    public static final String ADDR_DELETE = "ingestion.delete";

    private final DocumentIngestionService ingestionService;

    public IngestionVerticle(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Override
    public void start() {
        // executeBlocking because chunking + embedding HTTP calls + JDBC writes are all
        // blocking work; keeps Vert.x's event loop free while still giving us request/reply
        // semantics back to the calling Spring controller.
        vertx.eventBus().<CreateDocumentCommand>consumer(ADDR_CREATE, this::handleCreate);
        vertx.eventBus().<UpdateDocumentCommand>consumer(ADDR_UPDATE, this::handleUpdate);
        vertx.eventBus().<DeleteDocumentCommand>consumer(ADDR_DELETE, this::handleDelete);
    }

    private void handleCreate(Message<CreateDocumentCommand> msg) {
        vertx.executeBlocking(() -> ingestionService.create(msg.body()))
                .onSuccess(msg::reply)
                .onFailure(err -> msg.fail(500, err.getMessage()));
    }

    private void handleUpdate(Message<UpdateDocumentCommand> msg) {
        vertx.executeBlocking(() -> ingestionService.update(msg.body()))
                .onSuccess(msg::reply)
                .onFailure(err -> msg.fail(failureCode(err), err.getMessage()));
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
