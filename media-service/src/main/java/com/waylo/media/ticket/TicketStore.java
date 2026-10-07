package com.waylo.media.ticket;

import com.waylo.media.config.MediaProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Тікети в Redis: вони живуть хвилини, а не роки, тож окрема таблиця в БД для
 * них була б зайвою — TTL робить прибирання сам.
 *
 * Сам тікет — 256 біт з SecureRandom, у Redis лежить лише його значення як ключ.
 */
@Component
public class TicketStore {

    private static final String PREFIX = "media:ticket:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final MediaProperties props;

    public TicketStore(StringRedisTemplate redis, JsonMapper jsonMapper, MediaProperties props) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.props = props;
    }

    public String issue(UploadTicket ticket) {
        String value = newToken();
        redis.opsForValue().set(PREFIX + value, jsonMapper.writeValueAsString(ticket), props.ticketTtl());
        return value;
    }

    public Optional<UploadTicket> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String json = redis.opsForValue().get(PREFIX + token);
        return json == null ? Optional.empty() : Optional.of(jsonMapper.readValue(json, UploadTicket.class));
    }

    /**
     * Погасити тікет. Викликаємо ПІСЛЯ успішного збереження: якщо сховище
     * моргнуло, користувач просто повторює завантаження тим самим тікетом,
     * а не починає все спочатку. Повторне використання з іншим файлом
     * перезаписало б той самий ключ — тобто своє ж фото, не чуже.
     */
    public void consume(String token) {
        redis.delete(PREFIX + token);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
