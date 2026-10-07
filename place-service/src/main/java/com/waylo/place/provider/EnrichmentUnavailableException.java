package com.waylo.place.provider;

/**
 * Збагачувач не зміг отримати відповідь: таймаут, обрив, 429 чи 5xx.
 *
 * Це НЕ те саме, що «статті немає». Різниця принципова: відсутність статті —
 * остаточна відповідь, і її можна запамʼятати назавжди, а недоступність сервісу
 * означає лише «спробуй пізніше». Без цього розрізнення одна невдала секунда
 * залишала б місце без фото довіку.
 */
public class EnrichmentUnavailableException extends RuntimeException {

    public EnrichmentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
