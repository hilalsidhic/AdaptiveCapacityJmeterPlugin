package org.example.core.accumulator;

import org.example.core.model.StageResult;
import org.example.core.percentile.PercentileCalculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResultAccumulator {
    private final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
    private long sampleCount = 0L;
    private long errorCount = 0L;

    public void recordLatency(long latencyMs) {
        recordLatency(latencyMs, true);
    }

    public void recordLatency(long latencyMs, boolean success) {
        synchronized (latencies) {
            latencies.add(latencyMs);
            sampleCount++;
            if (!success) {
                errorCount++;
            }
        }
    }

    public void reset() {
        synchronized (latencies) {
            latencies.clear();
            sampleCount = 0L;
            errorCount = 0L;
        }
    }

    public StageResult calculateAndReset(PercentileCalculator pc) {
        return calculateAndReset(pc, 60.0d);
    }

    public StageResult calculateAndReset(PercentileCalculator pc, double windowSeconds) {
        List<Long> snapshot;
        long count;
        long errors;
        synchronized (latencies) {
            if (latencies.isEmpty()) {
                return new StageResult("", 0L, 0L, 0.0d, 0L, 0.0d);
            }
            snapshot = new ArrayList<>(latencies);
            count = sampleCount;
            errors = errorCount;
            latencies.clear();
            sampleCount = 0L;
            errorCount = 0L;
        }

        long curr = pc.calculate(snapshot);
        double normalizedWindow = windowSeconds > 0 ? windowSeconds : 1.0d;
        double qps = count / normalizedWindow;
        double errorRate = count == 0 ? 0.0d : ((double) errors / count) * 100.0d;
        return new StageResult("", curr, count, qps, errors, errorRate);
    }
}
