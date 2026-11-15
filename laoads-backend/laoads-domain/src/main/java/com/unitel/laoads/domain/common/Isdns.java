package com.unitel.laoads.domain.common;

import java.util.Optional;

/**
 * Chuẩn hoá số thuê bao Lào về dạng quốc tế không dấu "+": 856 + số quốc gia.
 * Nhận: "+856 20 5551 2345", "00856...", "020 5551 2345", "2055512345". Trả rỗng nếu không hợp lệ.
 */
public final class Isdns {

    private Isdns() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String d = raw.replaceAll("[\\s\\-().+]", "");
        if (d.endsWith(".0")) {
            d = d.substring(0, d.length() - 2); // ô Excel kiểu số
        }
        if (!d.chars().allMatch(Character::isDigit) || d.isEmpty()) {
            return Optional.empty();
        }
        if (d.startsWith("00856")) {
            d = d.substring(2);
        } else if (d.startsWith("0")) {
            d = "856" + d.substring(1);
        } else if (!d.startsWith("856")) {
            d = "856" + d;
        }
        // 856 + 8..10 chữ số (di động 20xxxxxxxx / 30xxxxxxx, cố định 21xxxxxx…)
        return d.length() >= 11 && d.length() <= 13 ? Optional.of(d) : Optional.empty();
    }

    /** Che giữa số khi hiển thị: 85620555xxxxx. */
    public static String mask(String isdn) {
        if (isdn == null || isdn.length() < 8) {
            return isdn;
        }
        return isdn.substring(0, isdn.length() - 5) + "xxxxx";
    }
}
