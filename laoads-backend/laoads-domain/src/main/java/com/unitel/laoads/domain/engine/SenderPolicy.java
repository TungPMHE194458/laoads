package com.unitel.laoads.domain.engine;

import com.unitel.laoads.domain.campaign.CampaignDefinition;
import com.unitel.laoads.domain.campaign.SchedulePlanner;
import com.unitel.laoads.domain.settings.NotificationSettings;
import com.unitel.laoads.domain.settings.SenderNumber;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chính sách gửi hiệu lực của một tin theo thứ tự ưu tiên: chiến dịch (nếu tự đặt) &gt; đầu số (nếu ghi đè) &gt; chung.
 * Hàm thuần — engine (CampaignRunner, MessageDispatcher) dùng chung để hai nơi tính giống nhau.
 */
public final class SenderPolicy {

    private SenderPolicy() {
    }

    /** Mã đầu số chuẩn hoá (trim + viết hoa) hoặc null nếu hành động không gắn đầu số. */
    public static String key(String senderId) {
        if (senderId == null || senderId.isBlank()) {
            return null;
        }
        return senderId.trim().toUpperCase(Locale.ROOT);
    }

    public static Map<String, SenderNumber> byCode(Collection<SenderNumber> senders) {
        return senders.stream().collect(Collectors.toMap(s -> key(s.getCode()), Function.identity(), (a, b) -> a));
    }

    /**
     * Khung giờ không gửi: của chiến dịch nếu chiến dịch bật; ngược lại của đầu số nếu đầu số ghi đè
     * (true = khung riêng, false = không chặn); ngược lại mặc định hệ thống nếu bật. null = không chặn.
     */
    public static LocalTime[] silentWindow(CampaignDefinition def, SenderNumber sender, NotificationSettings global) {
        var sh = def == null || def.schedule() == null ? null : def.schedule().silentHours();
        if (sh != null && sh.enabled()) {
            return new LocalTime[]{SchedulePlanner.parseTime(sh.from(), null), SchedulePlanner.parseTime(sh.to(), null)};
        }
        if (sender != null && sender.getSilentEnabled() != null) {
            if (!sender.getSilentEnabled()) {
                return null;
            }
            LocalTime from = SchedulePlanner.parseTime(sender.getSilentFrom(), null);
            LocalTime to = SchedulePlanner.parseTime(sender.getSilentTo(), null);
            if (from != null && to != null) {
                return new LocalTime[]{from, to};
            }
        }
        if (global.silentHoursEnabled()) {
            return new LocalTime[]{global.silentFromTime(), global.silentToTime()};
        }
        return null;
    }

    public static int retryMaxAttempts(SenderNumber sender, NotificationSettings global) {
        return sender != null && sender.getRetryMax() != null ? sender.getRetryMax() : global.retryMaxAttempts();
    }

    public static int retryIntervalMinutes(SenderNumber sender, NotificationSettings global) {
        return sender != null && sender.getRetryIntervalMin() != null ? sender.getRetryIntervalMin()
                : global.retryIntervalMinutes();
    }

    /**
     * Tin thứ {@code overflowIndex} (0-based) vượt ngân sách TPS được dời lịch rải đều theo TPS:
     * now + ceil((overflowIndex + 1) / tps) giây. Dời DUE_AT còn giúp tin của đầu số khác không bị chặn phía sau.
     */
    public static Instant throttledDueAt(Instant now, int overflowIndex, int tps) {
        long seconds = (overflowIndex + (long) tps) / tps;
        return now.plusSeconds(Math.max(1, seconds));
    }
}
