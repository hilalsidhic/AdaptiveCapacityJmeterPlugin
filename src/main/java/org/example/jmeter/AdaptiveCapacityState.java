package org.example.jmeter;

import org.apache.jmeter.samplers.SampleResult;
import org.example.core.accumulator.ResultAccumulator;
import org.example.core.degradation.AdaptiveCapacityPolicy;
import org.example.core.degradation.DegradationDecision;
import org.example.core.degradation.DegradationPolicy;
import org.example.core.degradation.impl.AbsoluteLatencyPolicy;
import org.example.core.degradation.impl.ErrorCountPolicy;
import org.example.core.degradation.impl.ErrorRatePolicy;
import org.example.core.degradation.impl.PercentLatencyPolicy;
import org.example.core.model.StageResult;
import org.example.core.percentile.PercentileCalculator;
import org.example.core.percentile.impl.P95Calculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AdaptiveCapacityState {
    private static final Logger log = LoggerFactory.getLogger(AdaptiveCapacityState.class);
    private final Map<String, ResultAccumulator> resultAccumulators = new ConcurrentHashMap<>();
    private final Map<String, StageResult> previousResults = new ConcurrentHashMap<>();
    private final PercentileCalculator percentileCalculator;
    private final Map<AdaptiveCapacityPolicy, DegradationPolicy> activePolicies;
    private final double errorRateThresholdPercent;
    private final long errorCountThreshold;
    private boolean lastThresholdExceeded = false;

    public AdaptiveCapacityState() {
        this(10.0d, Set.of(AdaptiveCapacityPolicy.PERCENT_LATENCY, AdaptiveCapacityPolicy.ABSOLUTE_LATENCY, AdaptiveCapacityPolicy.ERROR_RATE), 2000L, 5.0d, 10L);
    }

    public AdaptiveCapacityState(double thresholdPercent) {
        this(thresholdPercent, Set.of(AdaptiveCapacityPolicy.PERCENT_LATENCY, AdaptiveCapacityPolicy.ABSOLUTE_LATENCY, AdaptiveCapacityPolicy.ERROR_RATE), 2000L, 5.0d, 10L);
    }

    public AdaptiveCapacityState(double thresholdPercent, Set<AdaptiveCapacityPolicy> activePolicies, long absoluteLatencyThresholdMs, double errorRateThresholdPercent, long errorCountThreshold) {
        this.percentileCalculator = new P95Calculator();
        this.errorRateThresholdPercent = errorRateThresholdPercent;
        this.errorCountThreshold = errorCountThreshold;
        this.activePolicies = buildPolicyMap(thresholdPercent, activePolicies, absoluteLatencyThresholdMs, errorRateThresholdPercent, errorCountThreshold);
    }

    private Map<AdaptiveCapacityPolicy, DegradationPolicy> buildPolicyMap(double thresholdPercent, Set<AdaptiveCapacityPolicy> activePolicies, long absoluteLatencyThresholdMs, double errorRateThresholdPercent, long errorCountThreshold) {
        if (activePolicies == null || activePolicies.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<AdaptiveCapacityPolicy, DegradationPolicy> policies = new HashMap<>();
        if (activePolicies.contains(AdaptiveCapacityPolicy.PERCENT_LATENCY)) {
            policies.put(AdaptiveCapacityPolicy.PERCENT_LATENCY, new PercentLatencyPolicy(thresholdPercent));
        }
        if (activePolicies.contains(AdaptiveCapacityPolicy.ABSOLUTE_LATENCY)) {
            policies.put(AdaptiveCapacityPolicy.ABSOLUTE_LATENCY, new AbsoluteLatencyPolicy(absoluteLatencyThresholdMs));
        }
        if (activePolicies.contains(AdaptiveCapacityPolicy.ERROR_RATE)) {
            policies.put(AdaptiveCapacityPolicy.ERROR_RATE, new ErrorRatePolicy(errorRateThresholdPercent));
        }
        if (activePolicies.contains(AdaptiveCapacityPolicy.ERROR_COUNT)) {
            policies.put(AdaptiveCapacityPolicy.ERROR_COUNT, new ErrorCountPolicy(errorCountThreshold));
        }
        return policies;
    }

    public void accept(SampleResult sampleResult) {
        String samplerName = sampleResult == null || sampleResult.getSampleLabel() == null
                ? "unknown"
                : sampleResult.getSampleLabel();
        accept(samplerName, sampleResult == null ? 0L : sampleResult.getTime(), sampleResult == null || sampleResult.isSuccessful());
    }

    public void accept(String samplerName, long latencyMs) {
        accept(samplerName, latencyMs, true);
    }

    public void accept(String samplerName, long latencyMs, boolean success) {
        ResultAccumulator accumulator = resultAccumulators.computeIfAbsent(samplerName, ignored -> new ResultAccumulator());
        accumulator.recordLatency(latencyMs, success);
    }

    public synchronized Map<String, StageResult> finishAllStages() {
        Map<String, StageResult> previousSnapshot = new HashMap<>(previousResults);
        Map<String, StageResult> currentResults = new HashMap<>();
        lastThresholdExceeded = false;

        for (Map.Entry<String, ResultAccumulator> entry : new HashMap<>(resultAccumulators).entrySet()) {
            String samplerName = entry.getKey();
            StageResult currentResult = entry.getValue().calculateAndReset(this.percentileCalculator);
            currentResult.samplerName = samplerName;

            StageResult previous = previousSnapshot.get(samplerName);
            if (!activePolicies.isEmpty() && previous != null) {
                for (Map.Entry<AdaptiveCapacityPolicy, DegradationPolicy> policyEntry : activePolicies.entrySet()) {
                    DegradationDecision decision = policyEntry.getValue().evaluate(previous, currentResult);
                    if (!decision.decision) {
                        lastThresholdExceeded = true;
                    }
                }
            }

            previousResults.put(samplerName, currentResult);
            currentResults.put(samplerName, currentResult);
            log.info("Stage {} latency={} ms qps={} errorRate={} % activePolicies={}", samplerName, currentResult.currentLatency, currentResult.qps, currentResult.errorRatePercent, activePolicies.keySet());
        }

        return currentResults;
    }

    public synchronized boolean wasThresholdExceeded() {
        return lastThresholdExceeded;
    }

    public synchronized StageResult finishStage() {
        return finishStage("unknown");
    }

    public synchronized StageResult finishStage(String samplerName) {
        ResultAccumulator accumulator = resultAccumulators.get(samplerName);
        if (accumulator == null) {
            if (resultAccumulators.isEmpty()) {
                return new StageResult(samplerName, 0L, 0L, 0.0d, 0L, 0.0d);
            }
            String fallback = resultAccumulators.keySet().iterator().next();
            accumulator = resultAccumulators.get(fallback);
            samplerName = fallback;
        }

        StageResult currentResult = accumulator.calculateAndReset(this.percentileCalculator);
        currentResult.samplerName = samplerName;
        previousResults.put(samplerName, currentResult);
        return currentResult;
    }

    public synchronized void reset() {
        resultAccumulators.clear();
        previousResults.clear();
        lastThresholdExceeded = false;
    }
}