package org.example.jmeter;

import org.apache.jmeter.samplers.SampleResult;
import org.example.core.model.StageResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveCapacityStateTest {

    @Test
    void finishStageCalculatesTheWindowedPercentileAndResets() {
        AdaptiveCapacityState state = new AdaptiveCapacityState();

        state.accept(sampleWithTime(100L));
        state.accept(sampleWithTime(200L));
        StageResult stageOne = state.finishStage();

        assertEquals(200L, stageOne.currentLatency);

        state.accept(sampleWithTime(50L));
        state.accept(sampleWithTime(60L));
        StageResult stageTwo = state.finishStage();

        assertEquals(60L, stageTwo.currentLatency);
    }

    @Test
    void backendListenerAcceptsSampleResultsWithoutThrowing() {
        AdaptiveCapacityBackendListener listener = new AdaptiveCapacityBackendListener();

        assertDoesNotThrow(() -> listener.handleSampleResults(List.of(sampleWithTime(110L), sampleWithTime(120L)), null));
    }

    @Test
    void stateTracksErrorRateAlongsideLatency() {
        AdaptiveCapacityState state = new AdaptiveCapacityState();
        state.accept(failingSampleWithTime(100L));
        state.accept(sampleWithTime(90L));

        StageResult stage = state.finishStage("api");

        assertEquals(50.0d, stage.errorRatePercent, 0.01d);
    }

    @Test
    void stateSupportsEmptyPolicySelectionAndReset() {
        AdaptiveCapacityState state = new AdaptiveCapacityState(10.0d, java.util.Collections.emptySet(), 100L, 5.0d, 1L);
        state.accept("api", 25L, true);
        state.reset();

        assertEquals(0L, state.finishStage("api").sampleCount);
    }

    @Test
    void multipleSelectedPoliciesAreEvaluatedTogether() {
        AdaptiveCapacityState state = new AdaptiveCapacityState(
                10.0d,
                java.util.Set.of(
                        org.example.core.degradation.AdaptiveCapacityPolicy.PERCENT_LATENCY,
                        org.example.core.degradation.AdaptiveCapacityPolicy.ABSOLUTE_LATENCY,
                        org.example.core.degradation.AdaptiveCapacityPolicy.ERROR_RATE,
                        org.example.core.degradation.AdaptiveCapacityPolicy.ERROR_COUNT
                ),
                100L,
                5.0d,
                1L
        );

        state.accept("api", 100L, true);
        state.accept("api", 100L, true);
        state.finishAllStages();

        state.accept("api", 300L, true);
        state.accept("api", 300L, true);
        state.accept("api", 300L, false);
        state.accept("api", 300L, false);

        state.finishAllStages();

        assertEquals(true, state.wasThresholdExceeded());
    }

    private SampleResult sampleWithTime(long timeInMillis) {
        SampleResult result = new SampleResult(0L, timeInMillis);
        result.setSampleLabel("api");
        result.setSuccessful(true);
        return result;
    }

    private SampleResult failingSampleWithTime(long timeInMillis) {
        SampleResult result = sampleWithTime(timeInMillis);
        result.setSuccessful(false);
        return result;
    }
}
