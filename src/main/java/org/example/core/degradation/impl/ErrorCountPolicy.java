package org.example.core.degradation.impl;

import org.example.core.degradation.DegradationDecision;
import org.example.core.degradation.DegradationPolicy;
import org.example.core.model.StageResult;

import java.time.LocalDateTime;

public class ErrorCountPolicy implements DegradationPolicy {
    private final long errorCountThreshold;

    public ErrorCountPolicy(long errorCountThreshold) {
        this.errorCountThreshold = errorCountThreshold;
    }

    @Override
    public DegradationDecision evaluate(StageResult prevStage, StageResult currStage) {
        if (currStage == null) {
            return new DegradationDecision(LocalDateTime.now(), true);
        }

        boolean triggered = currStage.errorCount > this.errorCountThreshold;
        return new DegradationDecision(LocalDateTime.now(), !triggered);
    }
}
