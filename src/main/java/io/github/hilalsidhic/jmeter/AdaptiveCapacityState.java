package io.github.hilalsidhic.jmeter;

import org.apache.jmeter.samplers.SampleResult;
import io.github.hilalsidhic.core.accumulator.ResultAccumulator;
import io.github.hilalsidhic.core.degradation.AdaptiveCapacityPolicy;
import io.github.hilalsidhic.core.degradation.DegradationDecision;
import io.github.hilalsidhic.core.degradation.DegradationPolicy;
import io.github.hilalsidhic.core.degradation.impl.AbsoluteLatencyPolicy;
import io.github.hilalsidhic.core.degradation.impl.ErrorCountPolicy;
import io.github.hilalsidhic.core.degradation.impl.ErrorRatePolicy;
import io.github.hilalsidhic.core.degradation.impl.PercentLatencyPolicy;
import io.github.hilalsidhic.core.model.StageResult;
import io.github.hilalsidhic.core.percentile.PercentileCalculator;
import io.github.hilalsidhic.core.percentile.impl.P95Calculator;
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
    private final double stageWindowSeconds;
    private boolean lastThresholdExceeded = false;

    public AdaptiveCapacityState() {
        this(10.0d, Set.of(AdaptiveCapacityPolicy.PERCENT_LATENCY, AdaptiveCapacityPolicy.ABSOLUTE_LATENCY, AdaptiveCapacityPolicy.ERROR_RATE), 2000L, 5.0d, 10L, 60.0d);
    }

    public AdaptiveCapacityState(double thresholdPercent) {
        this(thresholdPercent, Set.of(AdaptiveCapacityPolicy.PERCENT_LATENCY, AdaptiveCapacityPolicy.ABSOLUTE_LATENCY, AdaptiveCapacityPolicy.ERROR_RATE), 2000L, 5.0d, 10L, 60.0d);
    }

    public AdaptiveCapacityState(double thresholdPercent, Set<AdaptiveCapacityPolicy> activePolicies, long absoluteLatencyThresholdMs, double errorRateThresholdPercent, long errorCountThreshold) {
        this(thresholdPercent, activePolicies, absoluteLatencyThresholdMs, errorRateThresholdPercent, errorCountThreshold, 60.0d);
    }

    public AdaptiveCapacityState(double thresholdPercent, Set<AdaptiveCapacityPolicy> activePolicies, long absoluteLatencyThresholdMs, double errorRateThresholdPercent, long errorCountThreshold, double stageWindowSeconds) {
        this.percentileCalculator = new P95Calculator();
        this.errorRateThresholdPercent = errorRateThresholdPercent;
        this.errorCountThreshold = errorCountThreshold;
        this.stageWindowSeconds = stageWindowSeconds > 0 ? stageWindowSeconds : 1.0d;
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
        if (latencyMs < 0L) {
            return;
        }
        ResultAccumulator accumulator = resultAccumulators.computeIfAbsent(samplerName, ignored -> new ResultAccumulator());
        accumulator.recordLatency(latencyMs, success);
    }

    public synchronized Map<String, StageResult> finishAllStages() {
        Map<String, StageResult> previousSnapshot = new HashMap<>(previousResults);
        Map<String, StageResult> currentResults = new HashMap<>();
        lastThresholdExceeded = false;

        for (Map.Entry<String, ResultAccumulator> entry : new HashMap<>(resultAccumulators).entrySet()) {
            String samplerName = entry.getKey();
            StageResult currentResult = entry.getValue().calculateAndReset(this.percentileCalculator, this.stageWindowSeconds, samplerName);

            if (currentResult.sampleCount == 0L) {
                continue;
            }

            StageResult previous = previousSnapshot.get(samplerName);
            if (!activePolicies.isEmpty()) {
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

    public synchronized Set<AdaptiveCapacityPolicy> getActivePolicies() {
        return Collections.unmodifiableSet(activePolicies.keySet());
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

        StageResult currentResult = accumulator.calculateAndReset(this.percentileCalculator, this.stageWindowSeconds, samplerName);
        if (currentResult.sampleCount == 0L) {
            return currentResult;
        }
        previousResults.put(samplerName, currentResult);
        return currentResult;
    }

    public synchronized void reset() {
        resultAccumulators.clear();
        previousResults.clear();
        lastThresholdExceeded = false;
    }
}