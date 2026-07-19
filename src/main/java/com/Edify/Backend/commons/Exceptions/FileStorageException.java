package com.Edify.Backend.commons.Exceptions;

public class FileStorageException extends RuntimeException {
    public FileStorageException() {
        super("Error occurred while storing file");
    }
}
