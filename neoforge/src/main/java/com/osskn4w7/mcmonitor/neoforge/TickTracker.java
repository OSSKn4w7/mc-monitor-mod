package com.osskn4w7.mcmonitor.neoforge;

// 用最近 100 个 tick 的实际间隔估算 TPS
public final class TickTracker {

    private final long[] deltasNanos = new long[100];
    private int idx;
    private boolean filled;
    private long lastTickNanos;

    public void tick() {
        long now = System.nanoTime();
        if (lastTickNanos > 0) {
            deltasNanos[idx] = now - lastTickNanos;
            idx = (idx + 1) % deltasNanos.length;
            if (idx == 0) filled = true;
        }
        lastTickNanos = now;
    }

    public double tps() {
        int n = filled ? deltasNanos.length : idx;
        if (n == 0) return 20.0;
        long sum = 0;
        for (int i = 0; i < n; i++) sum += deltasNanos[i];
        double avgMillis = sum / (double) n / 1_000_000.0;
        if (avgMillis <= 0) return 20.0;
        return Math.min(20.0, 1000.0 / avgMillis);
    }
}
