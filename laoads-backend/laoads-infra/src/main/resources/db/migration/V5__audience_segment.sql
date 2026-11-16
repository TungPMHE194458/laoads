-- =====================================================================================================
-- V5 — Tệp khách hàng lấy từ Audience API (OCS Audience / BCCS3 Audience — tài liệu "Tài liệu API tích hợp").
--      Loại tệp mới AUDIENCE, nguồn OCS | BCCS3; lưu request (bảng, cột, điều kiện) và số dòng NDJSON đã xử lý
--      (= offset để nạp tiếp khi stream OCS bị gián đoạn).
-- Oracle 11.2: identifier ≤ 30 ký tự, tiền tố LADS_.
-- =====================================================================================================

ALTER TABLE LADS_SEGMENT DROP CONSTRAINT LADS_SEGMENT_TYPE_CK;
ALTER TABLE LADS_SEGMENT ADD CONSTRAINT LADS_SEGMENT_TYPE_CK
  CHECK (SEGMENT_TYPE IN ('FILE', 'QUERY', 'EVENT', 'CAMPAIGN_OUTCOME', 'AUDIENCE'));

ALTER TABLE LADS_SEGMENT ADD (
  -- {"tableName", "selectedColumns":[{columnName, dataType, targetField}], "conditions":[...]} — đúng shape API
  AUDIENCE_JSON   CLOB,
  -- số dòng NDJSON đã xử lý (kể cả dòng số thuê bao không hợp lệ); OCS nạp tiếp từ đây
  IMPORTED_ROWS   NUMBER(12) DEFAULT 0 NOT NULL
);
