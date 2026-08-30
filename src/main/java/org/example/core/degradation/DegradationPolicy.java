package org.example.core.degradation;

import org.example.core.model.StageResult;

public interface DegradationPolicy {
    public DegradationDecision evaluate(
            StageResult prevStage,
            StageResult currStage
    );
}
