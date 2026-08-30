package org.example.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageResultTest {

    @Test
    void constructorsPopulateAllFields() {
        StageResult simple = new StageResult("api", 100L);
        StageResult medium = new StageResult("api", 150L, 3L, 2.5d);
        StageResult detailed = new StageResult("api", 200L, 7L, 4.5d, 2L, 28.5d);

        assertEquals("api", simple.samplerName);
        assertEquals(100L, simple.currentLatency);
        assertEquals(0L, simple.sampleCount);
        assertEquals(0.0d, simple.qps, 0.01d);

        assertEquals("api", medium.samplerName);
        assertEquals(150L, medium.currentLatency);
        assertEquals(3L, medium.sampleCount);
        assertEquals(2.5d, medium.qps, 0.01d);

        assertEquals("api", detailed.samplerName);
        assertEquals(200L, detailed.currentLatency);
        assertEquals(7L, detailed.sampleCount);
        assertEquals(4.5d, detailed.qps, 0.01d);
        assertEquals(2L, detailed.errorCount);
        assertEquals(28.5d, detailed.errorRatePercent, 0.01d);
    }
}
