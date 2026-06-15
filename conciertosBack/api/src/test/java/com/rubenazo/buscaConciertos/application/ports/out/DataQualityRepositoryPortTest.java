package com.rubenazo.buscaConciertos.application.ports.out;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compile-time contract test: verifies DataQualityRepositoryPort has
 * the new findEntityIdsBySourceAndField method.
 */
class DataQualityRepositoryPortTest {

    @Test
    void dataQualityRepositoryPort_hasFindEntityIdsBySourceAndField() throws NoSuchMethodException {
        Method method = DataQualityRepositoryPort.class.getMethod(
            "findEntityIdsBySourceAndField", String.class, String.class, List.class);
        assertThat(method).isNotNull();
        assertThat(method.getReturnType()).isEqualTo(List.class);
    }
}
