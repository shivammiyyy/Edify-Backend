package com.Edify.Backend.document.mapper;

import com.Edify.Backend.document.dto.DocumentResponse;
import com.Edify.Backend.document.entity.Document;
import org.springframework.stereotype.Component;

@Component
public class DocumentMapper {

    public DocumentResponse toResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .originalFileName(document.getOriginalFileName())
                .contentType(document.getContentType())
                .size(document.getSize())
                .status(document.getStatus())
                .uploadedAt(document.getUploadedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

}
