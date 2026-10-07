package com.waylo.media.event;

import java.util.List;

/** Власник більше не тримає цих файлів — прибрати байти. */
public record MediaDeleteRequestedEvent(List<String> mediaIds, String context) {}
