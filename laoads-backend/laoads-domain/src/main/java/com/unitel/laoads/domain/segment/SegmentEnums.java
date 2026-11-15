package com.unitel.laoads.domain.segment;

/** Kiểu, trạng thái, nguồn của tệp khách hàng (giữ đúng giá trị FE / contract). */
public final class SegmentEnums {

    private SegmentEnums() {
    }

    public enum Type { FILE, QUERY, EVENT, CAMPAIGN_OUTCOME }

    public enum Status { BUILDING, READY, FAILED, ARCHIVED }

    public enum Source { OCS, DWH, FILE, CAMPAIGN, EVENT }
}
