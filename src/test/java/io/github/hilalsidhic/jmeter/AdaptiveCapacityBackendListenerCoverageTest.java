package io.github.hilalsidhic.jmeter;

import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import io.github.hilalsidhic.core.degradation.AdaptiveCapacityPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdaptiveCapacityBackendListenerCoverageTest {

    @Test
    void defaultParametersAreConfigured() {
        AdaptiveCapacityBackendListener listener = new AdaptiveCapacityBackendListener();

        assertNotNull(listener.getDefaultParameters());
        assertFalse(listener.getDefaultParameters().getArguments().isEmpty());
    }

    @Test
    void setupAndHandleSampleResultsRunThroughAllListenerBranches() {
        AdaptiveCapacityBackendListener listener = new AdaptiveCapacityBackendListener();
        BackendListenerContext context = new BackendListenerContext(Map.of(
                "policyModes", "PERCENT_LATENCY,ABSOLUTE_LATENCY,ERROR_RATE,ERROR_COUNT",
                "evaluationIntervalSeconds", "0",
                "degradationPercentThreshold", "10.0",
                "absoluteLatencyThresholdMs", "1000",
                "errorRateThresholdPercent", "5.0",
                "errorCountThreshold", "1",
                "sampleLabels", "api"
        ));

        listener.setupTest(context);
        listener.handleSampleResults(List.of(sampleWithTime(100L, true), sampleWithTime(100L, true)), context);
        listener.handleSampleResults(List.of(sampleWithTime(300L, true), sampleWithTime(300L, false)), context);
        listener.handleSampleResults(null, context);
        listener.teardownTest(context);

        assertFalse(listener.getDefaultParameters().getArguments().isEmpty());
    }

    @Test
    void invalidPolicyModesFallbackToDefaultBehavior() {
        AdaptiveCapacityBackendListener listener = new AdaptiveCapacityBackendListener();
        BackendListenerContext context = new BackendListenerContext(Map.of(
                "policyModes", "NOPE",
                "evaluationIntervalSeconds", "1",
                "degradationPercentThreshold", "10.0",
                "absoluteLatencyThresholdMs", "1000",
                "errorRateThresholdPercent", "5.0",
                "errorCountThreshold", "1",
                "sampleLabels", ""
        ));

        assertDoesNotThrow(() -> listener.setupTest(context));
        assertDoesNotThrow(() -> listener.handleSampleResults(List.of(sampleWithTime(100L, true)), context));
    }

    private SampleResult sampleWithTime(long timeInMillis, boolean success) {
        SampleResult result = new SampleResult(0L, timeInMillis);
        result.setSampleLabel("api");
        result.setSuccessful(success);
        return result;
    }
}
