package com.unitel.laoads.infra.simulation;

import com.unitel.laoads.domain.engine.ConversionSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Nguồn chuyển đổi GIẢ LẬP (thay cho so sánh snapshot BI ngày T và T+n). Tất định theo ISDN: ~12% thuê bao
 * nhận tin và ~4% thuê bao nhóm đối chứng được coi là đã chuyển đổi → uplift ≈ +8 điểm %.
 */
@Component
@ConditionalOnProperty(name = "laoads.engine.conversion", havingValue = "simulated", matchIfMissing = true)
public class SimulatedConversionSource implements ConversionSource {

    static final int TREATMENT_PER_MILLE = 120;
    static final int CONTROL_PER_MILLE = 40;

    @Override
    public Set<Long> converted(long campaignId, String goalDescription, Collection<Candidate> candidates) {
        Set<Long> out = new HashSet<>();
        for (Candidate c : candidates) {
            int bucket = Math.floorMod((c.isdn() + ":" + campaignId + ":conv").hashCode(), 1000);
            if (bucket < (c.control() ? CONTROL_PER_MILLE : TREATMENT_PER_MILLE)) {
                out.add(c.targetId());
            }
        }
        return out;
    }
}
