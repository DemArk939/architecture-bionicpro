package com.example.bionicproauth.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
public class MinioService {

    private final MinioClient minioClient;
    private final String bucketName;
    private final String publicUrl;

    public MinioService(@Value("${minio.endpoint}") String endpoint,
                        @Value("${minio.access-key}") String accessKey,
                        @Value("${minio.secret-key}") String secretKey,
                        @Value("${minio.bucket-name}") String bucketName,
                        @Value("${minio.public-url}") String publicUrl) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucketName = bucketName;
        this.publicUrl = publicUrl;
        createBucketIfNotExists();
    }

    private void createBucketIfNotExists() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                // Устанавливаем публичную политику для чтения
                String policy = """
                        {
                          "Version": "2012-10-17",
                          "Statement": [
                            {
                              "Effect": "Allow",
                              "Principal": {"AWS": ["*"]},
                              "Action": ["s3:GetObject"],
                              "Resource": ["arn:aws:s3:::%s/*"]
                            }
                          ]
                        }
                        """.formatted(bucketName);
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(bucketName)
                        .config(policy)
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Minio bucket", e);
        }
    }

    public boolean objectExists(String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if (e.response().code() == 404) {
                return false;
            }
            throw new RuntimeException("Error checking object existence", e);
        } catch (Exception e) {
            throw new RuntimeException("Error checking object existence", e);
        }
    }

    public void uploadFile(String objectName, byte[] data, String contentType) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(bais, data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    public String getPublicUrl(String objectName) {
        return publicUrl + "/" + objectName;
    }
}