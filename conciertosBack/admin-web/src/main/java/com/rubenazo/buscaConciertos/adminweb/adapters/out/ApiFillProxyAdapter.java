package com.rubenazo.buscaConciertos.adminweb.adapters.out;

import com.rubenazo.buscaConciertos.adminweb.application.ports.out.QualityFillPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class ApiFillProxyAdapter implements QualityFillPort {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ApiFillProxyAdapter(RestTemplate restTemplate,
                                @Value("${app.api.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public void fill(Long id, String value) {
        String url = baseUrl + "/api/admin/quality/" + id + "/fill";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("value", value), headers);

        try {
            restTemplate.postForEntity(url, request, Void.class);
        } catch (HttpClientErrorException ex) {
            HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
            throw new ResponseStatusException(status, ex.getResponseBodyAsString());
        }
    }
}
