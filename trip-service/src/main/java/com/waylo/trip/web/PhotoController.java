package com.waylo.trip.web;

import com.waylo.trip.dto.PhotoResponse;
import com.waylo.trip.dto.PhotoUploadRequest;
import com.waylo.trip.dto.PhotoUploadResponse;
import com.waylo.trip.service.PhotoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Фото подорожі. Самих байтів тут немає: клієнт отримує тікет і несе файл
 * у media-service, а сюди повертається лише готовий список.
 */
@RestController
@RequestMapping("/api/trips/{id}/photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping
    public List<PhotoResponse> list(@RequestHeader("X-User-Id") UUID userId,
                                    @PathVariable UUID id) {
        return photoService.list(userId, id);
    }

    @PostMapping("/upload-ticket")
    public PhotoUploadResponse uploadTicket(@RequestHeader("X-User-Id") UUID userId,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody PhotoUploadRequest req) {
        return photoService.requestUpload(userId, id, req);
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-User-Id") UUID userId,
                       @PathVariable UUID id,
                       @PathVariable UUID photoId) {
        photoService.delete(userId, id, photoId);
    }
}
