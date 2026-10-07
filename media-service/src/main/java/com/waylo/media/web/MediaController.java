package com.waylo.media.web;

import com.waylo.media.dto.UploadResponse;
import com.waylo.media.service.MediaService;
import com.waylo.media.storage.StoredObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Публічна частина медіа.
 *
 * Віддача (GET) свідомо без авторизації: `<img src>` не вміє слати Bearer-токен,
 * тож захистом слугує невгадуваний UUID — та сама логіка, що й у публічних
 * посилань на маршрут. Завантаження (POST) навпаки вимагає і токена (його
 * перевіряє gateway), і тікета від сервісу-власника.
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Duration IMMUTABLE = Duration.ofDays(365);
    /** Фолбек на оригінал, поки воркер не домалював мініатюру, — кешуємо коротко. */
    private static final Duration SHORT = Duration.ofMinutes(1);

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public UploadResponse upload(@RequestParam("ticket") String ticket,
                                 @RequestParam("file") MultipartFile file) throws IOException {
        UUID mediaId = mediaService.accept(ticket, file.getBytes(),
                file.getContentType(), file.getOriginalFilename());
        return new UploadResponse(mediaId, "PROCESSING");
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<InputStreamResource> original(@PathVariable UUID mediaId) {
        return body(mediaService.original(mediaId), IMMUTABLE);
    }

    @GetMapping("/{mediaId}/thumb")
    public ResponseEntity<InputStreamResource> thumb(@PathVariable UUID mediaId) {
        MediaService.Thumb thumb = mediaService.thumb(mediaId);
        return body(thumb.object(), thumb.generated() ? IMMUTABLE : SHORT);
    }

    private ResponseEntity<InputStreamResource> body(StoredObject object, Duration cache) {
        CacheControl cacheControl = IMMUTABLE.equals(cache)
                ? CacheControl.maxAge(cache).cachePublic().immutable()
                : CacheControl.maxAge(cache).cachePublic();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        object.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : object.contentType()))
                .contentLength(object.size())
                .cacheControl(cacheControl)
                // Байти ніколи не виконуються як сторінка: чуже фото не стане XSS.
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(object.stream()));
    }
}
