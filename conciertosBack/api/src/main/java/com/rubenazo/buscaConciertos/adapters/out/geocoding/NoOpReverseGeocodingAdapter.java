package com.rubenazo.buscaConciertos.adapters.out.geocoding;

import com.rubenazo.buscaConciertos.application.ports.out.AdminArea;
import com.rubenazo.buscaConciertos.application.ports.out.ReverseGeocodingPort;

import java.util.Optional;

class NoOpReverseGeocodingAdapter implements ReverseGeocodingPort {

    @Override
    public Optional<AdminArea> reverse(double lat, double lng) {
        return Optional.empty();
    }
}
