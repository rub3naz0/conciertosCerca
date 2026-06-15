package com.rubenazo.buscaConciertos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Spring Boot entry point for the public API service (the app the mobile front talks to).
 *
 * Follows hexagonal architecture: {@code application} holds use cases and the ports they depend on,
 * {@code adapters/in} are the REST controllers, {@code adapters/out} the SQLite/Tavily/LLM/geocoding
 * implementations. The {@link java.time.Clock} bean is exposed so use cases inject time instead of
 * calling {@code now()} directly, which keeps them deterministic under test.
 */
@SpringBootApplication
public class BuscaConciertosApplication {

	public static void main(String[] args) {
		SpringApplication.run(BuscaConciertosApplication.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}
}
