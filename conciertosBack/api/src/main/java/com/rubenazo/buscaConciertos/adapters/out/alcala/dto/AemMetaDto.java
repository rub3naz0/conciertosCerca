package com.rubenazo.buscaConciertos.adapters.out.alcala.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AemMetaDto(String next) {}
