package com.rubenazo.buscaConciertos.application.ports.out;

import java.util.Optional;

public interface ReverseGeocodingPort {
    Optional<AdminArea> reverse(double lat, double lng);
}
