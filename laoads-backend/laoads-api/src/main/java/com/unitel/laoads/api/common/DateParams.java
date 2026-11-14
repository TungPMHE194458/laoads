package com.unitel.laoads.api.common;

import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.Times;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Parse query {@code from}/{@code to}: nhận ngày {@code yyyy-MM-dd} (theo giờ Lào) hoặc ISO-8601 có offset.
 * {@code from} = đầu ngày (inclusive); {@code to} dạng ngày = hết ngày đó (exclusive đầu ngày hôm sau).
 */
public final class DateParams {

    private DateParams() {
    }

    public static Instant from(String name, String value) {
        return parse(name, value, false);
    }

    public static Instant to(String name, String value) {
        return parse(name, value, true);
    }

    private static Instant parse(String name, String value, boolean endExclusive) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            if (v.length() == 10) {
                LocalDate d = LocalDate.parse(v);
                return (endExclusive ? d.plusDays(1) : d).atStartOfDay(Times.ZONE).toInstant();
            }
            return OffsetDateTime.parse(v).toInstant();
        } catch (DateTimeParseException e) {
            throw LaoadsException.withKey(ErrorCode.INVALID_PARAM, "error.INVALID_PARAM.dateTime",
                    "Tham số " + name + " phải là ngày yyyy-MM-dd hoặc thời điểm ISO-8601", name);
        }
    }
}
