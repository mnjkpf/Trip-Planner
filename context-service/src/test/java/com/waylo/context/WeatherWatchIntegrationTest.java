package com.waylo.context;

import com.waylo.context.watch.WatchedTrip;
import com.waylo.context.watch.WatchedTripRepository;
import com.waylo.context.watch.WeatherWatchService;
import com.waylo.context.weather.DailyWeather;
import com.waylo.context.weather.WeatherClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;

/**
 * Повна петля на боці context-service:
 *   trip.itinerary.snapshot → проєкція → прогноз → порада → trip.weather.alert.
 * Розклад вимкнено (перевіряємо реакцію на подію), кеш прогнозу — теж,
 * щоб тест міг підмінити погоду між перевірками.
 */
@SpringBootTest(properties = {
        "app.weather-watch.enabled=false",
        "app.weather-watch.forecast-cache-minutes=0"
})
@Import(TestcontainersConfiguration.class)
class WeatherWatchIntegrationTest {

    private static final String SNAPSHOTS = "trip.itinerary.snapshot";
    private static final String ALERTS = "trip.weather.alert";

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    ConsumerFactory<String, String> consumerFactory;
    @Autowired
    WatchedTripRepository repository;
    @Autowired
    WeatherWatchService watchService;
    @Autowired
    JsonMapper jsonMapper;

    @MockitoBean
    WeatherClient weatherClient;

    private String snapshot(UUID tripId, LocalDate start, LocalDate end) {
        return """
                {"tripId":"%s","deleted":false,"destinationName":"Rome","lat":41.9,"lon":12.5,
                 "startDate":"%s","endDate":"%s","snapshotAt":"%s",
                 "days":[
                   {"dayIndex":1,"date":"%s","places":[
                     {"name":"Villa Borghese","category":"PARK"},{"name":"Lido","category":"BEACH"}]},
                   {"dayIndex":2,"date":"%s","places":[
                     {"name":"Vatican Museums","category":"MUSEUM"}]}
                 ]}
                """.formatted(tripId, start, end, Instant.now(), start, end);
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshot_rainyOutdoorDay_publishesSwapAdvice_once() throws Exception {
        LocalDate d1 = LocalDate.now().plusDays(2);
        LocalDate d2 = d1.plusDays(1);
        Mockito.when(weatherClient.forecast(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of(
                        new DailyWeather(d1, 12.0, 17.0, 9.5),
                        new DailyWeather(d2, 13.0, 21.0, 0.2)));
        UUID tripId = UUID.randomUUID();

        try (Consumer<String, String> consumer =
                     consumerFactory.createConsumer("weather-test-" + tripId, "weather-test")) {
            consumer.subscribe(List.of(ALERTS));
            kafkaTemplate.send(SNAPSHOTS, tripId.toString(), snapshot(tripId, d1, d2)).get();

            String alert = awaitRecord(consumer, tripId.toString(), Duration.ofSeconds(30));
            assertNotNull(alert, "порада має прийти в trip.weather.alert");

            Map<String, Object> body = jsonMapper.readValue(alert, Map.class);
            List<Map<String, Object>> alerts = (List<Map<String, Object>>) body.get("alerts");
            assertEquals(1, alerts.size());
            assertEquals(d1.toString(), alerts.get(0).get("date"));
            assertEquals("RAIN", alerts.get(0).get("level"));
            assertEquals(d2.toString(), alerts.get(0).get("swapDate"));
        }

        WatchedTrip watched = awaitFingerprint(tripId);
        assertFalse(watched.getLastFingerprint().isEmpty());

        // Та сама погода → та сама порада → подія НЕ повторюється.
        assertFalse(watchService.checkTrip(tripId, LocalDate.now()));

        // Прогноз покращився → нова (порожня) порада знімає попередження.
        Mockito.when(weatherClient.forecast(anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of(
                        new DailyWeather(d1, 12.0, 20.0, 0.0),
                        new DailyWeather(d2, 13.0, 21.0, 0.0)));
        assertTrue(watchService.checkTrip(tripId, LocalDate.now()));
        assertEquals("", repository.findById(tripId).orElseThrow().getLastFingerprint());
    }

    @Test
    void deletedSnapshot_removesTripFromProjection() throws Exception {
        UUID tripId = UUID.randomUUID();
        // Подорож за місяць — поза вікном нагляду, тож погоду навіть не питаємо.
        LocalDate start = LocalDate.now().plusDays(30);
        kafkaTemplate.send(SNAPSHOTS, tripId.toString(), snapshot(tripId, start, start.plusDays(1))).get();
        assertTrue(awaitPresence(tripId, true), "знімок має зʼявитись у проєкції");

        String deleted = """
                {"tripId":"%s","deleted":true,"snapshotAt":"%s"}
                """.formatted(tripId, Instant.now());
        kafkaTemplate.send(SNAPSHOTS, tripId.toString(), deleted).get();
        assertTrue(awaitPresence(tripId, false), "після видалення подорожі нагляд припиняється");
    }

    private String awaitRecord(Consumer<String, String> consumer, String key, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                if (key.equals(r.key())) {
                    return r.value();
                }
            }
        }
        return null;
    }

    private WatchedTrip awaitFingerprint(UUID tripId) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            WatchedTrip w = repository.findById(tripId).orElse(null);
            if (w != null && w.getLastFingerprint() != null) {
                return w;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("відбиток поради так і не збережено");
    }

    private boolean awaitPresence(UUID tripId, boolean present) throws InterruptedException {
        for (int i = 0; i < 80; i++) {
            if (repository.existsById(tripId) == present) {
                return true;
            }
            Thread.sleep(250);
        }
        return false;
    }
}
