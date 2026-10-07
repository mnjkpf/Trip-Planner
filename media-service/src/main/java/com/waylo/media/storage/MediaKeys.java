package com.waylo.media.storage;

import java.util.UUID;

/**
 * Розкладка ключів у бакеті. Оригінал і похідні лежать поруч під одним
 * mediaId — тож видалення фото це просто видалення двох ключів.
 */
public final class MediaKeys {

    private MediaKeys() {
    }

    public static String original(UUID mediaId) {
        return "media/" + mediaId + "/original";
    }

    public static String thumb(UUID mediaId) {
        return "media/" + mediaId + "/thumb";
    }
}
