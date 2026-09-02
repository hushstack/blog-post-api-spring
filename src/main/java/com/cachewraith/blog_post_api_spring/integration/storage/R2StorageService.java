package com.cachewraith.blog_post_api_spring.integration.storage;

import com.cachewraith.blog_post_api_spring.common.exception.BusinessException;
import com.cachewraith.blog_post_api_spring.common.exception.ErrorCode;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Cloudflare R2, reached over its S3-compatible API.
 *
 * <p>Validation of the upload itself lives in {@link AbstractStorageService}; what is left here is
 * only the bucket mechanics. Two hosts are involved and only one of them is ever handed to a
 * client: requests are signed against the private {@code endpoint}, while the URL stored on a row —
 * and returned in a response — is built from the public r2.dev or custom domain.
 *
 * <p>Selected by {@code app.storage.driver=r2}; see {@link StorageConfig}.
 */
@Slf4j
public class R2StorageService extends AbstractStorageService {

    private final S3Client client;
    private final StorageProperties.R2 config;

    public R2StorageService(StorageProperties properties, S3Client client) {
        super(properties);
        this.client = client;
        this.config = properties.getR2();
    }

    @Override
    protected String publicBaseUrl() {
        return config.getPublicUrl();
    }

    @Override
    protected void write(String key, byte[] content, String contentType) {
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(config.getBucket())
                            .key(key)
                            // Set explicitly: without it R2 stores application/octet-stream and the
                            // public URL downloads the file instead of rendering it.
                            .contentType(contentType)
                            .cacheControl(config.getCacheControl())
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException ex) {
            // The bucket name and endpoint would be in the SDK message; log it, do not return it.
            log.error("Failed to upload object {} to R2", key, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not store file");
        }
    }

    @Override
    public Optional<byte[]> read(String url) {
        String key = keyFor(url).orElse(null);
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    client
                            .getObjectAsBytes(
                                    GetObjectRequest.builder().bucket(config.getBucket()).key(key).build())
                            .asByteArray());
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (SdkException ex) {
            log.warn("Could not read R2 object for url {}", url, ex);
            return Optional.empty();
        }
    }

    @Override
    public void replace(String url, byte[] content) {
        String key =
                keyFor(url)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.INVALID_IMAGE, "Unknown storage url"));

        if (!exists(key)) {
            // Nothing to replace: the object was deleted between upload and processing. Writing
            // anyway would resurrect an image whose owning row is already gone.
            return;
        }
        // An overwrite is a plain PUT at the same key, so every row referencing the URL stays valid.
        // The content type is derived from the key rather than carried over, because the
        // image.process stage only ever re-encodes to the format the extension already names.
        write(key, content, contentTypeForKey(key));
    }

    @Override
    public void delete(String url) {
        String key = keyFor(url).orElse(null);
        if (key == null) {
            return;
        }
        try {
            client.deleteObject(
                    DeleteObjectRequest.builder().bucket(config.getBucket()).key(key).build());
        } catch (SdkException ex) {
            // A leaked object is not worth failing the user's delete over; the row is already gone.
            log.warn("Could not delete R2 object for url {}", url, ex);
        }
    }

    private boolean exists(String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(config.getBucket()).key(key).build());
            return true;
        } catch (S3Exception ex) {
            // HeadObject has no response body, so a missing key can surface either as the mapped
            // NoSuchKeyException or as a bare 404. Anything else is a real fault and must propagate.
            if (ex instanceof NoSuchKeyException || ex.statusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }
}
