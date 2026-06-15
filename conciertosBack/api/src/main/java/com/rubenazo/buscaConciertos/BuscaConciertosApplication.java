package com.rubenazo.buscaConciertos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

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
