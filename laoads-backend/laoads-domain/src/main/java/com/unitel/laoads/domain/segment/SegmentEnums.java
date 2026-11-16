package com.unitel.laoads.domain.segment;

/** Kiểu, trạng thái, nguồn của tệp khách hàng (giữ đúng giá trị FE / contract). */
public final class SegmentEnums {

    private SegmentEnums() {
    }

    /** AUDIENCE: kéo từ Audience API (OCS / BCCS3) bằng stream NDJSON. */
    public enum Type { FILE, QUERY, EVENT, CAMPAIGN_OUTCOME, AUDIENCE }

    public enum Status { BUILDING, READY, FAILED, ARCHIVED }

    public enum Source { OCS, DWH, FILE, CAMPAIGN, EVENT, BCCS3 }
}
