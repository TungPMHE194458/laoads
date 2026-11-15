package com.unitel.laoads.domain.engine;

import java.util.Collection;
import java.util.Set;

/**
 * Port đo chuyển đổi tự động (mục tiêu AUTO_OCS). Thiết kế đích (tài liệu "Luồng lấy dữ liệu từ BI"):
 * so sánh snapshot BI của cùng ISDN giữa ngày gửi T và T+n (vd real_package_data đổi gói ngày → gói tháng,
 * g_volume tăng, active_umoney = 1). Bản hiện tại là nguồn giả lập (laoads-infra, simulation).
 */
public interface ConversionSource {

    record Candidate(long targetId, String isdn, boolean control) {
    }

    /** Trả về targetId đã chuyển đổi trong số ứng viên của một chiến dịch. */
    Set<Long> converted(long campaignId, String goalDescription, Collection<Candidate> candidates);
}
