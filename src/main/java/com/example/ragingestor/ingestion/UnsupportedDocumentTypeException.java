package com.example.ragingestor.ingestion;

public class UnsupportedDocumentTypeException extends RuntimeException {
    public UnsupportedDocumentTypeException(String message) {
        super(message);
    }
}
