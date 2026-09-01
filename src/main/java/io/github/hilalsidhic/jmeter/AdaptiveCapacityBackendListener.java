package io.github.hilalsidhic.jmeter;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import io.github.hilalsidhic.core.degradation.AdaptiveCapacityPolicy;
import io.github.hilalsidhic.core.model.StageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AdaptiveCapacityBackendListener extends AbstractBackendListenerClient {
    private static final Logger log = LoggerFactory.getLogger(AdaptiveCapacityBackendListener.class);

    private volatile AdaptiveCapacityState state = new AdaptiveCapacityState();
    private final AtomicBoolean stopIssued = new AtomicBoolean(false);
    private final Object windowLock = new Object();
    private volatile long lastWindowMillis = System.currentTimeMillis();
    private volatile Set<AdaptiveCapacityPolicy> policyModes = EnumSet.of(
            AdaptiveCapacityPolicy.PERCENT_LATENCY,
            AdaptiveCapacityPolicy.ABSOLUTE_LATENCY,
            AdaptiveCapacityPolicy.ERROR_RATE,
            AdaptiveCapacityPolicy.ERROR_COUNT
    );
    private double degradationPercentThreshold = 10.0d;
    private long absoluteLatencyThresholdMs = 2000L;
    private double errorRateThresholdPercent = 5.0d;
    private long errorCountThreshold = 10L;
    private long evaluationIntervalMs = 5_000L;
    private Set<String> targetSamplers = Collections.emptySet();

    @Override
    public Arguments getDefaultParameters() {
        Arguments args = new Arguments();
        args.addArgument("policyModes", "PERCENT_LATENCY,ABSOLUTE_LATENCY,ERROR_RATE,ERROR_COUNT");
        args.addArgument("evaluationIntervalSeconds", "5");
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
        stopIssued.set(false);
        lastWindowMillis = System.currentTimeMillis();
        log.info("AdaptiveCapacity initialized. policies={} interval={}s degradation={}%, absoluteLatency={}ms errorRate={}%, errorCount={} labels={}",
                policyModes,
                evaluationIntervalMs / 1000.0d,
                degradationPercentThreshold,
                absoluteLatencyThresholdMs,
                errorRateThresholdPercent,
                errorCountThreshold,
                targetSamplers.isEmpty() ? "*" : String.join(",", targetSamplers));
    }

    @Override
    public void handleSampleResults(List<SampleResult> sampleResults, BackendListenerContext context) {
        if (sampleResults == null || sampleResults.isEmpty()) {
            return;
        }

        for (SampleResult sampleResult : sampleResults) {
            if (sampleResult == null) {
                continue;
            }
            String samplerName = sampleResult.getSampleLabel() == null ? "unknown" : sampleResult.getSampleLabel();
            if (!targetSamplers.isEmpty() && !targetSamplers.contains(samplerName)) {
                continue;
            }
            state.accept(samplerName, sampleResult.getTime(), sampleResult.isSuccessful());
        }

        long now = System.currentTimeMillis();
        if (now - lastWindowMillis >= evaluationIntervalMs) {
            synchronized (windowLock) {
                if (now - lastWindowMillis < evaluationIntervalMs) {
                    return;
                }

                Map<String, StageResult> report = state.finishAllStages();
                lastWindowMillis = now;
                if (!report.isEmpty()) {
                    StringBuilder summary = new StringBuilder();
                    boolean first = true;
                    for (Map.Entry<String, StageResult> entry : report.entrySet()) {
                        if (!first) {
                            summary.append(", ");
                        }
                        StageResult stage = entry.getValue();
                        summary.append(entry.getKey())
                                .append(":[p95=")
                                .append(stage.currentLatency)
                                .append("ms,qps=")
                                .append(stage.qps)
                                .append(",errRate=")
                                .append(stage.errorRatePercent)
                                .append("%,errCount=")
                                .append(stage.errorCount)
                                .append("]");
                        first = false;
                    }

                    boolean thresholdReached = state.wasThresholdExceeded();
                    if (thresholdReached) {
                        stopTestAndReport(report, summary.toString());
                    } else {
                        log.info("AdaptiveCapacity window summary: {}", summary);
                    }
                }
            }
        }
    }

    @Override
    public void teardownTest(BackendListenerContext context) {
        Map<String, StageResult> finalReport = state.finishAllStages();
        if (!finalReport.isEmpty()) {
            boolean thresholdReached = state.wasThresholdExceeded();
            if (thresholdReached) {
                log.warn("AdaptiveCapacity final report: breach detected: {}", reportToString(finalReport));
                stopTestAndReport(finalReport, reportToString(finalReport));
            } else {
                log.info("AdaptiveCapacity final report: {}", reportToString(finalReport));
            }
        }
        state = new AdaptiveCapacityState();
        stopIssued.set(false);
        lastWindowMillis = System.currentTimeMillis();
    }

    private void readConfiguration(BackendListenerContext context) {
        policyModes = parsePolicyModes(context.getParameter("policyModes", "PERCENT_LATENCY,ABSOLUTE_LATENCY,ERROR_RATE,ERROR_COUNT"));

        String evalIntParam = context.getParameter("evaluationIntervalSeconds", "5");
        long intervalSec = evalIntParam != null && !evalIntParam.trim().isEmpty() ? Long.parseLong(evalIntParam.trim()) : 5L;
        evaluationIntervalMs = Math.max(0L, intervalSec) * 1000L;

        String degPercentParam = context.getParameter("degradationPercentThreshold", "10.0");
        degradationPercentThreshold = degPercentParam != null && !degPercentParam.trim().isEmpty() ? Double.parseDouble(degPercentParam.trim()) : 10.0d;

        String absLatParam = context.getParameter("absoluteLatencyThresholdMs", "2000");
        absoluteLatencyThresholdMs = absLatParam != null && !absLatParam.trim().isEmpty() ? Long.parseLong(absLatParam.trim()) : 2000L;

        String errRateParam = context.getParameter("errorRateThresholdPercent", "5.0");
        errorRateThresholdPercent = errRateParam != null && !errRateParam.trim().isEmpty() ? Double.parseDouble(errRateParam.trim()) : 5.0d;

        String errCountParam = context.getParameter("errorCountThreshold", "10");
        errorCountThreshold = errCountParam != null && !errCountParam.trim().isEmpty() ? Long.parseLong(errCountParam.trim()) : 10L;

        String labels = context.getParameter("sampleLabels", "");
        if (labels == null || labels.trim().isEmpty()) {
            targetSamplers = Collections.emptySet();
        } else {
            Set<String> set = new java.util.HashSet<>();
            for (String label : labels.split(",")) {
                String trimmed = label.trim();
                if (!trimmed.isEmpty()) {
                    set.add(trimmed);
                }
            }
            targetSamplers = set;
        }
    }

    private Set<AdaptiveCapacityPolicy> parsePolicyModes(String parameter) {
        if (parameter == null || parameter.trim().isEmpty()) {
            return EnumSet.of(
                    AdaptiveCapacityPolicy.PERCENT_LATENCY,
                    AdaptiveCapacityPolicy.ABSOLUTE_LATENCY,
                    AdaptiveCapacityPolicy.ERROR_RATE,
                    AdaptiveCapacityPolicy.ERROR_COUNT
            );
        }
        Set<AdaptiveCapacityPolicy> policies = EnumSet.noneOf(AdaptiveCapacityPolicy.class);
        String[] tokens = parameter.split(",");
        for (String value : tokens) {
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
        if (!stopIssued.compareAndSet(false, true)) {
            return;
        }

        SampleResult stopSample = buildStopSample(report, summary);
        if (JMeterContextService.getContext() != null) {
            JMeterContextService.getContext().setPreviousResult(stopSample);
        }

        notifySampleListeners(stopSample);

        // 1. Invoke static StandardJMeterEngine.stopEngine() which works from background threads
        try {
            org.apache.jmeter.engine.StandardJMeterEngine.stopEngine();
        } catch (Throwable e) {
            log.debug("StandardJMeterEngine.stopEngine() exception", e);
        }

        // 2. Invoke context engine if active
        try {
            if (JMeterContextService.getContext() != null && JMeterContextService.getContext().getEngine() != null) {
                JMeterContextService.getContext().getEngine().stopTest();
            }
        } catch (Throwable e) {
            log.debug("Context engine stopTest() exception", e);
        }

        // 3. Invoke engine via reflection if available
        try {
            java.lang.reflect.Field engineField = org.apache.jmeter.engine.StandardJMeterEngine.class.getDeclaredField("engine");
            engineField.setAccessible(true);
            org.apache.jmeter.engine.StandardJMeterEngine activeEngine = (org.apache.jmeter.engine.StandardJMeterEngine) engineField.get(null);
            if (activeEngine != null) {
                activeEngine.stopTest();
            }
        } catch (Throwable e) {
            log.debug("Reflection engine stopTest() exception", e);
        }

        log.warn("AdaptiveCapacity stop trigger. policies={} current window: {} reason={}", policyModes, summary, stopSample.getResponseMessage());
    }

    private SampleResult buildStopSample(Map<String, StageResult> report, String summary) {
        String message = buildStopReason(report, summary);
        long now = System.currentTimeMillis();
        SampleResult stopSample = SampleResult.createTestSample(now);
        stopSample.setSampleLabel("AdaptiveCapacity: Capacity Limit Exceeded");
        stopSample.setSuccessful(false);
        stopSample.setResponseCode("500");
        stopSample.setResponseMessage(message);
        stopSample.setResponseData(message.getBytes(StandardCharsets.UTF_8));
        stopSample.setDataType(SampleResult.TEXT);
        return stopSample;
    }

    private void notifySampleListeners(SampleResult stopSample) {
        try {
            org.apache.jmeter.samplers.SampleEvent event = new org.apache.jmeter.samplers.SampleEvent(stopSample, "AdaptiveCapacity");
            org.apache.jorphan.collections.HashTree tree = null;
            if (JMeterContextService.getContext() != null && JMeterContextService.getContext().getThread() != null) {
                tree = JMeterContextService.getContext().getThread().getTestTree();
            }
            if (tree == null) {
                try {
                    java.lang.reflect.Field engineField = org.apache.jmeter.engine.StandardJMeterEngine.class.getDeclaredField("engine");
                    engineField.setAccessible(true);
                    Object activeEngine = engineField.get(null);
                    if (activeEngine != null) {
                        java.lang.reflect.Field testField = org.apache.jmeter.engine.StandardJMeterEngine.class.getDeclaredField("test");
                        testField.setAccessible(true);
                        tree = (org.apache.jorphan.collections.HashTree) testField.get(activeEngine);
                    }
                } catch (Throwable ignored) {
                }
            }
            if (tree == null && JMeterContextService.getContext() != null && JMeterContextService.getContext().getEngine() != null) {
                try {
                    java.lang.reflect.Field testField = JMeterContextService.getContext().getEngine().getClass().getDeclaredField("test");
                    testField.setAccessible(true);
                    tree = (org.apache.jorphan.collections.HashTree) testField.get(JMeterContextService.getContext().getEngine());
                } catch (Throwable ignored) {
                }
            }
            if (tree != null) {
                org.apache.jorphan.collections.SearchByClass<org.apache.jmeter.samplers.SampleListener> searcher =
                        new org.apache.jorphan.collections.SearchByClass<>(org.apache.jmeter.samplers.SampleListener.class);
                tree.traverse(searcher);
                for (org.apache.jmeter.samplers.SampleListener listener : searcher.getSearchResults()) {
                    try {
                        listener.sampleOccurred(event);
                    } catch (Throwable e) {
                        log.warn("Error notifying listener {}", listener, e);
                    }
                }
            }
        } catch (Throwable e) {
            log.warn("Could not dispatch stop SampleResult to listeners", e);
        }
    }

    private String buildStopReason(Map<String, StageResult> report, String summary) {
        double peakQps = report.values().stream().mapToDouble(result -> result.qps).max().orElse(0.0d);
        long peakLatency = report.values().stream().mapToLong(result -> result.currentLatency).max().orElse(0L);
        return "AdaptiveCapacity stop triggered. threshold breach detected; peakLatency=" + peakLatency +
                "ms; peakQps=" + peakQps + "; stageSummary=" + summary;
    }

    private String reportToString(Map<String, StageResult> report) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, StageResult> entry : report.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            StageResult stage = entry.getValue();
            sb.append(entry.getKey())
                    .append(":[p95=")
                    .append(stage.currentLatency)
                    .append("ms,qps=")
                    .append(stage.qps)
                    .append(",errRate=")
                    .append(stage.errorRatePercent)
                    .append("%,errCount=")
                    .append(stage.errorCount)
                    .append("]");
            first = false;
        }
        return sb.toString();
    }
}
