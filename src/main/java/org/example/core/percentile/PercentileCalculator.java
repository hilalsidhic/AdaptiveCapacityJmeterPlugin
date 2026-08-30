package org.example.core.percentile;

import java.util.List;

public interface PercentileCalculator {
    long calculate(List<Long> responseTimes);
}
