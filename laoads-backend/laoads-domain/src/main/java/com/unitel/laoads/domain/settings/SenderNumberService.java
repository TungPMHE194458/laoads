package com.unitel.laoads.domain.settings;

import com.unitel.laoads.domain.campaign.CampaignEnums.Channel;
import com.unitel.laoads.domain.campaign.CampaignService;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.SecretCipher;
import com.unitel.laoads.domain.common.Texts;
import com.unitel.laoads.domain.common.Times;
import com.unitel.laoads.domain.engine.Utc;
import com.unitel.laoads.domain.workspace.Workspace;
import com.unitel.laoads.domain.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Đầu số gửi tin: CRUD + cấu hình riêng (kênh, kết nối gateway, phạm vi workspace, chính sách gửi).
 * Ghi: SUPER_ADMIN (kiểm tra ở controller). Mật khẩu gateway lưu mã hoá, không bao giờ trả ra ngoài.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SenderNumberService {

    /** Số mã chiến dịch tối đa nêu trong thông báo SENDER_IN_USE. */
    static final int IN_USE_PREVIEW = 5;
    static final Pattern SENDER_CODE = Pattern.compile("^[A-Za-z0-9_]{2,32}$");
    private static final Pattern HH_MM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9.\\-:\\[\\]]{1,255}$");

    // ───── Đầu vào (controller map từ request) ─────

    /** protocol: NONE | SMPP | HTTP. password: null/rỗng = giữ; khác rỗng = thay; clearPassword = xoá. */
    public record GatewayInput(String protocol, String host, Integer port, String systemType, String url,
                               String username, String password, boolean clearPassword, Integer tps) {

        public static final GatewayInput NONE = new GatewayInput("NONE", null, null, null, null, null, null, false, null);
    }

    public record PolicyInput(Integer retryMaxAttempts, Integer retryIntervalMinutes, Boolean silentHoursEnabled,
                              String silentFrom, String silentTo, Long dailyLimit) {

        public static final PolicyInput INHERIT = new PolicyInput(null, null, null, null, null, null);
    }

    public record SenderInput(String code, SenderKind kind, String description, boolean enabled, List<String> channels,
                              boolean allWorkspaces, List<Long> workspaceIds, GatewayInput gateway, PolicyInput policy) {
    }

    public record TestOutcome(ConnectionTestCode code, String detail, Long latencyMs, Instant testedAt) {

        public boolean ok() {
            return code == ConnectionTestCode.OK;
        }
    }

    private final SenderNumberRepository repo;
    private final WorkspaceRepository workspaceRepo;
    private final CampaignService campaignService;
    private final SecretCipher cipher;
    private final ConnectionProbe probe;
    private final JdbcTemplate jdbc;

    // ───────────── Đọc ─────────────

    @Transactional(readOnly = true)
    public List<SenderNumber> list() {
        return repo.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public SenderNumber get(long id) {
        return repo.findById(id).orElseThrow(() -> notFound(id));
    }

    /** Đầu số workspace được dùng cho kênh (null = mọi kênh): đang bật + trong phạm vi + hỗ trợ kênh. */
    @Transactional(readOnly = true)
    public List<SenderNumber> available(long workspaceId, Channel channel) {
        return repo.findAllByOrderByIdAsc().stream()
                .filter(SenderNumber::isEnabled)
                .filter(s -> s.allowedFor(workspaceId))
                .filter(s -> channel == null || s.supports(channel))
                .toList();
    }

    /** Số tin đã gửi trong ngày (giờ Lào) theo mã đầu số. */
    public Map<String, Long> sentToday() {
        Map<String, Long> out = new HashMap<>();
        jdbc.query("SELECT SENDER_CODE, COUNT(*) N FROM LADS_CAMPAIGN_MESSAGE WHERE SENDER_CODE IS NOT NULL AND SENT_AT >= ? "
                        + "GROUP BY SENDER_CODE",
                rs -> {
                    out.put(rs.getString("SENDER_CODE"), rs.getLong("N"));
                }, Utc.ts(Times.laosDayStart(Instant.now())));
        return out;
    }

    // ───────────── Ghi ─────────────

    @Transactional
    public SenderNumber create(SenderInput in, String actor) {
        SenderNumber s = new SenderNumber();
        apply(s, in, true);
        s.setCreatedAt(Instant.now());
        s.setCreatedBy(actor);
        return repo.save(s);
    }

    @Transactional
    public SenderNumber update(long id, SenderInput in, String actor) {
        SenderNumber s = get(id);
        apply(s, in, false);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(actor);
        return s;
    }

    @Transactional
    public SenderNumber setEnabled(long id, boolean enabled, String actor) {
        SenderNumber s = get(id);
        s.setEnabled(enabled);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(actor);
        return s;
    }

    /**
     * Xoá đầu số. Còn chiến dịch chưa kết thúc / huỷ dùng đầu số → 409 SENDER_IN_USE (nêu mã chiến dịch);
     * khi đó tắt đầu số thay vì xoá. Chiến dịch đã kết thúc vẫn giữ mã đầu số trong lịch sử.
     */
    @Transactional
    public SenderNumber delete(long id, String actor) {
        SenderNumber s = get(id);
        List<String> used = campaignService.codesUsingSender(s.getCode());
        if (!used.isEmpty()) {
            String preview = String.join(", ", used.subList(0, Math.min(IN_USE_PREVIEW, used.size())))
                    + (used.size() > IN_USE_PREVIEW ? " (+" + (used.size() - IN_USE_PREVIEW) + ")" : "");
            throw new LaoadsException(ErrorCode.SENDER_IN_USE, "Đầu số đang được dùng ở chiến dịch " + preview, preview);
        }
        repo.delete(s);
        log.info("[SENDER] {} xoá đầu số {}", actor, s.getCode());
        return s;
    }

    /**
     * Kiểm tra kết nối theo cấu hình ĐÃ LƯU, lưu kết quả vào đầu số. Không chạy trong transaction vì gọi ra mạng
     * (tối đa vài giây); đọc và ghi là hai lần gọi repository riêng.
     */
    public TestOutcome testConnection(long id, String actor) {
        SenderNumber s = repo.findById(id).orElseThrow(() -> notFound(id));
        if (s.getGwProtocol() == null) {
            throw LaoadsException.validation(List.of(new FieldViolation("gateway.protocol",
                    "error.validation.gatewayNotConfigured", "Đầu số chưa cấu hình kết nối gateway")));
        }
        ConnectionProbe.Result r;
        String password;
        try {
            password = cipher.decrypt(s.getGwPasswordEnc());
            r = probe.probe(new ConnectionProbe.Target(s.getGwProtocol(), s.getGwHost(), s.getGwPort(),
                    s.getGwSystemType(), s.getGwUrl(), s.getGwUsername(), password));
        } catch (IllegalStateException e) {
            r = new ConnectionProbe.Result(ConnectionTestCode.ERROR,
                    "Không giải mã được mật khẩu đã lưu — nhập lại mật khẩu", null);
        }
        Instant now = Instant.now();
        s.setGwTestAt(now);
        s.setGwTestOk(r.ok());
        s.setGwTestCode(r.code());
        s.setGwTestDetail(truncate(r.detail(), 500));
        s.setGwTestLatencyMs(r.latencyMs());
        repo.save(s);
        log.info("[SENDER] {} kiểm tra kết nối đầu số {}: {} {}", actor, s.getCode(), r.code(), r.detail());
        return new TestOutcome(r.code(), r.detail(), r.latencyMs(), now);
    }

    // ───────────── Kiểm tra + áp dụng đầu vào ─────────────

    private void apply(SenderNumber s, SenderInput in, boolean creating) {
        List<FieldViolation> errors = new ArrayList<>();

        String code = in.code() == null ? "" : in.code().trim().toUpperCase(Locale.ROOT);
        if (!SENDER_CODE.matcher(code).matches()) {
            errors.add(invalid("code"));
        } else if (creating ? repo.existsByCode(code) : repo.existsByCodeAndIdNot(code, s.getId())) {
            throw new LaoadsException(ErrorCode.SENDER_CODE_DUPLICATE, "Đầu số đã tồn tại");
        }
        if (in.kind() == null) {
            errors.add(invalid("kind"));
        }
        String description = Texts.trimToNull(in.description());
        if (description != null && description.length() > 255) {
            errors.add(maxLength("description", 255));
        }

        Set<Channel> channels = parseChannels(in.channels(), errors);
        if (in.kind() == SenderKind.BRANDNAME && channels.contains(Channel.USSD)) {
            errors.add(new FieldViolation("channels", "error.validation.brandnameSmsOnly",
                    "Brandname chỉ dùng được cho SMS"));
        }
        if (in.kind() == SenderKind.SHORTCODE && channels.contains(Channel.WHATSAPP)) {
            errors.add(new FieldViolation("channels", "error.validation.whatsappBrandOnly",
                    "WhatsApp chỉ dùng brand (tài khoản WhatsApp chính thức), không dùng đầu số ngắn"));
        }

        Set<Long> workspaceIds = new LinkedHashSet<>(in.workspaceIds() == null ? List.of() : in.workspaceIds());
        workspaceIds.remove(null);
        if (!in.allWorkspaces()) {
            if (workspaceIds.isEmpty()) {
                errors.add(new FieldViolation("workspaceIds", "error.validation.workspacesRequired",
                        "Chọn ít nhất một workspace"));
            } else {
                Set<Long> found = workspaceRepo.findAllById(workspaceIds).stream().map(Workspace::getId)
                        .collect(Collectors.toSet());
                if (!found.containsAll(workspaceIds)) {
                    errors.add(invalid("workspaceIds"));
                }
            }
        }

        GatewayInput gw = in.gateway() == null ? GatewayInput.NONE : in.gateway();
        GatewayProtocol protocol = parseProtocol(gw.protocol(), errors);
        String host = Texts.trimToNull(gw.host());
        String url = Texts.trimToNull(gw.url());
        String username = Texts.trimToNull(gw.username());
        String systemType = Texts.trimToNull(gw.systemType());
        String newPassword = gw.password() == null || gw.password().isEmpty() ? null : gw.password();
        if (protocol == GatewayProtocol.SMPP) {
            if (host == null) {
                errors.add(required("gateway.host"));
            } else if (!HOST.matcher(host).matches()) {
                errors.add(invalid("gateway.host"));
            }
            if (gw.port() == null) {
                errors.add(required("gateway.port"));
            } else if (gw.port() < 1 || gw.port() > 65535) {
                errors.add(range("gateway.port", 1, 65535));
            }
            if (username == null) {
                errors.add(required("gateway.username"));
            } else if (username.length() > 15) {
                errors.add(maxLength("gateway.username", 15));
            }
            if (systemType != null && systemType.length() > 12) {
                errors.add(maxLength("gateway.systemType", 12));
            }
            if (newPassword != null && newPassword.length() > 8) {
                errors.add(maxLength("gateway.password", 8));
            }
            // SMPP chỉ nhận C-Octet String ASCII
            if (!asciiOrNull(username)) {
                errors.add(invalid("gateway.username"));
            }
            if (!asciiOrNull(systemType)) {
                errors.add(invalid("gateway.systemType"));
            }
            if (!asciiOrNull(newPassword)) {
                errors.add(invalid("gateway.password"));
            }
        } else if (protocol == GatewayProtocol.HTTP) {
            if (url == null) {
                errors.add(required("gateway.url"));
            } else if (url.length() > 1000 || !validHttpUrl(url)) {
                errors.add(new FieldViolation("gateway.url", "error.validation.urlInvalid",
                        "URL phải bắt đầu bằng http:// hoặc https://"));
            }
            if (username != null && username.length() > 128) {
                errors.add(maxLength("gateway.username", 128));
            }
            if (newPassword != null && newPassword.length() > 256) {
                errors.add(maxLength("gateway.password", 256));
            }
        }
        if (protocol != null && gw.tps() != null && (gw.tps() < 1 || gw.tps() > 10_000)) {
            errors.add(range("gateway.tps", 1, 10_000));
        }

        PolicyInput p = in.policy() == null ? PolicyInput.INHERIT : in.policy();
        if (p.retryMaxAttempts() != null && (p.retryMaxAttempts() < 0 || p.retryMaxAttempts() > 10)) {
            errors.add(range("policy.retryMaxAttempts", 0, 10));
        }
        if (p.retryIntervalMinutes() != null && (p.retryIntervalMinutes() < 1 || p.retryIntervalMinutes() > 1440)) {
            errors.add(range("policy.retryIntervalMinutes", 1, 1440));
        }
        if (Boolean.TRUE.equals(p.silentHoursEnabled())) {
            if (p.silentFrom() == null || !HH_MM.matcher(p.silentFrom()).matches()) {
                errors.add(timeFormat("policy.silentFrom"));
            }
            if (p.silentTo() == null || !HH_MM.matcher(p.silentTo()).matches()) {
                errors.add(timeFormat("policy.silentTo"));
            }
        }
        if (p.dailyLimit() != null && (p.dailyLimit() < 1 || p.dailyLimit() > 100_000_000L)) {
            errors.add(range("policy.dailyLimit", 1, 100_000_000));
        }
        if (!errors.isEmpty()) {
            throw LaoadsException.validation(errors);
        }

        s.setCode(code);
        s.setKind(in.kind());
        s.setDescription(description);
        s.setEnabled(in.enabled());
        s.setChannelSet(channels);
        s.setAllWorkspaces(in.allWorkspaces());
        if (s.getWorkspaceIds() == null) {
            s.setWorkspaceIds(new HashSet<>());
        }
        s.getWorkspaceIds().clear();
        if (!in.allWorkspaces()) {
            s.getWorkspaceIds().addAll(workspaceIds);
        }
        applyGateway(s, protocol, host, url, username, systemType, newPassword, gw);
        s.setRetryMax(p.retryMaxAttempts());
        s.setRetryIntervalMin(p.retryIntervalMinutes());
        s.setSilentEnabled(p.silentHoursEnabled());
        s.setSilentFrom(Boolean.TRUE.equals(p.silentHoursEnabled()) ? p.silentFrom() : null);
        s.setSilentTo(Boolean.TRUE.equals(p.silentHoursEnabled()) ? p.silentTo() : null);
        s.setDailyLimit(p.dailyLimit());
    }

    private void applyGateway(SenderNumber s, GatewayProtocol protocol, String host, String url, String username,
                              String systemType, String newPassword, GatewayInput gw) {
        boolean endpointChanged = protocol != s.getGwProtocol()
                || !Objects.equals(protocol == GatewayProtocol.SMPP ? host : null, s.getGwHost())
                || !Objects.equals(protocol == GatewayProtocol.SMPP ? gw.port() : null, s.getGwPort())
                || !Objects.equals(protocol == GatewayProtocol.HTTP ? url : null, s.getGwUrl())
                || !Objects.equals(protocol == null ? null : username, s.getGwUsername())
                || newPassword != null || gw.clearPassword();
        if (protocol == null) {
            s.setGwProtocol(null);
            s.setGwHost(null);
            s.setGwPort(null);
            s.setGwSystemType(null);
            s.setGwUrl(null);
            s.setGwUsername(null);
            s.setGwPasswordEnc(null);
            s.setGwTps(null);
        } else {
            s.setGwProtocol(protocol);
            s.setGwHost(protocol == GatewayProtocol.SMPP ? host : null);
            s.setGwPort(protocol == GatewayProtocol.SMPP ? gw.port() : null);
            s.setGwSystemType(protocol == GatewayProtocol.SMPP ? systemType : null);
            s.setGwUrl(protocol == GatewayProtocol.HTTP ? url : null);
            s.setGwUsername(username);
            if (gw.clearPassword()) {
                s.setGwPasswordEnc(null);
            } else if (newPassword != null) {
                s.setGwPasswordEnc(cipher.encrypt(newPassword));
            }
            s.setGwTps(gw.tps());
        }
        if (endpointChanged) {
            // kết quả kiểm tra cũ không còn đúng với cấu hình mới
            s.setGwTestAt(null);
            s.setGwTestOk(null);
            s.setGwTestCode(null);
            s.setGwTestDetail(null);
            s.setGwTestLatencyMs(null);
        }
    }

    private static Set<Channel> parseChannels(Collection<String> raw, List<FieldViolation> errors) {
        Set<Channel> out = EnumSet.noneOf(Channel.class);
        boolean bad = false;
        for (String v : raw == null ? List.<String>of() : raw) {
            try {
                Channel c = Channel.valueOf(v == null ? "" : v.trim().toUpperCase(Locale.ROOT));
                if (SenderNumber.SUPPORTED_CHANNELS.contains(c)) {
                    out.add(c);
                } else {
                    bad = true;
                }
            } catch (IllegalArgumentException e) {
                bad = true;
            }
        }
        if (bad) {
            errors.add(invalid("channels"));
        } else if (out.isEmpty()) {
            errors.add(new FieldViolation("channels", "error.validation.channelsRequired", "Chọn ít nhất một kênh"));
        }
        return out;
    }

    private static GatewayProtocol parseProtocol(String raw, List<FieldViolation> errors) {
        String v = raw == null ? "NONE" : raw.trim().toUpperCase(Locale.ROOT);
        if (v.isEmpty() || "NONE".equals(v)) {
            return null;
        }
        try {
            return GatewayProtocol.valueOf(v);
        } catch (IllegalArgumentException e) {
            errors.add(invalid("gateway.protocol"));
            return null;
        }
    }

    static boolean validHttpUrl(String url) {
        try {
            URI u = URI.create(url);
            return ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme()))
                    && u.getHost() != null && !u.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean asciiOrNull(String s) {
        return s == null || s.chars().allMatch(c -> c >= 0x20 && c < 0x7F);
    }

    private static FieldViolation invalid(String field) {
        return new FieldViolation(field, "error.validation.invalidValue", "Giá trị không hợp lệ");
    }

    private static FieldViolation required(String field) {
        return new FieldViolation(field, "error.validation.required", "Không được để trống");
    }

    private static FieldViolation maxLength(String field, int max) {
        return new FieldViolation(field, "error.validation.maxLength", "Tối đa " + max + " ký tự", max);
    }

    private static FieldViolation range(String field, long min, long max) {
        return new FieldViolation(field, "error.validation.range", "Giá trị phải từ " + min + " đến " + max, min, max);
    }

    private static FieldViolation timeFormat(String field) {
        return new FieldViolation(field, "error.validation.timeFormat", "Giờ phải có dạng HH:mm");
    }

    private static LaoadsException notFound(long id) {
        return LaoadsException.notFound("error.notFound.senderNumber", "Không tìm thấy đầu số", id);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
