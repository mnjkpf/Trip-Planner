package com.travelplanner.place.web;

import com.travelplanner.place.domain.PlaceCategory;
import com.travelplanner.place.dto.PlaceResponse;
import com.travelplanner.place.service.PlaceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/places")
@Validated
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    /**
     * Пошук місць у радіусі. Приклад:
     *   GET /api/places/search?lat=50.45&lon=30.52&radius=2000&category=MUSEUM
     * category — необовʼязковий; без нього шукає всі категорії.
     */
    @GetMapping("/search")
    public List<PlaceResponse> search(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "2000") @Min(1) @Max(50000) int radius,
            @RequestParam(required = false) PlaceCategory category) {
        return placeService.search(lat, lon, radius, category);
    }

    @GetMapping("/{id}")
    public PlaceResponse getById(@PathVariable UUID id) {
        return placeService.getById(id);
    }
}
