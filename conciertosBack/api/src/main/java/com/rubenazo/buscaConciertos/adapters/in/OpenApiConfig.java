package com.rubenazo.buscaConciertos.adapters.in;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("BuscaConciertos API")
                .version("v1")
                .description("API REST para sincronizar datos de conciertos en España. "
                    + "Soporta sincronización incremental via HEAD (check) + GET (download) por recurso."));
    }
}
