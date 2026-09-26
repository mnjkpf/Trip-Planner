package com.travelplanner.trip.web;

import com.travelplanner.trip.dto.WishlistItemRequest;
import com.travelplanner.trip.dto.WishlistItemResponse;
import com.travelplanner.trip.service.WishlistService;
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

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WishlistItemResponse add(@RequestHeader("X-User-Id") UUID userId,
                                    @Valid @RequestBody WishlistItemRequest req) {
        return wishlistService.add(userId, req);
    }

    @GetMapping
    public List<WishlistItemResponse> list(@RequestHeader("X-User-Id") UUID userId) {
        return wishlistService.list(userId);
    }

    @DeleteMapping("/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestHeader("X-User-Id") UUID userId,
                       @PathVariable UUID placeId) {
        wishlistService.remove(userId, placeId);
    }
}
