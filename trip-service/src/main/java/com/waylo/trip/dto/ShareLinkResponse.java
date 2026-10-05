package com.waylo.trip.dto;

import java.time.Instant;

/**
 * Активне публічне посилання. path — відносний шлях, який фронт доклеює до
 * свого origin: бекенд не знає, під яким доменом крутиться UI, тож повний URL
 * збирати не його справа.
 */
public record ShareLinkResponse(
        String token,
        String path,
        Instant createdAt
) {
    public static ShareLinkResponse of(String token, Instant createdAt) {
        return new ShareLinkResponse(token, "/s/" + token, createdAt);
    }
}
