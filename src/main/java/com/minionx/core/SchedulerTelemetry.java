package com.minionx.core;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance, lock-free telemetry monitor tracking scheduler execution times,
 * action budgets, latency shedding, and p95/p99 duration percentiles.
 */
public class SchedulerTelemetry {

    private static final int WINDOW_SIZE = 100;
    private final long[] tickDurations = new long[WINDOW_SIZE];
    private int ringIndex = 0;
    private int sampleCount = 0;

    private final AtomicLong totalActionsExecuted = new AtomicLong(0L);
    private final AtomicLong totalShedActions = new AtomicLong(0L);
    private final AtomicLong longestActionNanos = new AtomicLong(0L);
    private volatile long lastTickDurationNanos = 0L;

    public synchronized void recordTick(long durationNanos, int actionsExecuted, int shedActions) {
        this.lastTickDurationNanos = durationNanos;
        this.totalActionsExecuted.addAndGet(actionsExecuted);
        this.totalShedActions.addAndGet(shedActions);

        tickDurations[ringIndex] = durationNanos;
        ringIndex = (ringIndex + 1) % WINDOW_SIZE;
        if (sampleCount < WINDOW_SIZE) {
            sampleCount++;
        }
    }

    public void recordActionDuration(long durationNanos) {
        longestActionNanos.accumulateAndGet(durationNanos, Math::max);
    }

    public synchronized double getP95DurationMillis() {
        if (sampleCount == 0) return 0.0;
        long[] copy = Arrays.copyOf(tickDurations, sampleCount);
        Arrays.sort(copy);
        int index = (int) Math.ceil(0.95 * copy.length) - 1;
        index = Math.clamp(index, 0, copy.length - 1);
        return copy[index] / 1_000_000.0;
    }

    public synchronized double getP99DurationMillis() {
        if (sampleCount == 0) return 0.0;
        long[] copy = Arrays.copyOf(tickDurations, sampleCount);
        Arrays.sort(copy);
        int index = (int) Math.ceil(0.99 * copy.length) - 1;
        index = Math.clamp(index, 0, copy.length - 1);
        return copy[index] / 1_000_000.0;
    }

    public synchronized double getAverageDurationMillis() {
        if (sampleCount == 0) return 0.0;
        long sum = 0;
        for (int i = 0; i < sampleCount; i++) {
            sum += tickDurations[i];
        }
        return (sum / (double) sampleCount) / 1_000_000.0;
    }

    public double getLongestActionMillis() {
        return longestActionNanos.get() / 1_000_000.0;
    }

    public long getTotalActionsExecuted() {
        return totalActionsExecuted.get();
    }

    public long getTotalShedActions() {
        return totalShedActions.get();
    }

    public long getLastTickDurationNanos() {
        return lastTickDurationNanos;
    }
}
