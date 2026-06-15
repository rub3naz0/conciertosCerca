package com.rubenazo.buscaConciertos.application.ports.out;

import java.util.Optional;

public interface GeocodingPort {
    Optional<Coordinates> geocode(String address, String city, String province);

    record Coordinates(double lat, double lng) {}
}
