package com.Edify.Backend.commons.Exceptions;

import com.Edify.Backend.commons.responses.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler  {

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ApiError> handleDocumentNotFound(
            DocumentNotFoundException ex
    ){

        return ResponseEntity

                .status(HttpStatus.NOT_FOUND)

                .body(

                        ApiError.builder()

                                .status(404)

                                .error("NOT_FOUND")

                                .message(ex.getMessage())

                                .timestamp(LocalDateTime.now())

                                .build()

                );

    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiError> handleStorage(

            FileStorageException ex

    ){

        return ResponseEntity

                .status(HttpStatus.INTERNAL_SERVER_ERROR)

                .body(

                        ApiError.builder()

                                .status(500)

                                .error("STORAGE_ERROR")

                                .message(ex.getMessage())

                                .timestamp(LocalDateTime.now())

                                .build()

                );

    }

}
