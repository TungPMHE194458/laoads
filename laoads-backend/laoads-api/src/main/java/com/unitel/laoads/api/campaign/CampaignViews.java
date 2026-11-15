package com.unitel.laoads.api.campaign;

import com.unitel.laoads.api.campaign.CampaignDtos.Detail;
import com.unitel.laoads.api.campaign.CampaignDtos.Summary;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.domain.campaign.Campaign;
import com.unitel.laoads.domain.campaign.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dựng DTO chiến dịch (tên nhóm / tệp KH lấy theo lô để tránh N+1). Dùng chung cho các controller chiến dịch. */
@Component
@RequiredArgsConstructor
public class CampaignViews {

    private final CampaignService service;

    public ApiResponse<List<Summary>> summaries(long ws, Page<Campaign> p) {
        Map<Long, String> groups = service.groupNames(ws);
        Map<Long, String> segments = service.segmentNames(p.getContent().stream()
                .map(Campaign::getSegmentId).filter(Objects::nonNull).toList());
        return ApiResponse.page(p, c -> Summary.from(c, c.getGroupId() == null ? null : groups.get(c.getGroupId()),
                c.getSegmentId() == null ? null : segments.get(c.getSegmentId())));
    }

    public Detail detail(Campaign c) {
        String group = c.getGroupId() == null ? null : service.groupNames(c.getWorkspaceId()).get(c.getGroupId());
        String segment = c.getSegmentId() == null ? null
                : service.segmentNames(List.of(c.getSegmentId())).get(c.getSegmentId());
        return Detail.from(c, service.definitionOf(c), group, segment, service.history(c.getId()));
    }
}
