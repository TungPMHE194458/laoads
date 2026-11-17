package com.unitel.laoads.domain.settings;

import com.unitel.laoads.domain.campaign.CampaignEnums.Channel;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LADS_SENDER_NUMBER — đầu số / brandname gửi tin (yêu cầu nâng cấp 2.0) cùng cấu hình riêng của nó:
 * kênh, kết nối gateway, phạm vi workspace, chính sách gửi (V4). Các trường chính sách NULL = theo chính sách chung.
 */
@Entity
@Table(name = "LADS_SENDER_NUMBER")
@Getter
@Setter
@NoArgsConstructor
public class SenderNumber {

    /** Kênh được phép gắn đầu số. */
    /** SMS / USSD: đầu số; WHATSAPP: brand = tài khoản WhatsApp chính thức ("Brand" của laoads.com), chỉ loại BRANDNAME. */
    public static final Set<Channel> SUPPORTED_CHANNELS = EnumSet.of(Channel.SMS, Channel.USSD, Channel.WHATSAPP);

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lads_sender_number_seq")
    @SequenceGenerator(name = "lads_sender_number_seq", sequenceName = "LADS_SENDER_NUMBER_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CODE", nullable = false, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "KIND", nullable = false, length = 16)
    private SenderKind kind;

    @Column(name = "DESCRIPTION", length = 255)
    private String description;

    @Column(name = "ENABLED", nullable = false)
    private Boolean enabled;

    /** Danh sách kênh, phân tách bằng dấu phẩy (vd "SMS,USSD"). Dùng {@link #channelSet()}. */
    @Column(name = "CHANNELS", nullable = false, length = 32)
    private String channels = "SMS";

    @Column(name = "ALL_WORKSPACES", nullable = false)
    private Boolean allWorkspaces = Boolean.TRUE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "LADS_SENDER_WORKSPACE", joinColumns = @JoinColumn(name = "SENDER_ID"))
    @Column(name = "WORKSPACE_ID", nullable = false)
    private Set<Long> workspaceIds = new HashSet<>();

    // ───── Kết nối gateway (protocol null = chưa cấu hình) ─────

    @Enumerated(EnumType.STRING)
    @Column(name = "GW_PROTOCOL", length = 8)
    private GatewayProtocol gwProtocol;

    @Column(name = "GW_HOST", length = 255)
    private String gwHost;

    @Column(name = "GW_PORT")
    private Integer gwPort;

    @Column(name = "GW_SYSTEM_TYPE", length = 13)
    private String gwSystemType;

    @Column(name = "GW_URL", length = 1000)
    private String gwUrl;

    @Column(name = "GW_USERNAME", length = 128)
    private String gwUsername;

    /** Mật khẩu đã mã hoá bằng {@link com.unitel.laoads.domain.common.SecretCipher}. */
    @Column(name = "GW_PASSWORD_ENC", length = 1000)
    private String gwPasswordEnc;

    @Column(name = "GW_TPS")
    private Integer gwTps;

    @Column(name = "GW_TEST_AT")
    private Instant gwTestAt;

    @Column(name = "GW_TEST_OK")
    private Boolean gwTestOk;

    @Enumerated(EnumType.STRING)
    @Column(name = "GW_TEST_CODE", length = 16)
    private ConnectionTestCode gwTestCode;

    @Column(name = "GW_TEST_DETAIL", length = 500)
    private String gwTestDetail;

    @Column(name = "GW_TEST_LATENCY_MS")
    private Long gwTestLatencyMs;

    // ───── Chính sách gửi riêng (null = theo chính sách chung) ─────

    @Column(name = "RETRY_MAX")
    private Integer retryMax;

    @Column(name = "RETRY_INTERVAL_MIN")
    private Integer retryIntervalMin;

    /** null = theo chung; true = khung giờ riêng; false = đầu số này không chặn giờ. */
    @Column(name = "SILENT_ENABLED")
    private Boolean silentEnabled;

    @Column(name = "SILENT_FROM", length = 5)
    private String silentFrom;

    @Column(name = "SILENT_TO", length = 5)
    private String silentTo;

    @Column(name = "DAILY_LIMIT")
    private Long dailyLimit;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "CREATED_BY", length = 64)
    private String createdBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "UPDATED_BY", length = 64)
    private String updatedBy;

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean isAllWorkspaces() {
        return !Boolean.FALSE.equals(allWorkspaces);
    }

    public Set<Channel> channelSet() {
        if (channels == null || channels.isBlank()) {
            return EnumSet.of(Channel.SMS);
        }
        Set<Channel> out = EnumSet.noneOf(Channel.class);
        Arrays.stream(channels.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .forEach(s -> {
                    try {
                        out.add(Channel.valueOf(s));
                    } catch (IllegalArgumentException ignored) {
                        // giá trị lạ trong DB → bỏ qua
                    }
                });
        return out;
    }

    public void setChannelSet(Set<Channel> set) {
        this.channels = set.stream().sorted().map(Channel::name).collect(Collectors.joining(","));
    }

    public boolean supports(Channel channel) {
        return channelSet().contains(channel);
    }

    /** Workspace có được dùng đầu số này không. */
    public boolean allowedFor(long workspaceId) {
        return isAllWorkspaces() || (workspaceIds != null && workspaceIds.contains(workspaceId));
    }

    /** Đầu số có trường chính sách nào khác chính sách chung không. */
    public boolean hasPolicyOverride() {
        return retryMax != null || retryIntervalMin != null || silentEnabled != null || dailyLimit != null;
    }
}
