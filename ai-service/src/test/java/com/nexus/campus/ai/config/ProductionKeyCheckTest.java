package com.nexus.campus.ai.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionKeyCheckTest {

    @Test
    void productionWithoutAKeyFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> new ProductionKeyCheck("prod", "").afterPropertiesSet());
    }

    @Test
    void productionWithAKeyStarts() {
        assertDoesNotThrow(
                () -> new ProductionKeyCheck("prod", "shared-secret").afterPropertiesSet());
    }

    @Test
    void localWithoutAKeyIsAllowed() {
        assertDoesNotThrow(
                () -> new ProductionKeyCheck("local", "").afterPropertiesSet());
    }

    @Test
    void aProfileListContainingProdCountsAsProduction() {
        assertThrows(IllegalStateException.class,
                () -> new ProductionKeyCheck("metrics, prod", " ").afterPropertiesSet());
    }
}
