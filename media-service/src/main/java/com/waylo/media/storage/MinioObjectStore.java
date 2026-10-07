package com.waylo.media.storage;

import com.waylo.media.config.MediaProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Optional;

/** Реалізація поверх S3 (локально — SeaweedFS) через MinIO SDK. */
@Component
public class MinioObjectStore implements ObjectStore {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStore.class);
    /** Межа частини для multipart-завантаження; -1 = хай клієнт вирішує сам. */
    private static final long PART_SIZE = -1;

    private final MinioClient client;
    private final String bucket;

    public MinioObjectStore(MinioClient client, MediaProperties props) {
        this.client = client;
        this.bucket = props.bucket();
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(in, bytes.length, PART_SIZE)
                    .contentType(contentType)
                    .build());
            log.info("minio: {} ← {} байт ({})", key, bytes.length, contentType);
        } catch (Exception ex) {
            throw new StorageException("не вдалося зберегти " + key, ex);
        }
    }

    @Override
    public Optional<StoredObject> get(String key) {
        StatObjectResponse stat;
        try {
            stat = client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (ErrorResponseException ex) {
            return Optional.empty();       // немає ключа — не помилка, а відповідь
        } catch (Exception ex) {
            throw new StorageException("не вдалося прочитати метадані " + key, ex);
        }
        try {
            GetObjectResponse stream = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(key).build());
            return Optional.of(new StoredObject(stream, stat.contentType(), stat.size()));
        } catch (Exception ex) {
            throw new StorageException("не вдалося прочитати " + key, ex);
        }
    }

    @Override
    public byte[] readAll(String key) {
        // MinIO кидає цілий перелік перевірюваних винятків — загортаємо їх в один свій.
        try (GetObjectResponse stream = client.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(key).build())) {
            return stream.readAllBytes();
        } catch (Exception ex) {
            throw new StorageException("не вдалося прочитати " + key, ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
            log.info("minio: {} видалено", key);
        } catch (Exception ex) {
            throw new StorageException("не вдалося видалити " + key, ex);
        }
    }

    /** Помилка сховища — щоб обробник не ловив перелік чужих типів MinIO. */
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
