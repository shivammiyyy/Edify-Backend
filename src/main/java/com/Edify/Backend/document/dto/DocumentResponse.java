package com.Edify.Backend.document.dto;

import com.Edify.Backend.Domain.DocumentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DocumentResponse {

    private UUID id;

    private String title;

    private String originalFileName;

    private String contentType;

    private Long size;

    private DocumentStatus status;

    private LocalDateTime uploadedAt;

    private LocalDateTime updatedAt;

}
