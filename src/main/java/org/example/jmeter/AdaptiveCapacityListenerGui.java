package org.example.jmeter;

import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.visualizers.gui.AbstractVisualizer;
import org.example.core.degradation.AdaptiveCapacityPolicy;
import org.example.core.model.StageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class AdaptiveCapacityListenerGui extends AbstractVisualizer {
    private static final Logger log = LoggerFactory.getLogger(AdaptiveCapacityListenerGui.class);

    private final JTextField policyModesField = new JTextField("PERCENT_LATENCY,ABSOLUTE_LATENCY,ERROR_RATE,ERROR_COUNT");
    private final JTextField evaluationIntervalSecondsField = new JTextField("60");
    private final JTextField degradationPercentThresholdField = new JTextField("10.0");
    private final JTextField absoluteLatencyThresholdField = new JTextField("2000");
    private final JTextField errorRateThresholdField = new JTextField("5.0");
    private final JTextField errorCountThresholdField = new JTextField("10");

    private AdaptiveCapacityState state;
    private volatile long lastWindowMillis = System.currentTimeMillis();
    private volatile boolean stopIssued = false;
    private volatile boolean initialized = false;

    public AdaptiveCapacityListenerGui() {
        super();
        state = buildStateFromCurrentValues();
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Adaptive Capacity Listener"));

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        addField(panel, c, 0, "Policy modes", policyModesField);
        addField(panel, c, 1, "Evaluation interval sec", evaluationIntervalSecondsField);
        addField(panel, c, 2, "Degradation % threshold", degradationPercentThresholdField);
        addField(panel, c, 3, "Absolute latency ms", absoluteLatencyThresholdField);
        addField(panel, c, 4, "Error rate %", errorRateThresholdField);
        addField(panel, c, 5, "Error count", errorCountThresholdField);

        add(panel, BorderLayout.CENTER);
    }

    @Override
    public String getStaticLabel() {
        return "Adaptive Capacity Listener";
    }

    @Override
    public String getLabelResource() {
        return "adaptiveCapacityListener";
    }

    @Override
    public TestElement createTestElement() {
        AdaptiveCapacityListenerTestElement element = new AdaptiveCapacityListenerTestElement();
        configureTestElement(element);
        modifyTestElement(element);
        return element;
    }

    @Override
    public void modifyTestElement(TestElement testElement) {
        if (!(testElement instanceof AdaptiveCapacityListenerTestElement)) {
            return;
        }
        AdaptiveCapacityListenerTestElement element = (AdaptiveCapacityListenerTestElement) testElement;
        element.setName(getStaticLabel());
        element.setPolicyModes(policyModesField.getText());
        element.setEvaluationIntervalSeconds(Long.parseLong(evaluationIntervalSecondsField.getText().trim()));
        element.setDegradationPercentThreshold(Double.parseDouble(degradationPercentThresholdField.getText().trim()));
        element.setAbsoluteLatencyThresholdMs(Long.parseLong(absoluteLatencyThresholdField.getText().trim()));
        element.setErrorRateThresholdPercent(Double.parseDouble(errorRateThresholdField.getText().trim()));
        element.setErrorCountThreshold(Long.parseLong(errorCountThresholdField.getText().trim()));
        state = buildStateFromCurrentValues();
        initialized = true;
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        if (!(element instanceof AdaptiveCapacityListenerTestElement)) {
            return;
        }
        AdaptiveCapacityListenerTestElement listener = (AdaptiveCapacityListenerTestElement) element;
        policyModesField.setText(listener.getPolicyModes());
        evaluationIntervalSecondsField.setText(Long.toString(listener.getEvaluationIntervalSeconds()));
        degradationPercentThresholdField.setText(Double.toString(listener.getDegradationPercentThreshold()));
        absoluteLatencyThresholdField.setText(Long.toString(listener.getAbsoluteLatencyThresholdMs()));
        errorRateThresholdField.setText(Double.toString(listener.getErrorRateThresholdPercent()));
        errorCountThresholdField.setText(Long.toString(listener.getErrorCountThreshold()));
        state = buildStateFromCurrentValues();
        initialized = true;
    }

    @Override
    public void clearGui() {
        super.clearGui();
        policyModesField.setText("PERCENT_LATENCY,ABSOLUTE_LATENCY,ERROR_RATE,ERROR_COUNT");
        evaluationIntervalSecondsField.setText("60");
        degradationPercentThresholdField.setText("10.0");
        absoluteLatencyThresholdField.setText("2000");
        errorRateThresholdField.setText("5.0");
        errorCountThresholdField.setText("10");
        state = buildStateFromCurrentValues();
    }

    @Override
    public void add(SampleResult sampleResult) {
        if (sampleResult == null) {
            return;
        }

        String samplerName = sampleResult.getSampleLabel() == null ? "unknown" : sampleResult.getSampleLabel();
        state.accept(samplerName, sampleResult.getTime(), sampleResult.isSuccessful());

        long now = System.currentTimeMillis();
        long intervalMs = Long.parseLong(evaluationIntervalSecondsField.getText().trim()) * 1000L;
        if (now - lastWindowMillis >= intervalMs) {
            Map<String, StageResult> report = state.finishAllStages();
            if (!report.isEmpty()) {
                boolean thresholdReached = state.wasThresholdExceeded();
                if (thresholdReached) {
                    stopTestAndReport(report);
                } else {
                    log.info("AdaptiveCapacity listener window summary: {}", reportToString(report));
                }
            }
            lastWindowMillis = now;
        }
    }

    @Override
    public boolean isStats() {
        return false;
    }

    @Override
    public void clearData() {
        state.reset();
        lastWindowMillis = System.currentTimeMillis();
        stopIssued = false;
    }

    private void stopTestAndReport(Map<String, StageResult> report) {
        if (stopIssued) {
            return;
        }
        stopIssued = true;

        SampleResult stopSample = buildStopSample(report);
        if (JMeterContextService.getContext() != null) {
            JMeterContextService.getContext().setPreviousResult(stopSample);
        }

        try {
            if (JMeterContextService.getContext() != null && JMeterContextService.getContext().getEngine() != null) {
                JMeterContextService.getContext().getEngine().stopTest();
            }
        } catch (Exception e) {
            log.error("AdaptiveCapacity listener could not stop the test cleanly", e);
        }

        log.warn("AdaptiveCapacity listener triggered stop. report={} reason={}", reportToString(report), stopSample.getResponseMessage());
    }

    private SampleResult buildStopSample(Map<String, StageResult> report) {
        String message = buildStopReason(report);
        SampleResult stopSample = new SampleResult(0L, 0L);
        stopSample.setSampleLabel("AdaptiveCapacity stop trigger");
        stopSample.setSuccessful(false);
        stopSample.setResponseCode("ADAPTIVE_CAPACITY_STOP");
        stopSample.setResponseMessage(message);
        stopSample.setResponseData(message.getBytes(StandardCharsets.UTF_8));
        stopSample.setDataType(SampleResult.TEXT);
        return stopSample;
    }

    private String buildStopReason(Map<String, StageResult> report) {
        double peakQps = report.values().stream().mapToDouble(result -> result.qps).max().orElse(0.0d);
        long peakLatency = report.values().stream().mapToLong(result -> result.currentLatency).max().orElse(0L);
        StringBuilder message = new StringBuilder("AdaptiveCapacity stop triggered. threshold breach detected; peakLatency=")
                .append(peakLatency)
                .append("ms; peakQps=")
                .append(peakQps)
                .append("; activePolicies=")
                .append(state.getActivePolicies())
                .append("; details=")
                .append(reportToString(report));
        return message.toString();
    }

    private AdaptiveCapacityState buildStateFromCurrentValues() {
        long intervalSeconds = Long.parseLong(evaluationIntervalSecondsField.getText().trim());
        double degradationThreshold = Double.parseDouble(degradationPercentThresholdField.getText().trim());
        long absoluteLatencyThreshold = Long.parseLong(absoluteLatencyThresholdField.getText().trim());
        double errorRateThreshold = Double.parseDouble(errorRateThresholdField.getText().trim());
        long errorCountThreshold = Long.parseLong(errorCountThresholdField.getText().trim());
        Set<AdaptiveCapacityPolicy> activePolicies = parsePolicies(policyModesField.getText());
        return new AdaptiveCapacityState(degradationThreshold, activePolicies, absoluteLatencyThreshold, errorRateThreshold, errorCountThreshold, intervalSeconds > 0 ? intervalSeconds : 1L);
    }

    private Set<AdaptiveCapacityPolicy> parsePolicies(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return EnumSet.of(
                    AdaptiveCapacityPolicy.PERCENT_LATENCY,
                    AdaptiveCapacityPolicy.ABSOLUTE_LATENCY,
                    AdaptiveCapacityPolicy.ERROR_RATE,
                    AdaptiveCapacityPolicy.ERROR_COUNT);
        }

        Set<AdaptiveCapacityPolicy> policies = EnumSet.noneOf(AdaptiveCapacityPolicy.class);
        for (String value : rawValue.split(",")) {
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
                AdaptiveCapacityPolicy.ERROR_COUNT) : policies;
    }

    private String reportToString(Map<String, StageResult> report) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, StageResult> entry : report.entrySet()) {
            StageResult result = entry.getValue();
            builder.append(entry.getKey())
                    .append("={latency:")
                    .append(result.currentLatency)
                    .append("ms,qps:")
                    .append(result.qps)
                    .append(",errorRate:")
                    .append(result.errorRatePercent)
                    .append("%,errorCount:")
                    .append(result.errorCount)
                    .append("}; ");
        }
        return builder.toString();
    }

    private void addField(JPanel panel, GridBagConstraints c, int row, String label, JTextField field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0.35;
        panel.add(new JLabel(label), c);

        c.gridx = 1;
        c.gridy = row;
        c.weightx = 0.65;
        panel.add(field, c);
    }
}
