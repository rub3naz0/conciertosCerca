package com.rubenazo.buscaConciertos.adapters.out.alcala.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AemEventDto(
    @JsonProperty("event_uid") String eventUid,
    String day,
    String time,
    String price,
    @JsonProperty("price_preorder") String pricePreorder,
    @JsonProperty("ticket_link") String ticketLink,
    String title,
    List<AemBandDto> bands,
    AemVenueDto venues
) {}
