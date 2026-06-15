package com.rubenazo.buscaConciertos.adminweb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate(
            @Value("${app.admin.username}") String username,
            @Value("${app.admin.password}") String password) {
        var restTemplate = new RestTemplate();
        // the API protects /api/admin/** with the same shared admin credentials
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(username, password));
        // and rejects mutations that look like CORS simple requests (CsrfHardeningFilter)
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("X-Requested-With", "XMLHttpRequest");
            return execution.execute(request, body);
        });
        return restTemplate;
    }
}
