package com.waylo.trip.domain;

public enum PhotoStatus {
    /** Тікет видано, байтів ще немає. */
    UPLOADING,
    /** media-service підтвердив, що файл на місці. */
    READY
}
