-- =====================================================================================
-- V6 — Kết quả thao tác trong nhật ký hệ thống (SRS 3.1.8: cột trạng thái Thành công / Thất bại)
-- Oracle 11.2: thêm cột NOT NULL có DEFAULT — các dòng cũ nhận SUCCESS (trước V6 chỉ ghi thao tác thành công).
-- =====================================================================================

ALTER TABLE LADS_AUDIT_LOG ADD (
  RESULT      VARCHAR2(16 CHAR) DEFAULT 'SUCCESS' NOT NULL,
  ERROR_CODE  VARCHAR2(64 CHAR)
);

CREATE INDEX LADS_AUDIT_RESULT_IX ON LADS_AUDIT_LOG (RESULT, CREATED_AT);

COMMENT ON COLUMN LADS_AUDIT_LOG.RESULT IS 'SUCCESS | FAILURE — thao tác thành công hay bị lỗi';
COMMENT ON COLUMN LADS_AUDIT_LOG.ERROR_CODE IS 'Mã lỗi (ErrorCode / tên exception) khi RESULT = FAILURE';
