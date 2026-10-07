package com.waylo.media.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    /** MinIO SDK — це звичайний S3-клієнт; сервером може бути будь-яке S3-сумісне сховище. */
    @Bean
    public MinioClient minioClient(@Value("${storage.s3.endpoint}") String endpoint,
                                   @Value("${storage.s3.access-key}") String accessKey,
                                   @Value("${storage.s3.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * Бакет створюємо самі на старті, а не init-контейнером у compose: сервіс
     * без нього однаково не працює, тож хай перевірка живе поруч із кодом.
     * Операція ідемпотентна.
     *
     * З повторами, бо MinIO в compose може піднятись на секунди пізніше за нас —
     * це дешевше, ніж гейтити старт на healthcheck чужого контейнера.
     */
    @Bean
    public BucketInitializer bucketInitializer(MinioClient client, MediaProperties props) {
        return new BucketInitializer(client, props.bucket());
    }

    public static class BucketInitializer {

        private static final int ATTEMPTS = 15;
        private static final long PAUSE_MS = 2000;

        public BucketInitializer(MinioClient client, String bucket) {
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                try {
                    if (client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                        log.info("minio: бакет '{}' на місці", bucket);
                    } else {
                        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                        log.info("minio: створено бакет '{}'", bucket);
                    }
                    return;
                } catch (Exception ex) {
                    if (attempt == ATTEMPTS) {
                        // Старт не валимо: метрики й health лишаються доступними,
                        // а завантаження відпадуть зрозумілою 503 зі сховища.
                        log.error("minio: бакет '{}' не готовий після {} спроб: {}", bucket, ATTEMPTS, ex.getMessage());
                        return;
                    }
                    log.warn("minio: спроба {}/{} не вдалась ({}) — чекаю", attempt, ATTEMPTS, ex.getMessage());
                    try {
                        Thread.sleep(PAUSE_MS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}
