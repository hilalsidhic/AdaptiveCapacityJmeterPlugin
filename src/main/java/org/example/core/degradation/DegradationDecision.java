package org.example.core.degradation;

import java.time.LocalDateTime;

public class DegradationDecision {
    public LocalDateTime time;
    public boolean decision;

    public DegradationDecision(LocalDateTime time, boolean decision) {
        this.time = time;
        this.decision = decision;
    }
}
