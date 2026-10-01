package com.example.ragingestor.ingestion.dto;

import com.example.ragingestor.security.Role;

import java.io.Serializable;
import java.util.UUID;

public class IngestionDtos {

    public record CreateDocumentCommand(
            String filename,
            String content,
            Role minRoleRequired,
            UUID actorUserId,
            UUID folderId
    ) implements Serializable {}

    public record UpdateDocumentCommand(
            UUID documentId,
            String newContent,
            UUID actorUserId
    ) implements Serializable {}

    public record DeleteDocumentCommand(
            UUID documentId,
            UUID actorUserId
    ) implements Serializable {}

    public record MoveDocumentCommand(
            UUID documentId,
            UUID folderId,
            UUID actorUserId
    ) implements Serializable {}

    public record IngestionResult(
            UUID documentId,
            int chunkCount,
            String status,
            String message
    ) implements Serializable {}
}
