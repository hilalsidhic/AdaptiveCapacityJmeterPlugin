package io.github.hilalsidhic.jmeter;

import org.apache.jmeter.gui.util.MenuFactory;
import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.visualizers.gui.AbstractVisualizer;
import io.github.hilalsidhic.core.degradation.AdaptiveCapacityPolicy;
import io.github.hilalsidhic.core.model.StageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdaptiveCapacityListenerGui extends AbstractVisualizer implements TestStateListener {
    private static final Logger log = LoggerFactory.getLogger(AdaptiveCapacityListenerGui.class);

    private final JTextField policyModesField = new JTextField("PERCENT_LATENCY,ABSOLUTE_LATENCY,ERROR_RATE,ERROR_COUNT");
    private final JTextField evaluationIntervalSecondsField = new JTextField("5");
    private final JTextField degradationPercentThresholdField = new JTextField("10.0");
    private final JTextField absoluteLatencyThresholdField = new JTextField("2000");
    private final JTextField errorRateThresholdField = new JTextField("5.0");
    private final JTextField errorCountThresholdField = new JTextField("10");

    private volatile AdaptiveCapacityState state;
    private final Object windowLock = new Object();
    private volatile long lastWindowMillis = System.currentTimeMillis();
    private volatile long cachedIntervalMs = 5_000L;
    private final AtomicBoolean stopIssued = new AtomicBoolean(false);

    public AdaptiveCapacityListenerGui() {
        super();
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);

        VerticalPanel mainPanel = new VerticalPanel();

        JPanel helpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel helpLabel = new JLabel("<html><b>Adaptive Capacity Listener</b> &bull; Multi-policy degradation monitor &bull; <a href='https://github.com/hilalsidhic/AdaptiveCapacityJmeterPlugin'>Documentation & Wiki</a></html>");
        helpPanel.add(helpLabel);
        mainPanel.add(helpPanel);

        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Adaptive Capacity Configuration"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        policyModesField.setToolTipText("Comma-separated active policies: PERCENT_LATENCY, ABSOLUTE_LATENCY, ERROR_RATE, ERROR_COUNT");
        evaluationIntervalSecondsField.setToolTipText("Duration of each evaluation window in seconds (default: 5)");
        degradationPercentThresholdField.setToolTipText("Latency percentage growth ceiling compared to prior stage (e.g. 10.0%)");
        absoluteLatencyThresholdField.setToolTipText("Maximum acceptable latency ceiling in milliseconds (e.g. 2000 ms)");
        errorRateThresholdField.setToolTipText("Maximum acceptable error rate percentage (e.g. 5.0%)");
        errorCountThresholdField.setToolTipText("Maximum acceptable error count ceiling per window (e.g. 10)");

        addField(configPanel, c, 0, "Policy Modes:", policyModesField);
        addField(configPanel, c, 1, "Evaluation Interval (sec):", evaluationIntervalSecondsField);
        addField(configPanel, c, 2, "Latency Degradation Threshold (%):", degradationPercentThresholdField);
        addField(configPanel, c, 3, "Absolute Latency Threshold (ms):", absoluteLatencyThresholdField);
        addField(configPanel, c, 4, "Error Rate Threshold (%):", errorRateThresholdField);
        addField(configPanel, c, 5, "Error Count Threshold:", errorCountThresholdField);

        mainPanel.add(configPanel);
        add(mainPanel, BorderLayout.CENTER);

        state = buildStateFromCurrentValues();
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
    public Collection<String> getMenuCategories() {
        return Collections.singletonList(MenuFactory.LISTENERS);
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
        try {
            element.setPolicyModes(policyModesField.getText());
            long intervalSeconds = Long.parseLong(evaluationIntervalSecondsField.getText().trim());
            element.setEvaluationIntervalSeconds(intervalSeconds);
            element.setDegradationPercentThreshold(Double.parseDouble(degradationPercentThresholdField.getText().trim()));
            element.setAbsoluteLatencyThresholdMs(Long.parseLong(absoluteLatencyThresholdField.getText().trim()));
            element.setErrorRateThresholdPercent(Double.parseDouble(errorRateThresholdField.getText().trim()));
            element.setErrorCountThreshold(Long.parseLong(errorCountThresholdField.getText().trim()));
            cachedIntervalMs = intervalSeconds * 1000L;
        } catch (NumberFormatException e) {
            log.warn("Invalid parameter value in AdaptiveCapacity listener config, using defaults", e);
        }
        state = buildStateFromCurrentValues();
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        if (!(element instanceof AdaptiveCapacityListenerTestElement)) {
            return;
        }
        AdaptiveCapacityListenerTestElement listener = (AdaptiveCapacityListenerTestElement) element;
        policyModesField.setText(listener.getPolicyModes());
        long intervalSeconds = listener.getEvaluationIntervalSeconds();
        evaluationIntervalSecondsField.setText(Long.toString(intervalSeconds));
        degradationPercentThresholdField.setText(Double.toString(listener.getDegradationPercentThreshold()));
        absoluteLatencyThresholdField.setText(Long.toString(listener.getAbsoluteLatencyThresholdMs()));
        errorRateThresholdField.setText(Double.toString(listener.getErrorRateThresholdPercent()));
        errorCountThresholdField.setText(Long.toString(listener.getErrorCountThreshold()));
        cachedIntervalMs = intervalSeconds * 1000L;
        state = buildStateFromCurrentValues();
    }

    @Override
    public void clearGui() {
        super.clearGui();
        policyModesField.setText("PERCENT_LATENCY,ABSOLUTE_LATENCY,ERROR_RATE,ERROR_COUNT");
        evaluationIntervalSecondsField.setText("5");
        degradationPercentThresholdField.setText("10.0");
        absoluteLatencyThresholdField.setText("2000");
        errorRateThresholdField.setText("5.0");
        errorCountThresholdField.setText("10");
        cachedIntervalMs = 5_000L;
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
        long intervalMs = cachedIntervalMs;
        if (now - lastWindowMillis >= intervalMs) {
            synchronized (windowLock) {
                if (now - lastWindowMillis < intervalMs) {
                    return;
                }

                Map<String, StageResult> report = state.finishAllStages();
                lastWindowMillis = now;
                if (!report.isEmpty()) {
                    boolean thresholdReached = state.wasThresholdExceeded();
                    if (thresholdReached) {
                        stopTestAndReport(report);
                    } else {
                        log.info("AdaptiveCapacity listener window summary: {}", reportToString(report));
                    }
                }
            }
        }
    }

    @Override
    public boolean isStats() {
        return false;
    }

    @Override
    public void clearData() {
        state = buildStateFromCurrentValues();
        lastWindowMillis = System.currentTimeMillis();
        stopIssued.set(false);
    }

    @Override
    public void testStarted() {
        clearData();
    }

    @Override
    public void testStarted(String host) {
        testStarted();
    }

    @Override
    public void testEnded() {
        Map<String, StageResult> finalReport = state.finishAllStages();
        if (!finalReport.isEmpty()) {
            boolean thresholdReached = state.wasThresholdExceeded();
            if (thresholdReached) {
                log.warn("AdaptiveCapacity listener final report: breach detected: {}", reportToString(finalReport));
                stopTestAndReport(finalReport);
            } else {
                log.info("AdaptiveCapacity listener final report: {}", reportToString(finalReport));
            }
        }
        clearData();
    }

    @Override
    public void testEnded(String host) {
        testEnded();
    }

    private void stopTestAndReport(Map<String, StageResult> report) {
        if (!stopIssued.compareAndSet(false, true)) {
            return;
        }

        SampleResult stopSample = buildStopSample(report);
        if (JMeterContextService.getContext() != null) {
            JMeterContextService.getContext().setPreviousResult(stopSample);
        }

        notifySampleListeners(stopSample);

        // 1. Static engine stop
        try {
            org.apache.jmeter.engine.StandardJMeterEngine.stopEngine();
        } catch (Throwable e) {
            log.debug("StandardJMeterEngine.stopEngine() exception", e);
        }

        // 2. Context engine stop
        try {
            if (JMeterContextService.getContext() != null && JMeterContextService.getContext().getEngine() != null) {
                JMeterContextService.getContext().getEngine().stopTest();
            }
        } catch (Throwable e) {
            log.debug("Context engine stopTest() exception", e);
        }

        // 3. Reflection engine stop
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

        log.warn("AdaptiveCapacity listener triggered stop. report={} reason={}", reportToString(report), stopSample.getResponseMessage());
    }

    private SampleResult buildStopSample(Map<String, StageResult> report) {
        String message = buildStopReason(report);
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

    private String buildStopReason(Map<String, StageResult> report) {
        double peakQps = report.values().stream().mapToDouble(result -> result.qps).max().orElse(0.0d);
        long peakLatency = report.values().stream().mapToLong(result -> result.currentLatency).max().orElse(0L);
        StringBuilder message = new StringBuilder("AdaptiveCapacity stop triggered. threshold breach detected; peakLatency=")
                .append(peakLatency)
                .append("ms; peakQps=")
                .append(peakQps)
                .append("; stageSummary=");

        boolean first = true;
        for (Map.Entry<String, StageResult> entry : report.entrySet()) {
            if (!first) {
                message.append(" | ");
            }
            StageResult r = entry.getValue();
            message.append(entry.getKey())
                    .append(":[p95=")
                    .append(r.currentLatency)
                    .append("ms,qps=")
                    .append(r.qps)
                    .append(",errRate=")
                    .append(r.errorRatePercent)
                    .append("%,errCount=")
                    .append(r.errorCount)
                    .append("]");
            first = false;
        }
        return message.toString();
    }

    private void addField(JPanel panel, GridBagConstraints c, int row, String label, JTextField field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0.0;
        panel.add(new JLabel(label), c);

        c.gridx = 1;
        c.weightx = 1.0;
        panel.add(field, c);
    }

    private AdaptiveCapacityState buildStateFromCurrentValues() {
        try {
            double degradation = Double.parseDouble(degradationPercentThresholdField.getText().trim());
            long absoluteLatency = Long.parseLong(absoluteLatencyThresholdField.getText().trim());
            double errorRate = Double.parseDouble(errorRateThresholdField.getText().trim());
            long errorCount = Long.parseLong(errorCountThresholdField.getText().trim());
            double interval = Double.parseDouble(evaluationIntervalSecondsField.getText().trim());
            cachedIntervalMs = (long) (interval * 1000.0d);

            return new AdaptiveCapacityState(
                    degradation,
                    parsePolicyModes(policyModesField.getText().trim()),
                    absoluteLatency,
                    errorRate,
                    errorCount,
                    interval
            );
        } catch (Exception e) {
            log.warn("Could not parse configuration fields, using defaults", e);
            cachedIntervalMs = 5_000L;
            return new AdaptiveCapacityState();
        }
    }

    private Set<AdaptiveCapacityPolicy> parsePolicyModes(String modes) {
        if (modes == null || modes.trim().isEmpty()) {
            return EnumSet.of(
                    AdaptiveCapacityPolicy.PERCENT_LATENCY,
                    AdaptiveCapacityPolicy.ABSOLUTE_LATENCY,
                    AdaptiveCapacityPolicy.ERROR_RATE,
                    AdaptiveCapacityPolicy.ERROR_COUNT
            );
        }
        Set<AdaptiveCapacityPolicy> set = EnumSet.noneOf(AdaptiveCapacityPolicy.class);
        for (String token : modes.split(",")) {
            String normalized = token.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                set.add(AdaptiveCapacityPolicy.valueOf(normalized.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // ignore unsupported policy gracefully
            }
        }
        return set.isEmpty() ? EnumSet.of(
                AdaptiveCapacityPolicy.PERCENT_LATENCY,
                AdaptiveCapacityPolicy.ABSOLUTE_LATENCY,
                AdaptiveCapacityPolicy.ERROR_RATE,
                AdaptiveCapacityPolicy.ERROR_COUNT
        ) : set;
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
