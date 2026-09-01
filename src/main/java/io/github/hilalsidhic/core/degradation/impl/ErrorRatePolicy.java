package io.github.hilalsidhic.core.degradation.impl;

import io.github.hilalsidhic.core.degradation.DegradationDecision;
import io.github.hilalsidhic.core.degradation.DegradationPolicy;
import io.github.hilalsidhic.core.model.StageResult;

import java.time.LocalDateTime;

public class ErrorRatePolicy implements DegradationPolicy {
    private final double errorRateThresholdPercent;

    public ErrorRatePolicy(double errorRateThresholdPercent) {
        this.errorRateThresholdPercent = errorRateThresholdPercent;
    }

    @Override
    public DegradationDecision evaluate(StageResult prevStage, StageResult currStage) {
        if (currStage == null) {
            return new DegradationDecision(LocalDateTime.now(), true);
        }

        boolean triggered = currStage.errorRatePercent > this.errorRateThresholdPercent;
        return new DegradationDecision(LocalDateTime.now(), !triggered);
    }
}
