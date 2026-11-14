package com.unitel.laoads.domain.pricing;

/** 13 loại hành động tính phí trong bảng giá (contract §7). */
public enum ActionType {
    TOP_UP,
    ADD_MONEY,
    ADD_PROMOTION,
    ADD_DATA,
    ADD_SMS,
    ADD_CALL_MINUTES,
    ADD_PACKAGE,
    REGISTER_CRBT,
    REGISTER_LAOTV,
    NOTIFICATION_SMS,
    NOTIFICATION_EMAIL,
    NOTIFICATION_WHATSAPP,
    NOTIFICATION_USSD
}
