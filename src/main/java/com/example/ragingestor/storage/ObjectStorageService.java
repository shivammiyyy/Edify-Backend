package com.example.ragingestor.storage;

public interface ObjectStorageService {
    void put(String storageKey, byte[] content, String contentType);
    byte[] get(String storageKey);
    void delete(String storageKey);
}
