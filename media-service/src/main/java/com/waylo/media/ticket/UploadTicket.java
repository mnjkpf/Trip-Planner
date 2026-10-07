package com.waylo.media.ticket;

import java.util.UUID;

/**
 * Одноразовий дозвіл на завантаження файлу.
 *
 * Права перевіряє той сервіс, який володіє сутністю (trip-service знає, хто
 * редактор подорожі). media-service про подорожі не знає нічого — він лише
 * переконується, що тікет справжній і ще не використаний. {@code context} —
 * непрозорий для нього рядок (зараз "trip:{uuid}"), який повертається назад
 * у подіях: так власник упізнає свій обʼєкт.
 */
public record UploadTicket(
        UUID mediaId,
        String context,
        String userId,
        long maxBytes
) {}
