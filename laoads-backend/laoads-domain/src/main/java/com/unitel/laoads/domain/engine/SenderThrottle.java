package com.unitel.laoads.domain.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Token bucket theo đầu số để engine không đưa vào gateway nhiều hơn TPS cấu hình (trung bình).
 * Sức chứa = TPS × {@code burstSeconds} (bằng chu kỳ dispatch) để các lượt cách nhau vài giây vẫn đạt đúng TPS trung bình.
 * Adapter gateway thật vẫn phải tự điều tiết tức thời (window SMPP). Trạng thái trong bộ nhớ — giả định 1 instance BE.
 */
public final class SenderThrottle {

    private record Bucket(double tokens, long atNanos, int tps) {
    }

    private final Map<String, Bucket> buckets = new HashMap<>();
    private final double burstSeconds;

    public SenderThrottle(double burstSeconds) {
        this.burstSeconds = burstSeconds;
    }

    /** Lấy 1 lượt gửi cho đầu số; false = đã hết lượt, tin phải chờ. */
    public synchronized boolean tryAcquire(String senderCode, int tps, long nowNanos) {
        double capacity = Math.max(1.0, tps * burstSeconds);
        Bucket b = buckets.get(senderCode);
        double tokens = b == null || b.tps() != tps
                ? capacity
                : Math.min(capacity, b.tokens() + Math.max(0, nowNanos - b.atNanos()) / 1e9 * tps);
        boolean ok = tokens >= 1.0;
        buckets.put(senderCode, new Bucket(ok ? tokens - 1.0 : tokens, nowNanos, tps));
        return ok;
    }
}
