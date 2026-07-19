package com.Edify.Backend.document.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.UUID;

public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    public S3FileStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }


    @Override
    public String store(MultipartFile file) throws IOException {
        String extension = "";

        String original = file.getOriginalFilename();

        if (original != null && original.contains(".")) {
            extension = original.substring(original.lastIndexOf("."));
        }

        String key = UUID.randomUUID() + extension;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromInputStream(
                        file.getInputStream(),
                        file.getSize()
                )
        );

        return key;
    }

    @Override
    public byte[] load(String storagePath) throws IOException {
        GetObjectRequest request =
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(storagePath)
                        .build();

        ResponseBytes<GetObjectResponse> object =
                s3Client.getObjectAsBytes(request);

        return object.asByteArray();
    }

    @Override
    public void delete(String storagePath) throws IOException {
        DeleteObjectRequest request =
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(storagePath)
                        .build();

        s3Client.deleteObject(request);
    }

    @Override
    public boolean exists(String storagePath) {
        try {

            s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(storagePath)
                            .build()
            );

            return true;

        } catch (NoSuchKeyException e) {

            return false;

        } catch (S3Exception e) {

            return e.statusCode() != 404;
        }
    }
}
