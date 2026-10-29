package br.com.marden.habitattio;

import org.springframework.boot.SpringApplication;

public class TestHabitattioApplication {

	public static void main(String[] args) {
		SpringApplication.from(HabitattioApplication::main).with(TestcontainersConfiguration.class)
			.withAdditionalProfiles("prod")
			.run(args);
	}

}
