package com.travelplanner.context;

import org.springframework.boot.SpringApplication;

public class TestContextServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(ContextServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
