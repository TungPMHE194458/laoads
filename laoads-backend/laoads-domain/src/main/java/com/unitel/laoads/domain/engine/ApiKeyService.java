package com.unitel.laoads.domain.engine;

import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * API key của workspace cho API sự kiện (quyền SECRET_KEY_MANAGE). Chỉ lưu SHA-256 + 4 ký tự cuối để hiển thị;
 * key đầy đủ chỉ trả về đúng 1 lần khi tạo / xoay vòng.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    public record KeyInfo(boolean configured, String hint, Instant rotatedAt) {
    }

    static final String PREFIX = "lads_";
    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public KeyInfo info(long workspaceId) {
        return jdbc.query("SELECT API_KEY_HINT, API_KEY_ROTATED_AT FROM LADS_WORKSPACE WHERE ID = ?",
                rs -> rs.next() ? new KeyInfo(rs.getString(1) != null, rs.getString(1), Utc.instant(rs, "API_KEY_ROTATED_AT"))
                        : new KeyInfo(false, null, null), workspaceId);
    }

    /** Tạo mới / xoay vòng key; key cũ hết hiệu lực ngay. Trả về key đầy đủ (chỉ lần này). */
    @Transactional
    public String rotate(long workspaceId, String actor) {
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        String key = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        int n = jdbc.update("UPDATE LADS_WORKSPACE SET API_KEY_HASH = ?, API_KEY_HINT = ?, API_KEY_ROTATED_AT = ? WHERE ID = ?",
                hash(key), "…" + key.substring(key.length() - 4), Utc.ts(Instant.now()), workspaceId);
        if (n == 0) {
            throw LaoadsException.notFound("error.notFound.workspace", "Không tìm thấy workspace", workspaceId);
        }
        log.info("[API-KEY] {} xoay vòng API key của workspace #{}", actor, workspaceId);
        return key;
    }

    @Transactional
    public void revoke(long workspaceId, String actor) {
        jdbc.update("UPDATE LADS_WORKSPACE SET API_KEY_HASH = NULL, API_KEY_HINT = NULL, API_KEY_ROTATED_AT = ? WHERE ID = ?",
                Utc.ts(Instant.now()), workspaceId);
        log.info("[API-KEY] {} thu hồi API key của workspace #{}", actor, workspaceId);
    }

    /** workspaceId của key (workspace phải ACTIVE), ngược lại 401 API_KEY_INVALID. */
    @Transactional(readOnly = true)
    public long resolve(String key) {
        if (key == null || !key.startsWith(PREFIX) || key.length() > 100) {
            throw invalid();
        }
        Long ws = jdbc.query("SELECT ID FROM LADS_WORKSPACE WHERE API_KEY_HASH = ? AND STATUS = 'ACTIVE'",
                rs -> rs.next() ? rs.getLong(1) : null, hash(key));
        if (ws == null) {
            throw invalid();
        }
        return ws;
    }

    static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LaoadsException invalid() {
        return new LaoadsException(ErrorCode.API_KEY_INVALID, "API key không hợp lệ hoặc đã bị thu hồi");
    }
}
