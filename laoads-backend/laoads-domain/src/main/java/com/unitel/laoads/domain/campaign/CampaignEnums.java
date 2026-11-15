package com.unitel.laoads.domain.campaign;

import com.unitel.laoads.domain.pricing.ActionType;

/** Trạng thái / chế độ / kiểu chiến dịch và kênh gửi (giữ đúng giá trị FE). */
public final class CampaignEnums {

    private CampaignEnums() {
    }

    public enum Status { DRAFT, PENDING_APPROVAL, SCHEDULED, RUNNING, PAUSED, COMPLETED, REJECTED, CANCELLED }

    public enum Mode { OFFICIAL, TEST }

    public enum Type { STATIC, EVENT }

    /** Kênh của một tin. OCS = hành động viễn thông (nạp tiền, tặng data…), không phải kênh truyền thông. */
    public enum Channel {
        SMS, EMAIL, WHATSAPP, USSD, OCS;

        public static Channel of(ActionType t) {
            return switch (t) {
                case NOTIFICATION_SMS -> SMS;
                case NOTIFICATION_EMAIL -> EMAIL;
                case NOTIFICATION_WHATSAPP -> WHATSAPP;
                case NOTIFICATION_USSD -> USSD;
                default -> OCS;
            };
        }

        public boolean isNotification() {
            return this != OCS;
        }
    }
}
