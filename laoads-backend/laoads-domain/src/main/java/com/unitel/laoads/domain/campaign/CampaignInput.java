package com.unitel.laoads.domain.campaign;

import java.util.List;

/** Dữ liệu tạo / sửa chiến dịch từ wizard (target, kênh, chi phí do BE tự tính). */
public record CampaignInput(
        String name,
        String description,
        CampaignEnums.Mode mode,
        CampaignEnums.Type type,
        Long groupId,
        Long segmentId,
        String language,
        List<String> testIsdns,
        CampaignDefinition definition
) {
}
