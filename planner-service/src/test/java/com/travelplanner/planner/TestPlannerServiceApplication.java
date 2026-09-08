package com.travelplanner.planner;

import org.springframework.boot.SpringApplication;

public class TestPlannerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(PlannerServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
