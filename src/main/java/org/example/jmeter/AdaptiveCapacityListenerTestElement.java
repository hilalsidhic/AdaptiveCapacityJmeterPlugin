package org.example.jmeter;

import org.apache.jmeter.reporters.ResultCollector;

public class AdaptiveCapacityListenerTestElement extends ResultCollector {
    public static final String POLICY_MODES = "adaptive.capacity.policyModes";
    public static final String EVALUATION_INTERVAL_SECONDS = "adaptive.capacity.evaluationIntervalSeconds";
    public static final String DEGRADATION_PERCENT_THRESHOLD = "adaptive.capacity.degradationPercentThreshold";
    public static final String ABSOLUTE_LATENCY_THRESHOLD_MS = "adaptive.capacity.absoluteLatencyThresholdMs";
    public static final String ERROR_RATE_THRESHOLD_PERCENT = "adaptive.capacity.errorRateThresholdPercent";
    public static final String ERROR_COUNT_THRESHOLD = "adaptive.capacity.errorCountThreshold";

    public String getPolicyModes() {
        return getPropertyAsString(POLICY_MODES, "PERCENT_LATENCY,ABSOLUTE_LATENCY,ERROR_RATE,ERROR_COUNT");
    }

    public void setPolicyModes(String value) {
        setProperty(POLICY_MODES, value);
    }

    public long getEvaluationIntervalSeconds() {
        return getPropertyAsLong(EVALUATION_INTERVAL_SECONDS, 60L);
    }

    public void setEvaluationIntervalSeconds(long value) {
        setProperty(EVALUATION_INTERVAL_SECONDS, value);
    }

    public double getDegradationPercentThreshold() {
        return Double.parseDouble(getPropertyAsString(DEGRADATION_PERCENT_THRESHOLD, "10.0"));
    }

    public void setDegradationPercentThreshold(double value) {
        setProperty(DEGRADATION_PERCENT_THRESHOLD, Double.toString(value));
    }

    public long getAbsoluteLatencyThresholdMs() {
        return getPropertyAsLong(ABSOLUTE_LATENCY_THRESHOLD_MS, 2000L);
    }

    public void setAbsoluteLatencyThresholdMs(long value) {
        setProperty(ABSOLUTE_LATENCY_THRESHOLD_MS, value);
    }

    public double getErrorRateThresholdPercent() {
        return Double.parseDouble(getPropertyAsString(ERROR_RATE_THRESHOLD_PERCENT, "5.0"));
    }

    public void setErrorRateThresholdPercent(double value) {
        setProperty(ERROR_RATE_THRESHOLD_PERCENT, Double.toString(value));
    }

    public long getErrorCountThreshold() {
        return getPropertyAsLong(ERROR_COUNT_THRESHOLD, 10L);
    }

    public void setErrorCountThreshold(long value) {
        setProperty(ERROR_COUNT_THRESHOLD, value);
    }
}
