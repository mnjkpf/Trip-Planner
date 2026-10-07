package com.waylo.place.config;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeometryConfig {

    // Фабрика JTS-точок у SRID 4326. Знадобиться, коли зберігатимемо
    // результати провайдера: point = factory.createPoint(new Coordinate(lon, lat)).
    @Bean
    public GeometryFactory geometryFactory() {
        return new GeometryFactory(new PrecisionModel(), 4326);
    }
}
