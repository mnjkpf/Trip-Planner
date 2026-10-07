package com.waylo.media.storage;

import java.util.Optional;

/**
 * Абстракція над обʼєктним сховищем. Існує рівно для того, щоб домену не
 * треба було знати про MinIO (і щоб у тестах можна було підмінити пам'яттю).
 */
public interface ObjectStore {

    void put(String key, byte[] bytes, String contentType);

    /** Потік обʼєкта; Optional.empty(), якщо ключа немає. */
    Optional<StoredObject> get(String key);

    byte[] readAll(String key);

    void delete(String key);
}
