-- =====================================================================================
-- V7 — Mô tả nhóm chiến dịch (laoads.com: "Tạo nhóm chiến dịch mới" có ô Mô tả, tối đa 500 ký tự;
-- tab "Nhóm chiến dịch" có cột MÔ TẢ). Cột cho phép NULL — nhóm cũ không có mô tả.
-- =====================================================================================

ALTER TABLE LADS_CAMPAIGN_GROUP ADD (DESCRIPTION VARCHAR2(500 CHAR));

COMMENT ON COLUMN LADS_CAMPAIGN_GROUP.DESCRIPTION IS 'Mô tả nhóm chiến dịch (tối đa 500 ký tự)';
