package com.travelplanner.place.service;

import com.travelplanner.place.domain.Place;
import com.travelplanner.place.domain.PlaceCategory;
import com.travelplanner.place.domain.PlaceExternalRef;
import com.travelplanner.place.dto.PlaceResponse;
import com.travelplanner.place.error.ApiExceptions.PlaceNotFoundException;
import com.travelplanner.place.provider.PlaceCandidate;
import com.travelplanner.place.provider.PlacesProvider;
import com.travelplanner.place.repository.PlaceExternalRefRepository;
import com.travelplanner.place.repository.PlaceRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PlaceService {

    private static final Logger log = LoggerFactory.getLogger(PlaceService.class);
    private static final int MAX_RESULTS = 50;

    private final PlaceRepository placeRepository;
    private final PlaceExternalRefRepository externalRefRepository;
    private final PlacesProvider placesProvider;
    private final GeometryFactory geometryFactory;

    public PlaceService(PlaceRepository placeRepository,
                        PlaceExternalRefRepository externalRefRepository,
                        PlacesProvider placesProvider,
                        GeometryFactory geometryFactory) {
        this.placeRepository = placeRepository;
        this.externalRefRepository = externalRefRepository;
        this.placesProvider = placesProvider;
        this.geometryFactory = geometryFactory;
    }

    /**
     * Пошук місць у радіусі. Спершу дивимось у власний каталог (БД). Якщо порожньо —
     * тягнемо кандидатів у провайдера (Geoapify), зберігаємо нові в places (+ звʼязок
     * у place_external_refs, дедуп по provider+external_id) і перечитуємо з БД, щоб
     * віддати вже з внутрішніми id. Наступні пошуки в цій зоні йдуть із каталогу.
     */
    @Transactional
    public List<PlaceResponse> search(double lat, double lon, int radiusMeters, PlaceCategory category) {
        String cat = category == null ? null : category.name();
        List<Place> found = placeRepository.findNearby(lat, lon, radiusMeters, cat, MAX_RESULTS);

        if (found.isEmpty()) {
            int saved = ingestFromProvider(lat, lon, radiusMeters, category);
            if (saved > 0) {
                found = placeRepository.findNearby(lat, lon, radiusMeters, cat, MAX_RESULTS);
            }
        }
        return found.stream().map(this::toResponse).toList();
    }

    /** Зберігає нових кандидатів провайдера в каталог. Повертає кількість збережених. */
    private int ingestFromProvider(double lat, double lon, int radiusMeters, PlaceCategory category) {
        List<PlaceCandidate> candidates = placesProvider.searchNearby(lat, lon, radiusMeters, category);
        int saved = 0;
        for (PlaceCandidate c : candidates) {
            // дедуп: той самий обʼєкт того самого провайдера в каталог удруге не пишемо
            if (externalRefRepository.existsByProviderAndExternalId(c.provider(), c.externalId())) {
                continue;
            }

            Point location = geometryFactory.createPoint(new Coordinate(c.lon(), c.lat())); // X=довгота, Y=широта
            location.setSRID(4326);
            Instant now = Instant.now();

            Place place = Place.builder()
                    .id(UUID.randomUUID())
                    .name(c.name())
                    .category(c.category() == null ? PlaceCategory.OTHER.name() : c.category().name())
                    .location(location)
                    .countryCode(c.countryCode())
                    .city(c.city())
                    .address(c.address())
                    .website(c.website())
                    .imageUrl(c.imageUrl())
                    .sourceProvider(c.provider())
                    .fetchedAt(now)
                    .build();
            placeRepository.save(place);

            PlaceExternalRef ref = PlaceExternalRef.builder()
                    .id(UUID.randomUUID())
                    .placeId(place.getId())
                    .provider(c.provider())
                    .externalId(c.externalId())
                    .rawPayload(c.rawPayload())
                    .build();
            externalRefRepository.save(ref);
            saved++;
        }
        if (saved > 0) {
            log.info("каталог поповнено: {} нових місць від {}", saved, placesProvider.name());
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public PlaceResponse getById(UUID id) {
        return placeRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new PlaceNotFoundException("Місце не знайдено: " + id));
    }

    private PlaceResponse toResponse(Place p) {
        Point loc = p.getLocation();
        double lat = loc != null ? loc.getY() : 0;   // Y = широта
        double lon = loc != null ? loc.getX() : 0;   // X = довгота
        return new PlaceResponse(
                p.getId(), p.getName(), p.getCategory(), p.getDescription(),
                lat, lon, p.getCity(), p.getCountryCode(), p.getAddress(),
                p.getImageUrl(), p.getWebsite());
    }
}
