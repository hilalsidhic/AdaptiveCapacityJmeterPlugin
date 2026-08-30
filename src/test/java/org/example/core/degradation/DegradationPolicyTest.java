package org.example.core.degradation;

import org.example.core.degradation.impl.AbsoluteLatencyPolicy;
import org.example.core.degradation.impl.PercentLatencyPolicy;
import org.example.core.model.StageResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DegradationPolicyTest {

    @Test
    void percentLatencyPolicyFlagsLargePercentageGrowth() {
        StageResult previous = new StageResult("api", 100L);
        StageResult current = new StageResult("api", 220L);

        assertFalse(new PercentLatencyPolicy(10).evaluate(previous, current).decision);
        assertFalse(new PercentLatencyPolicy(10).evaluate(previous, new StageResult("api", 120L)).decision);
        assertTrue(new PercentLatencyPolicy(20).evaluate(previous, new StageResult("api", 120L)).decision);
        assertTrue(new PercentLatencyPolicy(10).evaluate(null, new StageResult("api", 120L)).decision);
        assertTrue(new PercentLatencyPolicy(10).evaluate(new StageResult("api", 0L), new StageResult("api", 120L)).decision);
        assertEquals(10.0d, new PercentLatencyPolicy(10).getThreshold(), 0.01d);
    }

    @Test
    void absoluteLatencyPolicyFlagsLargeAbsoluteGrowth() {
        StageResult previous = new StageResult("api", 50L);
        StageResult current = new StageResult("api", 130L);

        assertFalse(new AbsoluteLatencyPolicy(50L).evaluate(previous, current).decision);
        assertTrue(new AbsoluteLatencyPolicy(50L).evaluate(previous, new StageResult("api", 80L)).decision);
        assertTrue(new AbsoluteLatencyPolicy(50L).evaluate(null, current).decision);
    }

    @Test
    void errorRatePolicyFlagsFailedRequests() {
        StageResult current = new StageResult("api", 100L, 10L, 10.0d, 2L, 20.0d);

        assertFalse(new org.example.core.degradation.impl.ErrorRatePolicy(10.0d).evaluate(new StageResult("api", 100L), current).decision);
        assertTrue(new org.example.core.degradation.impl.ErrorRatePolicy(30.0d).evaluate(new StageResult("api", 100L), current).decision);
        assertTrue(new org.example.core.degradation.impl.ErrorRatePolicy(10.0d).evaluate(new StageResult("api", 100L), null).decision);
    }

    @Test
    void errorCountPolicyFlagsTooManyErrors() {
        StageResult current = new StageResult("api", 100L, 10L, 10.0d, 3L, 30.0d);

        assertFalse(new org.example.core.degradation.impl.ErrorCountPolicy(2L).evaluate(new StageResult("api", 100L), current).decision);
        assertTrue(new org.example.core.degradation.impl.ErrorCountPolicy(10L).evaluate(new StageResult("api", 100L), current).decision);
        assertTrue(new org.example.core.degradation.impl.ErrorCountPolicy(10L).evaluate(new StageResult("api", 100L), null).decision);
    }
}
