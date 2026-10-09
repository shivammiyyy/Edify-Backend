package com.example.ragingestor.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorageService(
            @Value("${app.storage.endpoint}") String endpoint,
            @Value("${app.storage.access-key}") String accessKey,
            @Value("${app.storage.secret-key}") String secretKey,
            @Value("${app.storage.bucket}") String bucket) {
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
    }

    @Override
    public void put(String storageKey, byte[] content, String contentType) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (MinioException | IOException | java.security.InvalidKeyException
                 | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not store document object", e);
        }
    }

    @Override
    public byte[] get(String storageKey) {
        try (var stream = client.getObject(GetObjectArgs.builder().bucket(bucket).object(storageKey).build())) {
            return stream.readAllBytes();
        } catch (MinioException | IOException | java.security.InvalidKeyException
                 | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not read document object", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storageKey).build());
        } catch (MinioException | IOException | java.security.InvalidKeyException
                 | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not remove document object", e);
        }
    }

    private void ensureBucket() throws MinioException, IOException, java.security.InvalidKeyException,
            java.security.NoSuchAlgorithmException {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
