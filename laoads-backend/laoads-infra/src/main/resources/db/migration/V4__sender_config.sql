-- =====================================================================================================
-- V4 — Cấu hình từng đầu số gửi tin: kênh sử dụng, kết nối gateway (SMPP / HTTP), phạm vi workspace,
--      chính sách gửi riêng (retry, khung giờ không gửi, giới hạn tin/ngày); ghi đầu số trên từng tin gửi.
-- Oracle 11.2: NUMBER(1) + CHECK thay BOOLEAN, identifier ≤ 30 ký tự, tiền tố LADS_.
-- Cột mới đều NULL được hoặc có DEFAULT → đầu số cũ giữ nguyên hành vi (SMS, mọi workspace, theo chính sách chung).
-- =====================================================================================================

ALTER TABLE LADS_SENDER_NUMBER ADD (
  CHANNELS           VARCHAR2(32 CHAR)  DEFAULT 'SMS' NOT NULL,
  ALL_WORKSPACES     NUMBER(1)          DEFAULT 1 NOT NULL,
  -- Kết nối gateway; GW_PROTOCOL NULL = chưa cấu hình (dùng gateway mặc định của hệ thống)
  GW_PROTOCOL        VARCHAR2(8 CHAR),
  GW_HOST            VARCHAR2(255 CHAR),
  GW_PORT            NUMBER(5),
  GW_SYSTEM_TYPE     VARCHAR2(13 CHAR),
  GW_URL             VARCHAR2(1000 CHAR),
  GW_USERNAME        VARCHAR2(128 CHAR),
  -- Mật khẩu mã hoá AES-GCM (SecretCipher), không bao giờ trả về API
  GW_PASSWORD_ENC    VARCHAR2(1000 CHAR),
  GW_TPS             NUMBER(6),
  GW_TEST_AT         TIMESTAMP,
  GW_TEST_OK         NUMBER(1),
  GW_TEST_CODE       VARCHAR2(16 CHAR),
  GW_TEST_DETAIL     VARCHAR2(500 CHAR),
  GW_TEST_LATENCY_MS NUMBER(10),
  -- Chính sách riêng; NULL = theo chính sách chung (LADS_APP_SETTING notification.*)
  RETRY_MAX          NUMBER(2),
  RETRY_INTERVAL_MIN NUMBER(4),
  SILENT_ENABLED     NUMBER(1),
  SILENT_FROM        VARCHAR2(5 CHAR),
  SILENT_TO          VARCHAR2(5 CHAR),
  DAILY_LIMIT        NUMBER(10)
);

ALTER TABLE LADS_SENDER_NUMBER ADD CONSTRAINT LADS_SENDER_ALLWS_CK CHECK (ALL_WORKSPACES IN (0, 1));
ALTER TABLE LADS_SENDER_NUMBER ADD CONSTRAINT LADS_SENDER_PROTO_CK CHECK (GW_PROTOCOL IS NULL OR GW_PROTOCOL IN ('SMPP', 'HTTP'));
ALTER TABLE LADS_SENDER_NUMBER ADD CONSTRAINT LADS_SENDER_TESTOK_CK CHECK (GW_TEST_OK IS NULL OR GW_TEST_OK IN (0, 1));
ALTER TABLE LADS_SENDER_NUMBER ADD CONSTRAINT LADS_SENDER_SILENT_CK CHECK (SILENT_ENABLED IS NULL OR SILENT_ENABLED IN (0, 1));

-- Workspace được dùng đầu số khi ALL_WORKSPACES = 0
CREATE TABLE LADS_SENDER_WORKSPACE (
  SENDER_ID     NUMBER(19) NOT NULL,
  WORKSPACE_ID  NUMBER(19) NOT NULL,
  CONSTRAINT LADS_SENDER_WS_PK PRIMARY KEY (SENDER_ID, WORKSPACE_ID),
  CONSTRAINT LADS_SENDER_WS_SENDER_FK FOREIGN KEY (SENDER_ID) REFERENCES LADS_SENDER_NUMBER (ID) ON DELETE CASCADE,
  CONSTRAINT LADS_SENDER_WS_WS_FK FOREIGN KEY (WORKSPACE_ID) REFERENCES LADS_WORKSPACE (ID) ON DELETE CASCADE
);
CREATE INDEX LADS_SENDER_WS_WS_IX ON LADS_SENDER_WORKSPACE (WORKSPACE_ID);

-- Đầu số thực tế đã dùng cho từng tin (đếm giới hạn tin/ngày, báo cáo theo đầu số)
ALTER TABLE LADS_CAMPAIGN_MESSAGE ADD (SENDER_CODE VARCHAR2(32 CHAR));
CREATE INDEX LADS_CMSG_SENDER_IX ON LADS_CAMPAIGN_MESSAGE (SENDER_CODE, SENT_AT);
