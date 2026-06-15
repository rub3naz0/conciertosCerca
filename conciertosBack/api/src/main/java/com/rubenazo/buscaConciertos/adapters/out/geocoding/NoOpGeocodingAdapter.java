package com.rubenazo.buscaConciertos.adapters.out.geocoding;

import com.rubenazo.buscaConciertos.application.ports.out.GeocodingPort;

import java.util.Optional;

class NoOpGeocodingAdapter implements GeocodingPort {
    @Override
    public Optional<Coordinates> geocode(String address, String city, String province) {
        return Optional.empty();
    }
}
