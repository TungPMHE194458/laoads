package com.unitel.laoads.infra.simulation;

import com.unitel.laoads.domain.campaign.CampaignEnums.Channel;
import com.unitel.laoads.domain.engine.MessageGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Gateway GIẢ LẬP cho SMS / Email / WhatsApp / USSD / OCS — không gửi ra ngoài, chỉ mô phỏng kết quả để engine,
 * báo cáo và dashboard chạy đầu-cuối. Thay bằng adapter thật khi có thông số kết nối
 * ({@code laoads.engine.gateway} khác "simulated").
 *
 * <p>Quy tắc mô phỏng: WhatsApp lỗi với số kết thúc bằng 0 (không có tài khoản); ~6% email không có địa chỉ;
 * ~2% lỗi tạm thời (được retry); SMS / OCS có báo phát ngay, kênh khác báo phát sau (EngagementSimulator).
 */
@Component
@ConditionalOnProperty(name = "laoads.engine.gateway", havingValue = "simulated", matchIfMissing = true)
@Slf4j
public class SimulatedMessageGateway implements MessageGateway {

    @Override
    public Result send(Outbound m) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextInt(100) < 2) {
            return Result.fail("Gateway " + m.channel() + " tạm thời không phản hồi (giả lập)", true);
        }
        int h = Math.floorMod((m.isdn() + m.channel()).hashCode(), 100);
        if (m.channel() == Channel.WHATSAPP && m.isdn().endsWith("0")) {
            return Result.fail("Thuê bao không có tài khoản WhatsApp", false);
        }
        if (m.channel() == Channel.EMAIL && h < 6) {
            return Result.fail("Thuê bao chưa đăng ký địa chỉ email", false);
        }
        if (m.channel() == Channel.OCS && h < 3) {
            return Result.fail("OCS từ chối: thuê bao không đủ điều kiện nhận ưu đãi", false);
        }
        String ref = "SIM-" + m.channel() + "-" + Long.toString(System.nanoTime(), 36).toUpperCase();
        boolean deliveredNow = m.channel() == Channel.SMS || m.channel() == Channel.OCS;
        log.debug("[SIM-GW] {} {} → {} ({})", m.channel(), m.actionType(), m.isdn(), ref);
        return Result.ok(ref, deliveredNow);
    }

    @Override
    public String name() {
        return "gateway giả lập";
    }
}
