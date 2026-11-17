package com.unitel.laoads.domain.campaign;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tham số cá nhân hoá trong nội dung / tiêu đề tin: {@code {{key}}} hoặc {@code {{key|giá trị mặc định}}}.
 * Danh mục là DANH SÁCH TRẮNG: engine thay giá trị theo từng thuê bao lúc gửi; gửi duyệt chiến dịch có tham số
 * ngoài danh mục → 400. Thuê bao không có dữ liệu → dùng giá trị mặc định (nếu có), không thì chuỗi rỗng.
 */
public final class MessageParams {

    /** TARGET: lấy từ chính thuê bao nhận tin; SUBSCRIBER: lấy từ cột {@code column} của LADS_SUBSCRIBER (dữ liệu BI). */
    public enum Source { TARGET, SUBSCRIBER }

    /** {@code maxLength}: độ dài tối đa của giá trị, FE dùng để ước tính độ dài tin sau khi thay. */
    public record Param(String key, Source source, String column, int maxLength, String example) {
    }

    public static final List<Param> PARAMS = List.of(
            new Param("isdn", Source.TARGET, null, 13, "8562055512345"),
            new Param("package", Source.SUBSCRIBER, "REAL_PACKAGE_DATA", 16, "M50"));

    /** {@code {{ key }}} hoặc {@code {{ key | mặc định }}}; key không phân biệt hoa thường. */
    static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*(?:\\|([^{}]*))?}}");

    private static final Map<String, Param> BY_KEY =
            PARAMS.stream().collect(Collectors.toUnmodifiableMap(Param::key, Function.identity()));

    private MessageParams() {
    }

    public static Optional<Param> find(String key) {
        return Optional.ofNullable(key == null ? null : BY_KEY.get(key.toLowerCase(Locale.ROOT)));
    }

    /** Tham số ngoài danh mục, giữ nguyên cách viết của người dùng (vd {@code {{name}}}), theo thứ tự xuất hiện. */
    public static List<String> unknownTokens(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            if (find(m.group(1)).isEmpty()) {
                out.add(m.group());
            }
        }
        return new ArrayList<>(out);
    }

    /** Có tham số nào cần đọc LADS_SUBSCRIBER không (để engine chỉ truy vấn khi cần). */
    public static boolean needsSubscriber(String... texts) {
        for (String text : texts) {
            if (text == null || text.isEmpty()) {
                continue;
            }
            Matcher m = TOKEN.matcher(text);
            while (m.find()) {
                if (find(m.group(1)).map(p -> p.source() == Source.SUBSCRIBER).orElse(false)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Các cột LADS_SUBSCRIBER mà danh mục dùng (tên cột lấy từ danh mục, không từ dữ liệu người dùng). */
    public static List<Param> subscriberParams() {
        return PARAMS.stream().filter(p -> p.source() == Source.SUBSCRIBER).toList();
    }

    /**
     * Thay tham số bằng giá trị của thuê bao.
     *
     * @param subscriber giá trị theo key tham số (từ LADS_SUBSCRIBER); null = thuê bao không có dữ liệu
     */
    public static String render(String text, String isdn, Map<String, String> subscriber) {
        if (text == null || text.indexOf("{{") < 0) {
            return text;
        }
        Matcher m = TOKEN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Param p = find(m.group(1)).orElse(null);
            String value = null;
            if (p != null) {
                value = p.source() == Source.TARGET ? isdn : subscriber == null ? null : subscriber.get(p.key());
            }
            if (value == null || value.isBlank()) {
                value = m.group(2) == null ? "" : m.group(2).trim();
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
