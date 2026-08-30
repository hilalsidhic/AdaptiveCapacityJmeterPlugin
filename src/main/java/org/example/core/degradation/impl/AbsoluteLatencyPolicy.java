package org.example.core.degradation.impl;

import org.example.core.degradation.DegradationDecision;
import org.example.core.degradation.DegradationPolicy;
import org.example.core.model.StageResult;

import java.time.LocalDateTime;

public class AbsoluteLatencyPolicy implements DegradationPolicy {
    private final long threshold;

    public AbsoluteLatencyPolicy(long threshold) {
        this.threshold = threshold;
    }

    @Override
    public DegradationDecision evaluate(StageResult prevStage, StageResult currStage) {
        if (prevStage == null || currStage == null) {
            return new DegradationDecision(LocalDateTime.now(), true);
        }

        long increase = Math.abs(currStage.currentLatency - prevStage.currentLatency);
        if (increase > this.threshold) {
            return new DegradationDecision(LocalDateTime.now(), false);
        }
        return new DegradationDecision(LocalDateTime.now(), true);
    }
}
