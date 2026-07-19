package com.Edify.Backend.document.service.impl;

import com.Edify.Backend.Domain.DocumentStatus;
import com.Edify.Backend.User.entity.User;
import com.Edify.Backend.commons.responses.PageMapper;
import com.Edify.Backend.commons.responses.PageResponse;
import com.Edify.Backend.document.dto.DocumentResponse;
import com.Edify.Backend.document.dto.UpdateDocumentRequest;
import com.Edify.Backend.document.entity.Document;
import com.Edify.Backend.document.mapper.DocumentMapper;
import com.Edify.Backend.document.repository.DocumentRepository;
import com.Edify.Backend.document.service.DocumentService;
import com.Edify.Backend.document.service.FileStorageService;
import com.Edify.Backend.security.CurrentUserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository repository;

    private final DocumentMapper mapper;

    private final FileStorageService storageService;


    private final CurrentUserService currentUserService;

    @Override
    public DocumentResponse upload(
            String title,
            MultipartFile file
    ) throws IOException {

        User user = currentUserService.getCurrentUser();

        String storedFile =
                storageService.store(file);

        Document document =
                Document.builder()

                        .title(title)

                        .originalFileName(
                                file.getOriginalFilename()
                        )

                        .storedFileName(storedFile)

                        .storagePath(storedFile)

                        .contentType(file.getContentType())

                        .size(file.getSize())

                        .status(DocumentStatus.UPLOADING)

                        .owner(user)

                        .build();

        repository.save(document);

        return mapper.toResponse(document);
    }

    @Override
    public PageResponse<DocumentResponse> getDocuments(Pageable pageable) {

        User user =
                currentUserService.getCurrentUser();

        return PageMapper.toPageResponse(
                repository
                        .findAllByOwner(user, pageable)
                        .map(mapper::toResponse)
        );
    }

    @Override
    public DocumentResponse getDocument(UUID id) {

        User user =
                currentUserService.getCurrentUser();

        Document document =
                repository.findById(id)
                        .orElseThrow(() ->
                                new EntityNotFoundException("Document not found"));

        if (!document.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        return mapper.toResponse(document);
    }

    @Override
    public DocumentResponse updateDocument(
            UUID id,
            UpdateDocumentRequest request
    ) {

        User user =
                currentUserService.getCurrentUser();

        Document document =
                repository.findById(id)
                        .orElseThrow();

        if (!document.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        document.setTitle(request.getTitle());

        repository.save(document);

        return mapper.toResponse(document);
    }

    @Override
    public void deleteDocument(UUID id)
            throws IOException {

        User user =
                currentUserService.getCurrentUser();

        Document document =
                repository.findById(id)
                        .orElseThrow();

        if (!document.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        storageService.delete(document.getStoragePath());

        repository.delete(document);
    }

}
