package com.unitel.laoads.domain.common;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Quy ước thời gian: DB lưu UTC (hibernate.jdbc.time_zone=UTC), API trả ISO-8601 có offset
 * theo giờ Lào (+07:00), vd {@code 2026-11-15T09:30:00+07:00}.
 */
public final class Times {

    public static final ZoneId ZONE = ZoneId.of("Asia/Vientiane");

    private Times() {
    }

    /** 00:00 giờ Lào của ngày chứa {@code t}. */
    public static Instant laosDayStart(Instant t) {
        return t.atZone(ZONE).toLocalDate().atStartOfDay(ZONE).toInstant();
    }

    /** 00:00 giờ Lào của ngày kế tiếp. */
    public static Instant nextLaosDayStart(Instant t) {
        return t.atZone(ZONE).toLocalDate().plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    public static OffsetDateTime toOffset(Instant instant) {
        if (instant == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(instant.truncatedTo(ChronoUnit.SECONDS), ZONE);
    }
}
