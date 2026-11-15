package com.unitel.laoads.web.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chặn profile dev kết nối DB không phải localhost (vd Oracle dùng chung với BOC). Profile dev chạy
 * {@code DevDataSeeder} (tài khoản demo mật khẩu yếu) và {@code flyway.repair()} — không được để chúng chạm DB thật.
 * Dev mặc định dùng Oracle XE local ({@code docker-compose.dev.yml}). Cố ý trỏ DB khác:
 * {@code LAOADS_DEV_ALLOW_REMOTE_DB=true}. Không mở connection (BE vẫn phải start được khi DB down).
 */
@Component
@Profile("dev")
@Slf4j
public class DevDatabaseGuard {

    static final String ALLOW_REMOTE_KEY = "laoads.dev.allow-remote-db";
    static final List<String> URL_KEYS = List.of("spring.datasource.url", "laoads.read-only-datasource.jdbc-url");

    private static final Pattern DESCRIPTOR_HOST = Pattern.compile("(?i)\\(\\s*HOST\\s*=\\s*([^)\\s]+)\\s*\\)");
    private static final Pattern EZCONNECT_PROTOCOL = Pattern.compile("(?i)^tcps?://");

    private final Environment env;

    public DevDatabaseGuard(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void check() {
        List<String> remote = new ArrayList<>();
        for (String key : URL_KEYS) {
            String url = env.getProperty(key);
            if (url != null && !url.isBlank() && !isLocal(url)) {
                remote.add(key + "=" + url);
            }
        }
        if (remote.isEmpty()) {
            log.info("[DEV] DB local: {}", env.getProperty(URL_KEYS.get(0)));
            return;
        }
        if (env.getProperty(ALLOW_REMOTE_KEY, Boolean.class, false)) {
            log.warn("[DEV] LAOADS_DEV_ALLOW_REMOTE_DB=true — profile dev đang trỏ DB KHÔNG phải local: {}. "
                    + "DevDataSeeder + flyway.repair() sẽ chạy trên DB này.", remote);
            return;
        }
        throw new IllegalStateException("Profile dev chỉ được dùng DB local (docker-compose.dev.yml) nhưng đang trỏ "
                + remote + ". Bỏ LAOADS_DB_URL để dùng DB local, hoặc đặt LAOADS_DEV_ALLOW_REMOTE_DB=true nếu thật sự cố ý.");
    }

    /** true khi mọi host trong JDBC URL đều là loopback. TNS alias / URL không đọc được host → false (an toàn). */
    static boolean isLocal(String jdbcUrl) {
        List<String> hosts = hosts(jdbcUrl);
        return !hosts.isEmpty() && hosts.stream().allMatch(DevDatabaseGuard::isLoopback);
    }

    static List<String> hosts(String jdbcUrl) {
        int at = jdbcUrl.lastIndexOf('@');
        if (at < 0) {
            return List.of();
        }
        String rest = jdbcUrl.substring(at + 1).trim();
        if (rest.startsWith("(")) {
            List<String> hosts = new ArrayList<>();
            Matcher m = DESCRIPTOR_HOST.matcher(rest);
            while (m.find()) {
                hosts.add(m.group(1));
            }
            return hosts;
        }
        if (rest.startsWith("//")) {
            rest = rest.substring(2);
        }
        rest = EZCONNECT_PROTOCOL.matcher(rest).replaceFirst("");
        if (rest.startsWith("[")) {
            int end = rest.indexOf(']');
            return end > 1 ? List.of(rest.substring(1, end)) : List.of();
        }
        int cut = firstIndexOf(rest, ":/?");
        if (cut <= 0) {
            return List.of();
        }
        return List.of(rest.substring(0, cut));
    }

    private static boolean isLoopback(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("localhost") || h.startsWith("127.") || h.equals("::1") || h.equals("0:0:0:0:0:0:0:1");
    }

    private static int firstIndexOf(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }
}
