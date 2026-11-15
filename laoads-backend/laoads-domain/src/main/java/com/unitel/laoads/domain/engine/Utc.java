package com.unitel.laoads.domain.engine;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Cột TIMESTAMP lưu giờ UTC (Hibernate {@code jdbc.time_zone: UTC}, AuditWriter cũng vậy). Mọi câu lệnh
 * JdbcTemplate phải bind / đọc qua đây để không lệch theo múi giờ JVM.
 */
public final class Utc {

    private Utc() {
    }

    public static LocalDateTime ts(Instant i) {
        return i == null ? null : LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime t = rs.getObject(column, LocalDateTime.class);
        return t == null ? null : t.toInstant(ZoneOffset.UTC);
    }
}
