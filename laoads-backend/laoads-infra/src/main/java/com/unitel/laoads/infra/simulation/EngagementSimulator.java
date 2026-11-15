package com.unitel.laoads.infra.simulation;

import com.unitel.laoads.domain.engine.Utc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * GIẢ LẬP phản hồi từ nhà cung cấp / người nhận: báo phát (DLR), mở tin, click link tracking. Tất định theo
 * ID tin (ORA_HASH) nên mỗi tin chỉ "tung xúc xắc" một lần. Gateway thật sẽ cập nhật qua callback DLR và
 * endpoint tracking thật thay cho lớp này ({@code laoads.engine.simulation.engagement=false}).
 */
@Component
@ConditionalOnProperty(name = "laoads.engine.simulation.engagement", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class EngagementSimulator {

    private final JdbcTemplate jdbc;

    @Transactional
    public void simulate() {
        Instant now = Instant.now();
        var t30s = Utc.ts(now.minus(Duration.ofSeconds(30)));
        int delivered = jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET STATUS = 'DELIVERED', DELIVERED_AT = SENT_AT + "
                + "INTERVAL '5' SECOND WHERE STATUS = 'SENT' AND SENT_AT < ? AND MOD(ORA_HASH(ID), 100) < CASE CHANNEL "
                + "WHEN 'WHATSAPP' THEN 83 WHEN 'EMAIL' THEN 91 WHEN 'USSD' THEN 88 ELSE 96 END", t30s);
        int opened = jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET OPENED_AT = DELIVERED_AT + INTERVAL '1' MINUTE "
                + "WHERE STATUS = 'DELIVERED' AND OPENED_AT IS NULL AND CHANNEL IN ('WHATSAPP', 'EMAIL', 'USSD') "
                + "AND DELIVERED_AT < ? AND MOD(ORA_HASH(ID || 'o'), 100) < CASE CHANNEL WHEN 'WHATSAPP' THEN 64 "
                + "WHEN 'EMAIL' THEN 27 ELSE 41 END", t30s);
        int clicked = jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE m SET CLICKED_AT = NVL(OPENED_AT, DELIVERED_AT) + "
                + "INTERVAL '2' MINUTE WHERE m.CLICKED_AT IS NULL AND m.STATUS = 'DELIVERED' AND NVL(m.OPENED_AT, "
                + "m.DELIVERED_AT) < ? AND (m.OPENED_AT IS NOT NULL OR m.CHANNEL = 'SMS') "
                + "AND EXISTS (SELECT 1 FROM LADS_TRACKING_LINK l WHERE l.MESSAGE_ID = m.ID) "
                + "AND MOD(ORA_HASH(m.ID || 'c'), 100) < CASE m.CHANNEL WHEN 'SMS' THEN 6 ELSE 30 END", t30s);
        if (clicked > 0) {
            jdbc.update("UPDATE LADS_TRACKING_LINK l SET CLICK_COUNT = 1, FIRST_CLICK = (SELECT m.CLICKED_AT FROM "
                    + "LADS_CAMPAIGN_MESSAGE m WHERE m.ID = l.MESSAGE_ID) WHERE l.CLICK_COUNT = 0 AND EXISTS (SELECT 1 "
                    + "FROM LADS_CAMPAIGN_MESSAGE m WHERE m.ID = l.MESSAGE_ID AND m.CLICKED_AT IS NOT NULL)");
        }
        if (delivered + opened + clicked > 0) {
            log.debug("[SIM] báo phát {}, mở {}, click {}", delivered, opened, clicked);
        }
    }
}
