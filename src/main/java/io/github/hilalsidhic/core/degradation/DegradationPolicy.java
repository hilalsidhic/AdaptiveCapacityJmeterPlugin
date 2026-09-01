package io.github.hilalsidhic.core.degradation;

import io.github.hilalsidhic.core.model.StageResult;

public interface DegradationPolicy {
    public DegradationDecision evaluate(
            StageResult prevStage,
            StageResult currStage
    );
}
