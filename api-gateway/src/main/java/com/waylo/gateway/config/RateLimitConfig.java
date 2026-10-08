package com.waylo.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Ключ для rate-limiter'а: автентифікованого користувача лімітуємо за його id (sub з JWT),
 * анонімні запити (логін/реєстрація) — за IP. Ключ НІКОЛИ не порожній, інакше RequestRateLimiter
 * із deny-empty-key відхилив би запит.
 */
@Configuration
public class RateLimitConfig {

    /**
     * Окремий бакет для картинок. Той самий користувач, але інший ключ — тож
     * галерея з десятками мініатюр не з'їдає ліміт решти API (і навпаки).
     */
    @Bean
    public KeyResolver mediaKeyResolver() {
        KeyResolver base = userKeyResolver();
        return exchange -> base.resolve(exchange).map(key -> "media:" + key);
    }

    /**
     * Прев'ю маршруту для гостя: ключ майже завжди за IP (токена нема),
     * і бакет свій — щоб повільний ліміт планування не з'їдав бакет решти
     * публічних запитів, якими гість гортає каталог.
     */
    @Bean
    public KeyResolver previewKeyResolver() {
        KeyResolver base = userKeyResolver();
        return exchange -> base.resolve(exchange).map(key -> "preview:" + key);
    }

    /**
     * @Primary обовʼязковий: фабрика RequestRateLimiter інжектить KeyResolver ЗА ТИПОМ,
     * а біна тепер два. Без явного «головного» gateway не підніметься взагалі —
     * впаде на NoUniqueBeanDefinitionException ще до першого запиту.
     */
    @Primary
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(p -> "user:" + ((JwtAuthenticationToken) p).getToken().getSubject())
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    var addr = exchange.getRequest().getRemoteAddress();
                    return "ip:" + (addr != null ? addr.getAddress().getHostAddress() : "unknown");
                }));
    }
}
