package org.example.core.percentile;

import org.example.core.percentile.impl.P95Calculator;
import org.example.core.percentile.impl.P99Calculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PercentileCalculatorTest {

    @Test
    void p95CalculatorReturnsHighTailValue() {
        List<Long> latencies = List.of(40L, 60L, 80L, 100L, 120L, 140L, 160L, 180L, 200L, 1000L);

        assertEquals(1000L, new P95Calculator().calculate(latencies));
    }

    @Test
    void p95CalculatorHandlesEmptyInput() {
        assertEquals(0L, new P95Calculator().calculate(List.of()));
        assertEquals(0L, new P95Calculator().calculate(null));
    }

    @Test
    void p99CalculatorReturnsLargestValueInTheSample() {
        List<Long> latencies = List.of(40L, 60L, 80L, 100L, 120L, 140L, 160L, 180L, 200L, 1000L);

        assertEquals(1000L, new P99Calculator().calculate(latencies));
    }

    @Test
    void p99CalculatorHandlesEmptyInput() {
        assertEquals(0L, new P99Calculator().calculate(List.of()));
        assertEquals(0L, new P99Calculator().calculate(null));
    }
}
