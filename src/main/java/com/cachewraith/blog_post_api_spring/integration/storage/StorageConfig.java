package com.cachewraith.blog_post_api_spring.integration.storage;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Picks the storage backend.
 *
 * <p>Factory Method in one bean, mirroring {@code EmailConfig}: the decision lives in a single place
 * rather than being spread across {@code @ConditionalOnProperty} annotations on two {@code @Service}
 * classes, where which one wins is easy to get subtly wrong. Selecting R2 with an incomplete
 * configuration fails the context start rather than quietly writing user uploads to a container's
 * local disk, where they vanish on the next deploy.
 */
@Slf4j
@Configuration
public class StorageConfig {

    @Bean
    public StorageService storageService(StorageProperties properties, ObjectProvider<S3Client> s3) {
        if (properties.getDriver() != StorageProperties.Driver.R2) {
            log.info("Storage driver: local ({})", properties.getBasePath());
            return new LocalStorageService(properties);
        }

        S3Client client = s3.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException(
                    "app.storage.driver=r2 but no S3 client was built; check the app.storage.r2.* values");
        }
        StorageProperties.R2 r2 = properties.getR2();
        require(r2.getBucket(), "app.storage.r2.bucket");
        require(r2.getPublicUrl(), "app.storage.r2.public-url");

        log.info("Storage driver: r2 (bucket {}, public {})", r2.getBucket(), r2.getPublicUrl());
        return new R2StorageService(properties, client);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.storage.driver", havingValue = "r2")
    public S3Client r2Client(StorageProperties properties) {
        StorageProperties.R2 r2 = properties.getR2();
        require(r2.getEndpoint(), "app.storage.r2.endpoint");
        require(r2.getAccessKeyId(), "app.storage.r2.access-key-id");
        require(r2.getSecretAccessKey(), "app.storage.r2.secret-access-key");

        return S3Client.builder()
                .endpointOverride(URI.create(r2.getEndpoint()))
                .region(Region.of(r2.getRegion()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(r2.getAccessKeyId(), r2.getSecretAccessKey())))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(r2.isPathStyleAccess()).build())
                // R2 is S3-compatible but not S3. Recent SDK versions add a CRC32 trailer to every
                // request by default, which R2 rejects on some paths; ask for a checksum only where
                // the protocol actually requires one.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    /** Fail at startup with the property name, rather than at the first upload with a 500. */
    private static void require(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be set when app.storage.driver=r2");
        }
    }
}
