package com.unitel.laoads.domain.engine;

import com.unitel.laoads.domain.campaign.CampaignDefinition.GoalDef;

import java.util.Collection;
import java.util.Set;

/**
 * Port đo chuyển đổi tự động (mục tiêu AUTO_OCS). Thiết kế đích (tài liệu "Luồng lấy dữ liệu từ BI"):
 * so sánh snapshot BI của cùng ISDN giữa ngày gửi T và T+n (vd real_package_data đổi gói ngày → gói tháng,
 * g_volume tăng, active_umoney = 1). Hai bản: giả lập (laoads.engine.conversion=simulated, mặc định) và
 * {@code snapshot} — khách hàng đạt mục tiêu khi dữ liệu BI mới nhất (LADS_SUBSCRIBER) thoả điều kiện của mục tiêu.
 */
public interface ConversionSource {

    record Candidate(long targetId, String isdn, boolean control) {
    }

    /** Trả về targetId đã chuyển đổi trong số ứng viên của một chiến dịch. */
    Set<Long> converted(long campaignId, GoalDef goal, Collection<Candidate> candidates);
}
