package com.unitel.laoads.domain.campaign;

import com.unitel.laoads.domain.campaign.CampaignDefinition.ScheduleDef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulePlannerTest {

    private static Instant vte(int y, int m, int d, int h, int min) {
        return ZonedDateTime.of(y, m, d, h, min, 0, 0, SchedulePlanner.ZONE).toInstant();
    }

    private static ScheduleDef recurring(Instant start, Instant end, int every, String unit, List<Integer> weekdays,
                                         String time) {
        return new ScheduleDef("RECURRING", start, end, every, unit, weekdays, time, null);
    }

    @Test
    void nowAndOnce() {
        Instant now = vte(2026, 11, 15, 10, 0);
        assertThat(SchedulePlanner.firstRun(new ScheduleDef("NOW", null, null, null, null, null, null, null), now)).isEqualTo(now);
        Instant future = vte(2026, 11, 20, 8, 0);
        assertThat(SchedulePlanner.firstRun(new ScheduleDef("ONCE", future, null, null, null, null, null, null), now)).isEqualTo(future);
        // ONCE đã quá giờ → chạy ngay
        assertThat(SchedulePlanner.firstRun(new ScheduleDef("ONCE", vte(2026, 11, 1, 8, 0), null, null, null, null, null, null), now))
                .isEqualTo(now);
        assertThat(SchedulePlanner.nextAfter(new ScheduleDef("ONCE", future, null, null, null, null, null, null), future)).isNull();
    }

    @Test
    void dailyEveryTwoDaysUntilEnd() {
        ScheduleDef s = recurring(vte(2026, 11, 15, 0, 0), vte(2026, 11, 20, 0, 0), 2, "DAY", null, "09:30");
        Instant first = SchedulePlanner.firstRun(s, vte(2026, 11, 15, 8, 0));
        assertThat(first).isEqualTo(vte(2026, 11, 15, 9, 30));
        Instant second = SchedulePlanner.nextAfter(s, first);
        assertThat(second).isEqualTo(vte(2026, 11, 17, 9, 30));
        assertThat(SchedulePlanner.nextAfter(s, second)).isEqualTo(vte(2026, 11, 19, 9, 30));
        assertThat(SchedulePlanner.nextAfter(s, vte(2026, 11, 19, 9, 30))).isNull(); // 21/11 vượt endAt
    }

    @Test
    void weeklyOnMondayAndThursday() {
        // 15/11/2026 là Chủ nhật; weekdays 1 = Thứ hai, 4 = Thứ năm
        ScheduleDef s = recurring(vte(2026, 11, 15, 0, 0), null, 1, "WEEK", List.of(1, 4), "08:00");
        Instant first = SchedulePlanner.firstRun(s, vte(2026, 11, 15, 12, 0));
        assertThat(first).isEqualTo(vte(2026, 11, 16, 8, 0));
        assertThat(SchedulePlanner.nextAfter(s, first)).isEqualTo(vte(2026, 11, 19, 8, 0));
    }

    @Test
    void monthlyClampsToShortMonths() {
        ScheduleDef s = recurring(vte(2027, 1, 31, 0, 0), null, 1, "MONTH", null, "10:00");
        Instant jan = SchedulePlanner.firstRun(s, vte(2027, 1, 30, 0, 0));
        assertThat(jan).isEqualTo(vte(2027, 1, 31, 10, 0));
        assertThat(SchedulePlanner.nextAfter(s, jan)).isEqualTo(vte(2027, 2, 28, 10, 0));
    }

    @Test
    void silentHoursOvernightWindow() {
        LocalTime from = LocalTime.of(21, 0);
        LocalTime to = LocalTime.of(7, 0);
        // 22:30 → 07:00 hôm sau
        assertThat(SchedulePlanner.applySilentHours(vte(2026, 11, 15, 22, 30), from, to)).isEqualTo(vte(2026, 11, 16, 7, 0));
        // 05:00 → 07:00 cùng ngày
        assertThat(SchedulePlanner.applySilentHours(vte(2026, 11, 16, 5, 0), from, to)).isEqualTo(vte(2026, 11, 16, 7, 0));
        // 12:00 → giữ nguyên
        assertThat(SchedulePlanner.applySilentHours(vte(2026, 11, 16, 12, 0), from, to)).isEqualTo(vte(2026, 11, 16, 12, 0));
    }

    @Test
    void silentHoursSameDayWindow() {
        Instant t = vte(2026, 11, 16, 12, 30);
        assertThat(SchedulePlanner.applySilentHours(t, LocalTime.of(12, 0), LocalTime.of(13, 0))).isEqualTo(vte(2026, 11, 16, 13, 0));
        assertThat(SchedulePlanner.applySilentHours(t, LocalTime.of(9, 0), LocalTime.of(9, 0))).isEqualTo(t);
    }
}
