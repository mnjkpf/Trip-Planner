package com.waylo.media.dto;

import java.util.UUID;

/** Файл прийнято; мініатюра робиться у воркері, тож статус PROCESSING. */
public record UploadResponse(UUID mediaId, String status) {}
