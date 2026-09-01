package io.github.hilalsidhic.core.percentile.impl;

import io.github.hilalsidhic.core.percentile.PercentileCalculator;

import java.util.ArrayList;
import java.util.List;

public class P99Calculator implements PercentileCalculator {
    @Override
    public long calculate(List<Long> responseTimes) {
        if (responseTimes == null || responseTimes.isEmpty()) {
            return 0L;
        }

        List<Long> sorted = new ArrayList<>(responseTimes);
        sorted.sort(Long::compare);
        int index = (int) Math.ceil(sorted.size() * 0.99) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}
