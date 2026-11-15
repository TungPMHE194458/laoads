package com.unitel.laoads.domain.campaign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignHistoryRepository extends JpaRepository<CampaignHistory, Long> {

    List<CampaignHistory> findByCampaignIdOrderByActionAtAscIdAsc(Long campaignId);
}
