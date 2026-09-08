package com.travelplanner.trip;

import org.springframework.boot.SpringApplication;

public class TestTripServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(TripServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
