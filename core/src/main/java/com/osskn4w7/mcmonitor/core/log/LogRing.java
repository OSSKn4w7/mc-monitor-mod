package com.osskn4w7.mcmonitor.core.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

// 最近日志环形缓冲：线程安全，容量固定，旧的自动淘汰
public final class LogRing {

    private final ArrayDeque<String> lines;
    private final int capacity;

    public LogRing(int capacity) {
        this.capacity = capacity;
        this.lines = new ArrayDeque<>(capacity);
    }

    public synchronized void add(String line) {
        if (lines.size() >= capacity) lines.pollFirst();
        lines.addLast(line);
    }

    // 取最后 n 条（n <= 0 返回空列表）
    public synchronized List<String> tail(int n) {
        List<String> out = new ArrayList<>();
        if (n <= 0) return out;
        int skip = Math.max(0, lines.size() - n);
        int idx = 0;
        for (String line : lines) {
            if (idx++ >= skip) out.add(line);
        }
        return out;
    }

    public synchronized int size() {
        return lines.size();
    }
}
