package com.unitel.laoads.domain.common;

import java.util.Locale;

/** Helper chuẩn hoá chuỗi đầu vào (username, code, LIKE pattern). */
public final class Texts {

    /** Ký tự escape dùng trong mọi JPQL {@code LIKE ... ESCAPE '!'}. */
    public static final char LIKE_ESCAPE = '!';

    private Texts() {
    }

    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Username lưu chữ thường — login/VSA so khớp không phân biệt hoa thường. */
    public static String normalizeUsername(String s) {
        String t = trimToNull(s);
        return t == null ? null : t.toLowerCase(Locale.ROOT);
    }

    /** Mã workspace lưu chữ HOA. */
    public static String normalizeCode(String s) {
        String t = trimToNull(s);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }

    /**
     * Chuỗi tìm kiếm → pattern {@code %q%} chữ thường đã escape {@code ! % _};
     * null nếu q rỗng (JPQL dùng {@code :q IS NULL OR ...}).
     */
    public static String likePattern(String q) {
        String t = trimToNull(q);
        if (t == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("%");
        for (char c : t.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == LIKE_ESCAPE || c == '%' || c == '_') {
                sb.append(LIKE_ESCAPE);
            }
            sb.append(c);
        }
        return sb.append('%').toString();
    }
}
