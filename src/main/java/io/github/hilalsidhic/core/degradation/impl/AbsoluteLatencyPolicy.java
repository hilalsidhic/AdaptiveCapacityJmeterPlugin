package io.github.hilalsidhic.core.degradation.impl;

import io.github.hilalsidhic.core.degradation.DegradationDecision;
import io.github.hilalsidhic.core.degradation.DegradationPolicy;
import io.github.hilalsidhic.core.model.StageResult;

import java.time.LocalDateTime;

public class AbsoluteLatencyPolicy implements DegradationPolicy {
    private final long threshold;

    public AbsoluteLatencyPolicy(long threshold) {
        this.threshold = threshold;
    }

    @Override
    public DegradationDecision evaluate(StageResult prevStage, StageResult currStage) {
        if (currStage == null) {
            return new DegradationDecision(LocalDateTime.now(), true);
        }

        if (currStage.currentLatency > this.threshold) {
            return new DegradationDecision(LocalDateTime.now(), false);
        }
        return new DegradationDecision(LocalDateTime.now(), true);
    }
}
