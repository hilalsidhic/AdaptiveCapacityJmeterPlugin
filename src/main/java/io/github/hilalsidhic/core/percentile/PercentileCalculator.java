package io.github.hilalsidhic.core.percentile;

import java.util.List;

public interface PercentileCalculator {
    long calculate(List<Long> responseTimes);
}
