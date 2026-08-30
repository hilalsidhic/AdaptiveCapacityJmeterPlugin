package org.example.core.accumulator;

import org.example.core.model.StageResult;
import org.example.core.percentile.impl.P95Calculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultAccumulatorTest {

    @Test
    void emptyAccumulatorReturnsZeroResult() {
        ResultAccumulator accumulator = new ResultAccumulator();

        StageResult stage = accumulator.calculateAndReset(new P95Calculator());

        assertEquals(0L, stage.currentLatency);
        assertEquals(0L, stage.sampleCount);
    }

    @Test
    void accumulatorTracksSamplesAndErrors() {
        ResultAccumulator accumulator = new ResultAccumulator();
        accumulator.recordLatency(50L);
        accumulator.recordLatency(200L, false);

        StageResult stage = accumulator.calculateAndReset(new P95Calculator());

        assertEquals(200L, stage.currentLatency);
        assertEquals(2L, stage.sampleCount);
        assertEquals(1L, stage.errorCount);
        assertEquals(50.0d, stage.errorRatePercent, 0.01d);
    }

    @Test
    void resetClearsTrackedState() {
        ResultAccumulator accumulator = new ResultAccumulator();
        accumulator.recordLatency(10L, false);
        accumulator.reset();

        StageResult stage = accumulator.calculateAndReset(new P95Calculator());

        assertEquals(0L, stage.sampleCount);
    }

    @Test
    void qpsUsesTheConfiguredWindowSeconds() {
        ResultAccumulator accumulator = new ResultAccumulator();
        accumulator.recordLatency(50L);
        accumulator.recordLatency(60L);

        StageResult stage = accumulator.calculateAndReset(new P95Calculator(), 2.0d);

        assertEquals(1.0d, stage.qps, 0.001d);
    }
}
