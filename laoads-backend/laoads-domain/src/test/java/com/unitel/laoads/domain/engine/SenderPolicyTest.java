package com.unitel.laoads.domain.engine;

import com.unitel.laoads.domain.campaign.CampaignDefinition;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ScheduleDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.SilentHoursDef;
import com.unitel.laoads.domain.common.Times;
import com.unitel.laoads.domain.settings.NotificationSettings;
import com.unitel.laoads.domain.settings.SenderNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SenderPolicyTest {

    private static final NotificationSettings GLOBAL =
            new NotificationSettings(3, 30, true, true, "21:00", "07:00", "LO");

    private static CampaignDefinition def(SilentHoursDef sh) {
        return new CampaignDefinition(List.of(), new ScheduleDef("NOW", null, null, null, null, null, null, sh), null, null);
    }

    private static SenderNumber sender(Boolean silent, String from, String to) {
        SenderNumber s = new SenderNumber();
        s.setCode("118");
        s.setSilentEnabled(silent);
        s.setSilentFrom(from);
        s.setSilentTo(to);
        return s;
    }

    @Test
    @DisplayName("khung giờ: chiến dịch tự bật > đầu số ghi đè > chung")
    void silentPrecedence() {
        CampaignDefinition own = def(new SilentHoursDef(true, "22:00", "06:00"));
        CampaignDefinition inherit = def(new SilentHoursDef(false, null, null));

        assertThat(SenderPolicy.silentWindow(own, sender(true, "20:00", "08:00"), GLOBAL))
                .containsExactly(LocalTime.of(22, 0), LocalTime.of(6, 0));
        assertThat(SenderPolicy.silentWindow(inherit, sender(true, "20:00", "08:00"), GLOBAL))
                .containsExactly(LocalTime.of(20, 0), LocalTime.of(8, 0));
        assertThat(SenderPolicy.silentWindow(inherit, sender(false, null, null), GLOBAL))
                .as("đầu số tắt khung giờ").isNull();
        assertThat(SenderPolicy.silentWindow(inherit, sender(null, null, null), GLOBAL))
                .containsExactly(LocalTime.of(21, 0), LocalTime.of(7, 0));
        assertThat(SenderPolicy.silentWindow(inherit, null, GLOBAL))
                .containsExactly(LocalTime.of(21, 0), LocalTime.of(7, 0));
    }

    @Test
    @DisplayName("retry: đầu số ghi đè, null thì theo chung; mã chuẩn hoá viết hoa")
    void retryAndKey() {
        SenderNumber s = new SenderNumber();
        assertThat(SenderPolicy.retryMaxAttempts(s, GLOBAL)).isEqualTo(3);
        assertThat(SenderPolicy.retryIntervalMinutes(null, GLOBAL)).isEqualTo(30);
        s.setRetryMax(0);
        s.setRetryIntervalMin(5);
        assertThat(SenderPolicy.retryMaxAttempts(s, GLOBAL)).isZero();
        assertThat(SenderPolicy.retryIntervalMinutes(s, GLOBAL)).isEqualTo(5);
        assertThat(SenderPolicy.key(" unitel ")).isEqualTo("UNITEL");
        assertThat(SenderPolicy.key(" ")).isNull();
    }

    @Test
    @DisplayName("tin vượt TPS được rải đều: TPS 10 → 10 tin đầu +1s, tin 11 +2s")
    void throttledDueAt() {
        Instant now = Instant.parse("2026-11-16T03:00:00Z");
        assertThat(SenderPolicy.throttledDueAt(now, 0, 10)).isEqualTo(now.plusSeconds(1));
        assertThat(SenderPolicy.throttledDueAt(now, 9, 10)).isEqualTo(now.plusSeconds(1));
        assertThat(SenderPolicy.throttledDueAt(now, 10, 10)).isEqualTo(now.plusSeconds(2));
        assertThat(SenderPolicy.throttledDueAt(now, 0, 1)).isEqualTo(now.plusSeconds(1));
        assertThat(SenderPolicy.throttledDueAt(now, 1, 1)).isEqualTo(now.plusSeconds(2));
    }

    @Test
    @DisplayName("token bucket: đầy TPS × burst, hết thì chặn, nạp lại theo thời gian")
    void throttleBucket() {
        SenderThrottle t = new SenderThrottle(5.0);
        long t0 = 1_000_000_000L;
        int granted = 0;
        for (int i = 0; i < 20; i++) {
            if (t.tryAcquire("118", 2, t0)) {
                granted++;
            }
        }
        assertThat(granted).as("sức chứa 2 × 5").isEqualTo(10);
        assertThat(t.tryAcquire("118", 2, t0 + 400_000_000L)).as("0,4s mới nạp 0,8 lượt").isFalse();
        assertThat(t.tryAcquire("118", 2, t0 + 1_000_000_000L)).as("sau 1s nạp đủ 1 lượt").isTrue();
        assertThat(t.tryAcquire("209", 2, t0)).as("đầu số khác có bucket riêng").isTrue();
    }

    @Test
    @DisplayName("ngày giờ Lào: 00:00 hôm nay / hôm sau theo Asia/Vientiane (UTC+7)")
    void laosDay() {
        Instant t = Instant.parse("2026-11-16T18:30:00Z"); // 01:30 ngày 17/11 giờ Lào
        assertThat(Times.laosDayStart(t)).isEqualTo(Instant.parse("2026-11-16T17:00:00Z"));
        assertThat(Times.nextLaosDayStart(t)).isEqualTo(Instant.parse("2026-11-17T17:00:00Z"));
    }
}
