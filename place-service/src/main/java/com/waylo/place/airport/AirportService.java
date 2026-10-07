package com.waylo.place.airport;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

/**
 * In-memory пошук і geo-запити по каталогу аеропортів. Для 750 записів лінійне
 * сканування — найшвидший варіант (жодних індексів, жодних структур зі станом).
 */
@Service
public class AirportService {

    private final AirportCatalog catalog;

    public AirportService(AirportCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Пошук за IATA (точне співпадіння, пріоритет), містом або назвою аеропорту.
     * Регістр не враховується; порожній запит повертає порожньо.
     */
    public List<Airport> suggest(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        String q = query.trim().toLowerCase(Locale.ROOT);
        return catalog.all().stream()
            .map(a -> new Scored(a, score(a, q)))
            .filter(s -> s.score > 0)
            .sorted(Comparator.comparingInt((Scored s) -> s.score).reversed())
            .limit(limit)
            .map(s -> s.airport)
            .toList();
    }

    /** Найближчі до координат аеропорти — за прямою (haversine). */
    public List<Airport> nearest(double lat, double lon, int limit) {
        return catalog.all().stream()
            .sorted(Comparator.comparingDouble(a -> haversineKm(lat, lon, a.lat(), a.lon())))
            .limit(limit)
            .toList();
    }

    private static int score(Airport a, String q) {
        String iata = a.iata().toLowerCase(Locale.ROOT);
        String city = a.city().toLowerCase(Locale.ROOT);
        String name = a.name().toLowerCase(Locale.ROOT);
        if (iata.equals(q)) return 1000;                         // точно IATA
        if (iata.startsWith(q)) return 500;                      // префікс IATA
        if (city.equals(q)) return 400;
        if (city.startsWith(q)) return 300;
        if (name.toLowerCase(Locale.ROOT).startsWith(q)) return 200;
        if (city.contains(q)) return 100;
        if (name.contains(q)) return 50;
        return 0;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    private record Scored(Airport airport, int score) {}
}
