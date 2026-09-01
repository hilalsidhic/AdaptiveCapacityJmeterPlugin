package io.github.hilalsidhic.core.model;

public class StageResult {
    public final String samplerName;
    public final long currentLatency;
    public final long sampleCount;
    public final double qps;
    public final long errorCount;
    public final double errorRatePercent;

    public StageResult(String samplerName, long currentLatency) {
        this(samplerName, currentLatency, 0L, 0.0d, 0L, 0.0d);
    }

    public StageResult(String samplerName, long currentLatency, long sampleCount, double qps) {
        this(samplerName, currentLatency, sampleCount, qps, 0L, 0.0d);
    }

    public StageResult(String samplerName, long currentLatency, long sampleCount, double qps, long errorCount, double errorRatePercent) {
        this.samplerName = samplerName;
        this.currentLatency = currentLatency;
        this.sampleCount = sampleCount;
        this.qps = qps;
        this.errorCount = errorCount;
        this.errorRatePercent = errorRatePercent;
    }
}
