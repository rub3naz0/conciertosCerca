package com.rubenazo.buscaConciertos.adminweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the admin web app — a separate service from the public API, used to
 * manually fill SEVERE data-quality gaps the automated pipeline could not resolve.
 *
 * It owns no domain data: its out-adapters proxy over HTTP to the public API's admin endpoints
 * ({@code AdminCrudProxyAdapter}, {@code ApiFillProxyAdapter}) and read SEVERE issues directly from
 * the shared SQLite file ({@code DataQualitySqliteReadAdapter}).
 */
@SpringBootApplication
public class AdminWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminWebApplication.class, args);
    }
}
