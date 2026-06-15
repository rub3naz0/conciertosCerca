package com.rubenazo.buscaConciertos.application;

import java.time.Instant;
import java.util.List;

public record SyncResponse<T>(Instant timestamp, List<T> data) {}
