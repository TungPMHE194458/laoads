package com.unitel.laoads.domain.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unitel.laoads.domain.campaign.CampaignDefinition;
import com.unitel.laoads.domain.campaign.CampaignEnums.Channel;
import com.unitel.laoads.domain.campaign.CampaignService;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.SecretCipher;
import com.unitel.laoads.domain.settings.SenderNumberService.GatewayInput;
import com.unitel.laoads.domain.settings.SenderNumberService.PolicyInput;
import com.unitel.laoads.domain.settings.SenderNumberService.SenderInput;
import com.unitel.laoads.domain.workspace.Workspace;
import com.unitel.laoads.domain.workspace.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SenderNumberServiceTest {

    @Mock
    SenderNumberRepository repo;
    @Mock
    WorkspaceRepository workspaceRepo;
    @Mock
    CampaignService campaignService;
    @Mock
    ConnectionProbe probe;
    @Mock
    JdbcTemplate jdbc;

    final SecretCipher cipher = new SecretCipher("test-secrets-key-0123456789abcdef", "");
    SenderNumberService service;

    @BeforeEach
    void setUp() {
        service = new SenderNumberService(repo, workspaceRepo, campaignService, cipher, probe, jdbc);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workspaceRepo.findAllById(anyIterable())).thenAnswer(inv -> {
            List<Workspace> out = new java.util.ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                if ((Long) id < 100) {
                    Workspace w = new Workspace();
                    w.setId((Long) id);
                    out.add(w);
                }
            }
            return out;
        });
    }

    private static SenderInput input(String code, SenderKind kind, List<String> channels, GatewayInput gw, PolicyInput p) {
        return new SenderInput(code, kind, "mô tả", true, channels, true, List.of(), gw, p);
    }

    private static GatewayInput smpp(String password) {
        return new GatewayInput("SMPP", "smsc.local", 2775, "CMT", null, "laoads", password, false, 50);
    }

    private static List<String> fields(Throwable e) {
        return ((LaoadsException) e).getErrors().stream().map(FieldViolation::field).toList();
    }

    private static List<String> keys(Throwable e) {
        return ((LaoadsException) e).getErrors().stream().map(FieldViolation::messageKey).toList();
    }

    private static SenderNumber existing(long id, String code) {
        SenderNumber s = new SenderNumber();
        s.setId(id);
        s.setCode(code);
        s.setKind(SenderKind.SHORTCODE);
        s.setEnabled(true);
        s.setCreatedAt(Instant.now());
        return s;
    }

    @Nested
    @DisplayName("Tạo / sửa — cấu hình hợp lệ")
    class Valid {

        @Test
        @DisplayName("SMPP: chuẩn hoá mã, lưu kênh, mã hoá mật khẩu (không lưu bản rõ)")
        void createSmpp() {
            SenderNumber s = service.create(input(" 118x ", SenderKind.SHORTCODE, List.of("sms", "USSD"), smpp("pw123"),
                    new PolicyInput(2, 15, true, "21:30", "07:00", 1000L)), "superadmin");

            assertThat(s.getCode()).isEqualTo("118X");
            assertThat(s.channelSet()).containsExactlyInAnyOrder(Channel.SMS, Channel.USSD);
            assertThat(s.getGwProtocol()).isEqualTo(GatewayProtocol.SMPP);
            assertThat(s.getGwPasswordEnc()).startsWith("v1:").doesNotContain("pw123");
            assertThat(cipher.decrypt(s.getGwPasswordEnc())).isEqualTo("pw123");
            assertThat(s.getRetryMax()).isEqualTo(2);
            assertThat(s.getSilentFrom()).isEqualTo("21:30");
            assertThat(s.getDailyLimit()).isEqualTo(1000L);
            assertThat(s.getCreatedBy()).isEqualTo("superadmin");
        }

        @Test
        @DisplayName("phạm vi giới hạn: lưu đúng danh sách workspace")
        void scopedWorkspaces() {
            SenderInput in = new SenderInput("BRAND", SenderKind.BRANDNAME, null, true, List.of("SMS"), false,
                    List.of(1L, 2L), GatewayInput.NONE, PolicyInput.INHERIT);
            SenderNumber s = service.create(in, "superadmin");
            assertThat(s.isAllWorkspaces()).isFalse();
            assertThat(s.getWorkspaceIds()).containsExactlyInAnyOrder(1L, 2L);
            assertThat(s.allowedFor(1L)).isTrue();
            assertThat(s.allowedFor(3L)).isFalse();
        }

        @Test
        @DisplayName("sửa: bỏ trống mật khẩu = giữ; clearPassword = xoá; đổi endpoint xoá kết quả kiểm tra cũ")
        void passwordSemantics() {
            SenderNumber s = existing(1, "118");
            s.setGwProtocol(GatewayProtocol.SMPP);
            s.setGwHost("smsc.local");
            s.setGwPort(2775);
            s.setGwUsername("laoads");
            s.setGwPasswordEnc(cipher.encrypt("old"));
            s.setGwTestAt(Instant.now());
            s.setGwTestOk(true);
            s.setGwTestCode(ConnectionTestCode.OK);
            when(repo.findById(1L)).thenReturn(Optional.of(s));

            service.update(1, input("118", SenderKind.SHORTCODE, List.of("SMS"), smpp(null), PolicyInput.INHERIT), "a");
            assertThat(cipher.decrypt(s.getGwPasswordEnc())).isEqualTo("old");
            assertThat(s.getGwTestCode()).as("cùng endpoint, giữ kết quả kiểm tra").isEqualTo(ConnectionTestCode.OK);

            service.update(1, input("118", SenderKind.SHORTCODE, List.of("SMS"),
                    new GatewayInput("SMPP", "smsc2.local", 2775, null, null, "laoads", "", false, null),
                    PolicyInput.INHERIT), "a");
            assertThat(s.getGwHost()).isEqualTo("smsc2.local");
            assertThat(s.getGwTestCode()).as("đổi host → kết quả cũ không còn đúng").isNull();
            assertThat(cipher.decrypt(s.getGwPasswordEnc())).isEqualTo("old");

            service.update(1, input("118", SenderKind.SHORTCODE, List.of("SMS"),
                    new GatewayInput("SMPP", "smsc2.local", 2775, null, null, "laoads", null, true, null),
                    PolicyInput.INHERIT), "a");
            assertThat(s.getGwPasswordEnc()).isNull();
        }

        @Test
        @DisplayName("protocol NONE xoá sạch cấu hình kết nối; policy null = theo chung")
        void noneClearsGateway() {
            SenderNumber s = existing(1, "209");
            s.setGwProtocol(GatewayProtocol.HTTP);
            s.setGwUrl("https://gw.local/send");
            s.setGwPasswordEnc(cipher.encrypt("token"));
            s.setGwTps(10);
            s.setRetryMax(5);
            when(repo.findById(1L)).thenReturn(Optional.of(s));

            service.update(1, input("209", SenderKind.SHORTCODE, List.of("SMS"), GatewayInput.NONE, PolicyInput.INHERIT), "a");

            assertThat(s.getGwProtocol()).isNull();
            assertThat(s.getGwUrl()).isNull();
            assertThat(s.getGwPasswordEnc()).isNull();
            assertThat(s.getGwTps()).isNull();
            assertThat(s.hasPolicyOverride()).isFalse();
        }
    }

    @Nested
    @DisplayName("Tạo / sửa — dữ liệu sai")
    class Invalid {

        @Test
        @DisplayName("brandname không dùng cho USSD; phạm vi giới hạn cần workspace")
        void channelAndScope() {
            SenderInput in = new SenderInput("BRAND", SenderKind.BRANDNAME, null, true, List.of("SMS", "USSD"), false,
                    List.of(), GatewayInput.NONE, PolicyInput.INHERIT);
            assertThatThrownBy(() -> service.create(in, "a"))
                    .satisfies(e -> assertThat(keys(e)).contains("error.validation.brandnameSmsOnly",
                            "error.validation.workspacesRequired"));
        }

        @Test
        @DisplayName("kênh rỗng / kênh không hỗ trợ / workspace không tồn tại")
        void badChannelsAndWorkspaces() {
            assertThatThrownBy(() -> service.create(input("118", SenderKind.SHORTCODE, List.of(), GatewayInput.NONE,
                    PolicyInput.INHERIT), "a")).satisfies(e -> assertThat(keys(e)).contains("error.validation.channelsRequired"));
            assertThatThrownBy(() -> service.create(input("118", SenderKind.SHORTCODE, List.of("EMAIL"), GatewayInput.NONE,
                    PolicyInput.INHERIT), "a")).satisfies(e -> assertThat(fields(e)).contains("channels"));
            SenderInput unknownWs = new SenderInput("118", SenderKind.SHORTCODE, null, true, List.of("SMS"), false,
                    List.of(1L, 999L), GatewayInput.NONE, PolicyInput.INHERIT);
            assertThatThrownBy(() -> service.create(unknownWs, "a")).satisfies(e -> assertThat(fields(e)).contains("workspaceIds"));
        }

        @Test
        @DisplayName("SMPP thiếu host / port / system_id, mật khẩu > 8 ký tự; HTTP URL sai")
        void gatewayFields() {
            GatewayInput bad = new GatewayInput("SMPP", null, 70000, null, null, null, "123456789", false, 0);
            assertThatThrownBy(() -> service.create(input("118", SenderKind.SHORTCODE, List.of("SMS"), bad,
                    PolicyInput.INHERIT), "a"))
                    .satisfies(e -> assertThat(fields(e)).contains("gateway.host", "gateway.port", "gateway.username",
                            "gateway.password", "gateway.tps"));
            GatewayInput http = new GatewayInput("HTTP", null, null, null, "ftp://gw.local", null, null, false, null);
            assertThatThrownBy(() -> service.create(input("118", SenderKind.SHORTCODE, List.of("SMS"), http,
                    PolicyInput.INHERIT), "a"))
                    .satisfies(e -> assertThat(keys(e)).contains("error.validation.urlInvalid"));
            GatewayInput proto = new GatewayInput("SOAP", null, null, null, null, null, null, false, null);
            assertThatThrownBy(() -> service.create(input("118", SenderKind.SHORTCODE, List.of("SMS"), proto,
                    PolicyInput.INHERIT), "a"))
                    .satisfies(e -> assertThat(fields(e)).contains("gateway.protocol"));
        }

        @Test
        @DisplayName("chính sách: khung giờ riêng cần HH:mm, retry / giới hạn ngoài khoảng")
        void policyFields() {
            PolicyInput p = new PolicyInput(11, 0, true, "25:00", null, 0L);
            assertThatThrownBy(() -> service.create(input("118", SenderKind.SHORTCODE, List.of("SMS"), GatewayInput.NONE, p), "a"))
                    .satisfies(e -> assertThat(fields(e)).contains("policy.retryMaxAttempts", "policy.retryIntervalMinutes",
                            "policy.silentFrom", "policy.silentTo", "policy.dailyLimit"));
        }

        @Test
        @DisplayName("trùng mã → 409 SENDER_CODE_DUPLICATE")
        void duplicate() {
            when(repo.existsByCode("118")).thenReturn(true);
            assertThatThrownBy(() -> service.create(input("118", SenderKind.SHORTCODE, List.of("SMS"), GatewayInput.NONE,
                    PolicyInput.INHERIT), "a"))
                    .isInstanceOfSatisfying(LaoadsException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.SENDER_CODE_DUPLICATE));
        }
    }

    @Nested
    @DisplayName("Xoá đầu số")
    class Delete {

        @Test
        @DisplayName("không còn chiến dịch nào dùng → xoá")
        void deletesWhenUnused() {
            SenderNumber s = existing(7, "209");
            when(repo.findById(7L)).thenReturn(Optional.of(s));
            when(campaignService.codesUsingSender("209")).thenReturn(List.of());

            assertThat(service.delete(7, "superadmin")).isSameAs(s);
            verify(repo).delete(s);
        }

        @Test
        @DisplayName("chiến dịch chưa kết thúc đang dùng → 409 SENDER_IN_USE, nêu tối đa 5 mã")
        void blockedWhenInUse() {
            when(repo.findById(1L)).thenReturn(Optional.of(existing(1, "118")));
            when(campaignService.codesUsingSender("118"))
                    .thenReturn(List.of("CP01", "CP02", "CP03", "CP04", "CP05", "CP06", "CP07"));

            assertThatThrownBy(() -> service.delete(1, "superadmin"))
                    .isInstanceOfSatisfying(LaoadsException.class, e -> {
                        assertThat(e.getCode()).isEqualTo(ErrorCode.SENDER_IN_USE);
                        assertThat(e.getArgs()).containsExactly("CP01, CP02, CP03, CP04, CP05 (+2)");
                    });
            verify(repo, never()).delete(any());
        }

        @Test
        @DisplayName("không tồn tại → 404")
        void notFound() {
            when(repo.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.delete(99, "superadmin"))
                    .isInstanceOfSatisfying(LaoadsException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("Kiểm tra kết nối / đầu số dùng được")
    class TestAndAvailable {

        @Test
        @DisplayName("chưa cấu hình kết nối → 400 VALIDATION_ERROR, không gọi ra mạng")
        void notConfigured() {
            when(repo.findById(1L)).thenReturn(Optional.of(existing(1, "UNITEL")));
            assertThatThrownBy(() -> service.testConnection(1, "a"))
                    .satisfies(e -> assertThat(fields(e)).containsExactly("gateway.protocol"));
            verify(probe, never()).probe(any());
        }

        @Test
        @DisplayName("gọi probe với mật khẩu đã giải mã và lưu kết quả vào đầu số")
        void probesWithDecryptedPassword() {
            SenderNumber s = existing(1, "118");
            s.setGwProtocol(GatewayProtocol.SMPP);
            s.setGwHost("smsc.local");
            s.setGwPort(2775);
            s.setGwUsername("laoads");
            s.setGwPasswordEnc(cipher.encrypt("secret"));
            when(repo.findById(1L)).thenReturn(Optional.of(s));
            when(probe.probe(any())).thenReturn(new ConnectionProbe.Result(ConnectionTestCode.AUTH_FAILED, "Sai mật khẩu", 12L));

            var out = service.testConnection(1, "a");

            ArgumentCaptor<ConnectionProbe.Target> target = ArgumentCaptor.forClass(ConnectionProbe.Target.class);
            verify(probe).probe(target.capture());
            assertThat(target.getValue().password()).isEqualTo("secret");
            assertThat(target.getValue().toString()).doesNotContain("secret");
            assertThat(out.ok()).isFalse();
            assertThat(s.getGwTestCode()).isEqualTo(ConnectionTestCode.AUTH_FAILED);
            assertThat(s.getGwTestOk()).isFalse();
            assertThat(s.getGwTestLatencyMs()).isEqualTo(12L);
            verify(repo).save(s);
        }

        @Test
        @DisplayName("available: chỉ đầu số đang bật, trong phạm vi workspace và hỗ trợ kênh")
        void available() {
            SenderNumber sms = existing(1, "118");
            SenderNumber off = existing(2, "209");
            off.setEnabled(false);
            SenderNumber ussd = existing(3, "1555");
            ussd.setChannelSet(Set.of(Channel.SMS, Channel.USSD));
            SenderNumber scoped = existing(4, "BRAND");
            scoped.setAllWorkspaces(false);
            scoped.setWorkspaceIds(new java.util.HashSet<>(Set.of(2L)));
            when(repo.findAllByOrderByIdAsc()).thenReturn(List.of(sms, off, ussd, scoped));

            assertThat(service.available(1, Channel.SMS)).extracting(SenderNumber::getCode).containsExactly("118", "1555");
            assertThat(service.available(1, Channel.USSD)).extracting(SenderNumber::getCode).containsExactly("1555");
            assertThat(service.available(2, null)).extracting(SenderNumber::getCode).containsExactly("118", "1555", "BRAND");
        }
    }

    @Nested
    @DisplayName("CampaignDefinition.usesSender")
    class UsesSender {

        private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        @Test
        @DisplayName("khớp mã ở bất kỳ nhóm / hành động nào, không phân biệt hoa thường")
        void matchesAnyAction() throws Exception {
            CampaignDefinition d = mapper.readValue("""
                    {"actionGroups":[
                      {"id":"g1","allocationPercent":50,"isControl":false,"actions":[
                        {"id":"a1","type":"ADD_DATA","delayMinutes":0,"senderId":null}]},
                      {"id":"g2","allocationPercent":50,"isControl":false,"actions":[
                        {"id":"a2","type":"NOTIFICATION_SMS","delayMinutes":0,"senderId":"unitel"}]}]}
                    """, CampaignDefinition.class);
            assertThat(d.usesSender("UNITEL")).isTrue();
            assertThat(d.usesSender("118")).isFalse();
            assertThat(d.usesSender(null)).isFalse();
            assertThat(mapper.readValue("{}", CampaignDefinition.class).usesSender("118")).isFalse();
        }
    }
}
