package com.Edify.Backend.document.service;

import com.Edify.Backend.commons.responses.PageResponse;
import com.Edify.Backend.document.dto.DocumentResponse;
import com.Edify.Backend.document.dto.UpdateDocumentRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface DocumentService {

    DocumentResponse upload(String title, MultipartFile file) throws IOException;

    PageResponse<DocumentResponse> getDocuments(Pageable pageable);

    DocumentResponse getDocument(UUID id);

    DocumentResponse updateDocument(UUID id, UpdateDocumentRequest request) throws IOException;

    void deleteDocument(UUID id) throws IOException;
}
