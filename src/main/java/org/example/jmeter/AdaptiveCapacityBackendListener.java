package org.example.jmeter;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.example.core.degradation.AdaptiveCapacityPolicy;
import org.example.core.model.StageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AdaptiveCapacityBackendListener extends AbstractBackendListenerClient {
    private static final Logger log = LoggerFactory.getLogger(AdaptiveCapacityBackendListener.class);

    private AdaptiveCapacityState state = new AdaptiveCapacityState();
    private volatile boolean stopIssued = false;
    private volatile long lastWindowMillis = System.currentTimeMillis();
    private volatile Set<AdaptiveCapacityPolicy> policyModes = EnumSet.of(
            AdaptiveCapacityPolicy.PERCENT_LATENCY,
            AdaptiveCapacityPolicy.ABSOLUTE_LATENCY,
            AdaptiveCapacityPolicy.ERROR_RATE,
            AdaptiveCapacityPolicy.ERROR_COUNT
    );
    private volatile double degradationPercentThreshold = 10.0d;
    private volatile long absoluteLatencyThresholdMs = 2000L;
    private volatile double errorRateThresholdPercent = 5.0d;
    private volatile long errorCountThreshold = 10L;
    private volatile long evaluationIntervalMs = 60_000L;
    private volatile Set<String> targetSamplers = Collections.emptySet();

    @Override
    public Arguments getDefaultParameters() {
        Arguments args = new Arguments();
        args.addArgument("policyModes", "PERCENT_LATENCY,ABSOLUTE_LATENCY,ERROR_RATE,ERROR_COUNT");
        args.addArgument("evaluationIntervalSeconds", "60");
        args.addArgument("degradationPercentThreshold", "10.0");
        args.addArgument("absoluteLatencyThresholdMs", "2000");
        args.addArgument("errorRateThresholdPercent", "5.0");
        args.addArgument("errorCountThreshold", "10");
        args.addArgument("sampleLabels", "");
        return args;
    }

    @Override
    public void setupTest(BackendListenerContext context) {
        readConfiguration(context);
        state = new AdaptiveCapacityState(degradationPercentThreshold, policyModes, absoluteLatencyThresholdMs, errorRateThresholdPercent, errorCountThreshold, evaluationIntervalMs / 1000.0d);
        stopIssued = false;
        lastWindowMillis = System.currentTimeMillis();
        log.info("AdaptiveCapacity initialized. policies={} interval={}s degradation={}%, absoluteLatency={}ms errorRate={}%, errorCount={} labels={}",
                policyModes,
                evaluationIntervalMs / 1000,
                degradationPercentThreshold,
                absoluteLatencyThresholdMs,
                errorRateThresholdPercent,
                errorCountThreshold,
                targetSamplers.isEmpty() ? "ALL" : targetSamplers);
    }

    @Override
    public void handleSampleResults(List<SampleResult> sampleResults, BackendListenerContext context) {
        if (sampleResults == null || sampleResults.isEmpty()) {
            return;
        }

        readConfiguration(context);

        for (SampleResult sampleResult : sampleResults) {
            if (sampleResult == null) {
                continue;
            }

            String samplerName = sampleResult.getSampleLabel() == null ? "unknown" : sampleResult.getSampleLabel();
            if (!targetSamplers.isEmpty() && !targetSamplers.contains(samplerName)) {
                continue;
            }
            state.accept(sampleResult);
        }

        long now = System.currentTimeMillis();
        if (now - lastWindowMillis >= evaluationIntervalMs) {
            Map<String, StageResult> report = state.finishAllStages();
            if (report.isEmpty()) {
                lastWindowMillis = now;
                return;
            }

            StringBuilder summary = new StringBuilder();
            for (Map.Entry<String, StageResult> entry : report.entrySet()) {
                StageResult result = entry.getValue();
                summary.append(entry.getKey())
                        .append("=latency:")
                        .append(result.currentLatency)
                        .append("ms qps:")
                        .append(result.qps)
                        .append(" errorRate:")
                        .append(result.errorRatePercent)
                        .append("% errorCount:")
                        .append(result.errorCount)
                        .append(" ");
            }

            boolean thresholdReached = state.wasThresholdExceeded();
            if (thresholdReached) {
                stopTestAndReport(report, summary.toString());
            } else {
                log.info("AdaptiveCapacity window summary: {}", summary);
            }

            lastWindowMillis = now;
        }
    }

    @Override
    public void teardownTest(BackendListenerContext context) {
        readConfiguration(context);
        Map<String, StageResult> finalReport = state.finishAllStages();
        if (!finalReport.isEmpty()) {
            log.info("AdaptiveCapacity final report: {}", reportToString(finalReport));
        }
    }

    private void readConfiguration(BackendListenerContext context) {
        if (context == null) {
            return;
        }

        policyModes = parsePolicies(context.getParameter("policyModes", "PERCENT_LATENCY,ABSOLUTE_LATENCY,ERROR_RATE,ERROR_COUNT"));
        degradationPercentThreshold = Double.parseDouble(context.getParameter("degradationPercentThreshold", "10.0"));
        absoluteLatencyThresholdMs = context.getLongParameter("absoluteLatencyThresholdMs", 2000L);
        errorRateThresholdPercent = Double.parseDouble(context.getParameter("errorRateThresholdPercent", "5.0"));
        errorCountThreshold = context.getLongParameter("errorCountThreshold", 10L);
        evaluationIntervalMs = context.getLongParameter("evaluationIntervalSeconds", 60L) * 1000L;

        String rawLabels = context.getParameter("sampleLabels", "");
        if (rawLabels == null || rawLabels.trim().isEmpty()) {
            targetSamplers = Collections.emptySet();
            return;
        }

        List<String> labels = new ArrayList<>();
        for (String label : rawLabels.split(",")) {
            String trimmed = label.trim();
            if (!trimmed.isEmpty()) {
                labels.add(trimmed);
            }
        }
        targetSamplers = labels.isEmpty() ? Collections.emptySet() : Set.copyOf(labels);
    }

    private Set<AdaptiveCapacityPolicy> parsePolicies(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return EnumSet.of(
                    AdaptiveCapacityPolicy.PERCENT_LATENCY,
                    AdaptiveCapacityPolicy.ABSOLUTE_LATENCY,
                    AdaptiveCapacityPolicy.ERROR_RATE,
                    AdaptiveCapacityPolicy.ERROR_COUNT
            );
        }

        Set<AdaptiveCapacityPolicy> policies = EnumSet.noneOf(AdaptiveCapacityPolicy.class);
        String[] values = rawValue.split(",");
        for (String value : values) {
            String normalized = value.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                policies.add(AdaptiveCapacityPolicy.valueOf(normalized.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // ignore unsupported values while preserving the intended multi-policy model
            }
        }
        return policies.isEmpty() ? EnumSet.of(
                AdaptiveCapacityPolicy.PERCENT_LATENCY,
                AdaptiveCapacityPolicy.ABSOLUTE_LATENCY,
                AdaptiveCapacityPolicy.ERROR_RATE,
                AdaptiveCapacityPolicy.ERROR_COUNT
        ) : policies;
    }

    private void stopTestAndReport(Map<String, StageResult> report, String summary) {
        if (stopIssued) {
            return;
        }

        stopIssued = true;
        SampleResult stopSample = buildStopSample(report, summary);
        if (JMeterContextService.getContext() != null) {
            JMeterContextService.getContext().setPreviousResult(stopSample);
        }

        try {
            if (JMeterContextService.getContext() != null && JMeterContextService.getContext().getEngine() != null) {
                JMeterContextService.getContext().getEngine().stopTest();
            }
        } catch (Exception e) {
            log.error("AdaptiveCapacity plugin could not stop the test cleanly", e);
        }

        log.warn("AdaptiveCapacity stop trigger. policies={} current window: {} reason={}", policyModes, summary, stopSample.getResponseMessage());
    }

    private SampleResult buildStopSample(Map<String, StageResult> report, String summary) {
        String message = buildStopReason(report, summary);
        SampleResult stopSample = new SampleResult(0L, 0L);
        stopSample.setSampleLabel("AdaptiveCapacity stop trigger");
        stopSample.setSuccessful(false);
        stopSample.setResponseCode("ADAPTIVE_CAPACITY_STOP");
        stopSample.setResponseMessage(message);
        stopSample.setResponseData(message.getBytes(StandardCharsets.UTF_8));
        stopSample.setDataType(SampleResult.TEXT);
        return stopSample;
    }

    private String buildStopReason(Map<String, StageResult> report, String summary) {
        double peakQps = report.values().stream().mapToDouble(result -> result.qps).max().orElse(0.0d);
        long peakLatency = report.values().stream().mapToLong(result -> result.currentLatency).max().orElse(0L);
        return "AdaptiveCapacity stop triggered. threshold breach detected; peakLatency=" + peakLatency + "ms; peakQps=" + peakQps + "; activePolicies=" + policyModes + "; details=" + summary;
    }

    private String reportToString(Map<String, StageResult> report) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, StageResult> entry : report.entrySet()) {
            StageResult result = entry.getValue();
            builder.append(entry.getKey())
                    .append("={latency:")
                    .append(result.currentLatency)
                    .append("ms, qps:")
                    .append(result.qps)
                    .append(", errorRate:")
                    .append(result.errorRatePercent)
                    .append("%, errorCount:")
                    .append(result.errorCount)
                    .append(", samples:")
                    .append(result.sampleCount)
                    .append("}; ");
        }
        return builder.toString();
    }
}
