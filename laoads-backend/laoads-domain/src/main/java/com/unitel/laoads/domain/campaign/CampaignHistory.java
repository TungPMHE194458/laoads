package com.unitel.laoads.domain.campaign;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** LADS_CAMPAIGN_HISTORY — dòng thời gian thao tác của chiến dịch (CREATE, SUBMIT, APPROVE, START…). */
@Entity
@Table(name = "LADS_CAMPAIGN_HISTORY")
@Getter
@Setter
@NoArgsConstructor
public class CampaignHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lads_campaign_history_seq")
    @SequenceGenerator(name = "lads_campaign_history_seq", sequenceName = "LADS_CAMPAIGN_HISTORY_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CAMPAIGN_ID", nullable = false)
    private Long campaignId;

    @Column(name = "ACTION_AT", nullable = false)
    private Instant actionAt;

    @Column(name = "ACTOR", nullable = false, length = 64)
    private String actor;

    @Column(name = "ACTION", nullable = false, length = 32)
    private String action;

    @Column(name = "NOTE", length = 1000)
    private String note;

    public static CampaignHistory of(long campaignId, String actor, String action, String note) {
        CampaignHistory h = new CampaignHistory();
        h.setCampaignId(campaignId);
        h.setActionAt(Instant.now());
        h.setActor(actor == null ? "system" : actor);
        h.setAction(action);
        h.setNote(note == null || note.isBlank() ? null : (note.length() > 1000 ? note.substring(0, 1000) : note));
        return h;
    }
}
