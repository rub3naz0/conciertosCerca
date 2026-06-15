package com.rubenazo.buscaConciertos.adapters.out.alcala.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AemPageDto(AemMetaDto meta, @JsonAlias("objects") List<AemEventDto> events) {}
