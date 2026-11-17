package com.unitel.laoads.domain.campaign;

import com.unitel.laoads.domain.campaign.CampaignDefinition.ScheduleDef;
import com.unitel.laoads.domain.common.Times;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

/**
 * Tính thời điểm chạy theo lịch (SRS 3.2.5) và dời giờ gửi khỏi khung giờ không gửi (YC nâng cấp 9.0).
 * Mọi phép tính ngày/giờ theo giờ Lào ({@link Times#ZONE}).
 */
public final class SchedulePlanner {

    static final ZoneId ZONE = Times.ZONE;
    private static final int MAX_SCAN_DAYS = 800;

    private SchedulePlanner() {
    }

    /** Lượt chạy đầu tiên khi chiến dịch được duyệt; null = lịch lặp lại không còn lượt nào. */
    public static Instant firstRun(ScheduleDef s, Instant now) {
        String type = s == null || s.type() == null ? "NOW" : s.type();
        return switch (type) {
            case "ONCE" -> s.startAt() == null || s.startAt().isBefore(now) ? now : s.startAt();
            case "RECURRING" -> nextOccurrence(s, now.minusSeconds(1));
            default -> now;
        };
    }

    /** Lượt kế tiếp sau lượt vừa chạy — chỉ áp dụng RECURRING; null = hết lịch. */
    public static Instant nextAfter(ScheduleDef s, Instant lastRun) {
        if (s == null || !"RECURRING".equals(s.type())) {
            return null;
        }
        return nextOccurrence(s, lastRun);
    }

    /** Nhiều khung thời gian (laoads.com "Khung thời gian 1, 2…"): lượt kế tiếp = sớm nhất trong các khung. */
    static Instant nextOccurrence(ScheduleDef s, Instant after) {
        if (s.frames() == null || s.frames().isEmpty()) {
            return nextInFrame(s, after);
        }
        Instant best = null;
        for (CampaignDefinition.FrameDef f : s.frames()) {
            Instant t = nextInFrame(new ScheduleDef(s.type(), s.startAt(), s.endAt(), f.every(), f.unit(), f.weekdays(),
                    f.sendTime(), s.silentHours(), f.monthDays(), s.maxOccurrences(), null), after);
            if (t != null && (best == null || t.isBefore(best))) {
                best = t;
            }
        }
        return best;
    }

    private static Instant nextInFrame(ScheduleDef s, Instant after) {
        LocalTime time = parseTime(s.sendTime(), LocalTime.of(9, 0));
        Instant startAt = s.startAt() != null ? s.startAt() : after;
        LocalDate startDate = startAt.atZone(ZONE).toLocalDate();
        int every = Math.max(1, s.every() == null ? 1 : s.every());
        String unit = s.unit() == null ? "DAY" : s.unit();
        Set<Integer> weekdays = s.weekdays() == null || s.weekdays().isEmpty()
                ? Set.of(startDate.getDayOfWeek().getValue() % 7) : new HashSet<>(s.weekdays());
        Set<Integer> monthDays = s.monthDays() == null || s.monthDays().isEmpty()
                ? Set.of(startDate.getDayOfMonth()) : new HashSet<>(s.monthDays());
        LocalDate afterDate = after.atZone(ZONE).toLocalDate();
        LocalDate d = afterDate.isAfter(startDate) ? afterDate : startDate;
        for (int i = 0; i < MAX_SCAN_DAYS; i++, d = d.plusDays(1)) {
            if (!matches(unit, every, weekdays, monthDays, startDate, d)) {
                continue;
            }
            Instant candidate = ZonedDateTime.of(d, time, ZONE).toInstant();
            if (!candidate.isAfter(after) || candidate.isBefore(startAt)) {
                continue;
            }
            if (s.endAt() != null && candidate.isAfter(s.endAt())) {
                return null;
            }
            return candidate;
        }
        return null;
    }

    private static boolean matches(String unit, int every, Set<Integer> weekdays, Set<Integer> monthDays,
                                   LocalDate start, LocalDate d) {
        return switch (unit) {
            case "WEEK" -> {
                long weeks = ChronoUnit.WEEKS.between(weekStart(start), weekStart(d));
                yield weeks % every == 0 && weekdays.contains(d.getDayOfWeek().getValue() % 7);
            }
            case "MONTH" -> {
                long months = ChronoUnit.MONTHS.between(start.withDayOfMonth(1), d.withDayOfMonth(1));
                // ngày 29–31 ở tháng ngắn hơn → chạy vào ngày cuối tháng
                int dom = d.getDayOfMonth();
                boolean day = monthDays.contains(dom)
                        || (dom == d.lengthOfMonth() && monthDays.stream().anyMatch(m -> m > d.lengthOfMonth()));
                yield months % every == 0 && day;
            }
            default -> ChronoUnit.DAYS.between(start, d) % every == 0;
        };
    }

    /** Tuần bắt đầu Chủ nhật (khớp weekdays 0 = Chủ nhật của FE). */
    private static LocalDate weekStart(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() % 7);
    }

    /**
     * Nếu {@code t} rơi vào khung giờ không gửi [from, to) thì dời tới {@code to} gần nhất. Khung có thể qua nửa
     * đêm (21:00–07:00). from == to = không chặn.
     */
    public static Instant applySilentHours(Instant t, LocalTime from, LocalTime to) {
        if (from == null || to == null || from.equals(to)) {
            return t;
        }
        ZonedDateTime z = t.atZone(ZONE);
        LocalTime time = z.toLocalTime();
        boolean overnight = from.isAfter(to);
        boolean silent = overnight ? (!time.isBefore(from) || time.isBefore(to)) : (!time.isBefore(from) && time.isBefore(to));
        if (!silent) {
            return t;
        }
        LocalDate day = z.toLocalDate();
        if (overnight && !time.isBefore(from)) {
            day = day.plusDays(1); // sau giờ bắt đầu buổi tối → sáng hôm sau
        }
        return ZonedDateTime.of(day, to, ZONE).toInstant();
    }

    public static LocalTime parseTime(String hhmm, LocalTime def) {
        if (hhmm == null || hhmm.isBlank()) {
            return def;
        }
        try {
            return LocalTime.parse(hhmm.trim());
        } catch (DateTimeParseException e) {
            return def;
        }
    }
}
