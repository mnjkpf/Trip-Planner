package com.waylo.place;

import org.springframework.boot.SpringApplication;

public class TestPlaceServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(PlaceServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
