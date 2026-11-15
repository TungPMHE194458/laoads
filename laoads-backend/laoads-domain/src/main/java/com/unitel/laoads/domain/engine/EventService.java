package com.unitel.laoads.domain.engine;

import com.unitel.laoads.domain.campaign.Campaign;
import com.unitel.laoads.domain.campaign.CampaignEnums.Status;
import com.unitel.laoads.domain.campaign.CampaignEnums.Type;
import com.unitel.laoads.domain.campaign.CampaignRepository;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.Isdns;
import com.unitel.laoads.domain.common.LaoadsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * API sự kiện: hệ thống ngoài báo một sự kiện của thuê bao (kích hoạt SIM, nạp thẻ lần đầu…) kèm API key của
 * workspace → thuê bao được đưa vào mọi chiến dịch EVENT đang chạy có cùng template sự kiện.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    public record Accepted(String eventType, String isdn, int matchedCampaigns, int enrolled) {
    }

    private final ApiKeyService apiKeyService;
    private final CampaignRepository campaignRepo;
    private final CampaignRunner runner;
    private final JdbcTemplate jdbc;

    public Accepted ingest(String apiKey, String eventType, String rawIsdn) {
        long ws = apiKeyService.resolve(apiKey);
        String type = eventType == null ? "" : eventType.trim().toUpperCase(Locale.ROOT);
        if (!type.matches("[A-Z0-9_]{2,64}")) {
            throw LaoadsException.validation(List.of(new FieldViolation("eventType", "error.validation.invalidValue",
                    "Giá trị không hợp lệ")));
        }
        String isdn = Isdns.normalize(rawIsdn).orElseThrow(() -> LaoadsException.validation(List.of(
                new FieldViolation("isdn", "error.validation.isdnInvalid", "Số thuê bao không hợp lệ", rawIsdn))));
        List<Campaign> campaigns = campaignRepo.findByWorkspaceIdAndTypeAndStatus(ws, Type.EVENT, Status.RUNNING).stream()
                .filter(c -> type.equals(c.eventTemplate())).toList();
        int enrolled = 0;
        for (Campaign c : campaigns) {
            if (runner.addEventTarget(c.getId(), isdn)) {
                enrolled++;
            }
        }
        jdbc.update("INSERT INTO LADS_INBOUND_EVENT (ID, WORKSPACE_ID, EVENT_TYPE, ISDN, RECEIVED_AT, MATCHED_CAMPAIGNS) "
                + "VALUES (LADS_INBOUND_EVENT_SEQ.NEXTVAL, ?, ?, ?, ?, ?)", ws, type, isdn, Utc.ts(Instant.now()), campaigns.size());
        log.info("[EVENT] workspace #{} sự kiện {} cho {}: khớp {} chiến dịch, ghi danh {}", ws, type, Isdns.mask(isdn),
                campaigns.size(), enrolled);
        return new Accepted(type, Isdns.mask(isdn), campaigns.size(), enrolled);
    }
}
