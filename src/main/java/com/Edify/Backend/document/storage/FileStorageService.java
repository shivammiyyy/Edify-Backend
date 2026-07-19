package com.Edify.Backend.document.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {
    String store(MultipartFile file) throws IOException;

    byte[] load(String storagePath) throws IOException;

    void delete(String storagePath) throws IOException;

    boolean exists(String storagePath);
}
