package org.example.core.model;

public class StageResult {
    public String samplerName;
    public long currentLatency;
    public long sampleCount;
    public double qps;
    public long errorCount;
    public double errorRatePercent;

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
