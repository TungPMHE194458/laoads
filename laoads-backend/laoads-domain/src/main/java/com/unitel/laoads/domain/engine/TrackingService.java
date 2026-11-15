package com.unitel.laoads.domain.engine;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** Link tracking (SRS 3.2.3.6): ghi nhận click rồi trả URL gốc để chuyển hướng. */
@Service
@RequiredArgsConstructor
public class TrackingService {

    private final JdbcTemplate jdbc;

    @Transactional
    public Optional<String> click(String token) {
        if (token == null || !token.matches("[A-Za-z0-9]{6,32}")) {
            return Optional.empty();
        }
        var rows = jdbc.query("SELECT TARGET_URL, MESSAGE_ID FROM LADS_TRACKING_LINK WHERE TOKEN = ?",
                (rs, i) -> new Object[]{rs.getString(1), rs.getLong(2)}, token);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        String url = (String) rows.get(0)[0];
        long messageId = (Long) rows.get(0)[1];
        var now = Utc.ts(Instant.now());
        jdbc.update("UPDATE LADS_TRACKING_LINK SET CLICK_COUNT = CLICK_COUNT + 1, FIRST_CLICK = NVL(FIRST_CLICK, ?) "
                + "WHERE TOKEN = ?", now, token);
        // click ⇒ đã mở tin
        jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET CLICKED_AT = NVL(CLICKED_AT, ?), OPENED_AT = NVL(OPENED_AT, ?) "
                + "WHERE ID = ?", now, now, messageId);
        return Optional.of(url);
    }
}
