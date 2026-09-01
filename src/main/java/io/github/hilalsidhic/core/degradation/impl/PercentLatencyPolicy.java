package io.github.hilalsidhic.core.degradation.impl;

import io.github.hilalsidhic.core.degradation.DegradationDecision;
import io.github.hilalsidhic.core.degradation.DegradationPolicy;
import io.github.hilalsidhic.core.model.StageResult;

import java.time.LocalDateTime;

public class PercentLatencyPolicy implements DegradationPolicy {

    private final double threshold;

    public PercentLatencyPolicy(double threshold) {
        this.threshold = threshold;
    }

    public double getThreshold() {
        return threshold;
    }

    @Override
    public DegradationDecision evaluate(StageResult prevStage, StageResult currStage) {
        if (prevStage == null || currStage == null) {
            return new DegradationDecision(LocalDateTime.now(), true);
        }

        long previousLatency = prevStage.currentLatency;
        if (previousLatency <= 0) {
            return new DegradationDecision(LocalDateTime.now(), true);
        }

        double percentIncrease = ((double) (currStage.currentLatency - previousLatency) / previousLatency) * 100.0;
        if (percentIncrease > threshold) {
            return new DegradationDecision(LocalDateTime.now(), false);
        }
        return new DegradationDecision(LocalDateTime.now(), true);
    }
}
