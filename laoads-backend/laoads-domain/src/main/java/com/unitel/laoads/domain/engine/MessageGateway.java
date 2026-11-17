package com.unitel.laoads.domain.engine;

import com.unitel.laoads.domain.campaign.CampaignEnums.Channel;
import com.unitel.laoads.domain.pricing.ActionType;

import java.math.BigDecimal;

/**
 * Port gửi tin / thực hiện hành động viễn thông. Bản hiện tại là gateway giả lập (laoads-infra, simulation);
 * khi có kết nối thật thay bằng SMPP/HTTP (SMS), WhatsApp Business API, SMTP (Email), USSD gateway, OCS/Upoint.
 */
public interface MessageGateway {

    /**
     * {@code senderId}: mã đầu số SMS / USSD hoặc brand WhatsApp; {@code whatsappTemplateId}: WhatsApp gửi bằng template
     * đã duyệt (khi đó {@code content} = null) — như laoads.com.
     */
    record Outbound(long messageId, Channel channel, ActionType actionType, String isdn, String senderId,
                    String subject, String content, BigDecimal amount, String packageCode, String language,
                    String whatsappTemplateId) {

        public Outbound(long messageId, Channel channel, ActionType actionType, String isdn, String senderId,
                        String subject, String content, BigDecimal amount, String packageCode, String language) {
            this(messageId, channel, actionType, isdn, senderId, subject, content, amount, packageCode, language, null);
        }
    }

    /**
     * @param success     gateway nhận tin / OCS cộng tài nguyên thành công
     * @param delivered   đã có báo cáo phát (DLR) ngay khi gửi
     * @param retryable   lỗi tạm thời, nên thử lại (theo cấu hình retry)
     * @param providerRef mã tham chiếu phía nhà cung cấp
     */
    record Result(boolean success, boolean delivered, boolean retryable, String providerRef, String error) {

        public static Result ok(String ref, boolean delivered) {
            return new Result(true, delivered, false, ref, null);
        }

        public static Result fail(String error, boolean retryable) {
            return new Result(false, false, retryable, null, error);
        }
    }

    Result send(Outbound message);

    /** Tên hiển thị trong log / kết quả bắn tin thử. */
    String name();
}
