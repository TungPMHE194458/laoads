package com.unitel.laoads.infra.engine;

import com.unitel.laoads.domain.engine.CampaignRunner;
import com.unitel.laoads.domain.engine.EngineMaintenance;
import com.unitel.laoads.domain.engine.MessageDispatcher;
import com.unitel.laoads.domain.segment.AudienceSegmentImporter;
import com.unitel.laoads.domain.segment.SegmentService;
import com.unitel.laoads.infra.bootstrap.DatabaseBootstrap;
import com.unitel.laoads.infra.simulation.EngagementSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Lịch chạy của engine. Mọi job chỉ chạy khi Flyway đã migrate xong (BE vẫn khởi động được khi DB down) và
 * nuốt lỗi từng việc để một chiến dịch hỏng không chặn các chiến dịch khác. Tắt toàn bộ:
 * {@code laoads.engine.enabled=false}. Giả định 1 instance BE chạy job.
 */
@Component
@ConditionalOnProperty(name = "laoads.engine.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class EngineJobs {

    private final DatabaseBootstrap db;
    private final SegmentService segmentService;
    private final AudienceSegmentImporter audienceImporter;
    private final CampaignRunner runner;
    private final MessageDispatcher dispatcher;
    private final EngineMaintenance maintenance;
    private final ObjectProvider<EngagementSimulator> simulator;
    private final int batch;

    public EngineJobs(DatabaseBootstrap db, SegmentService segmentService, AudienceSegmentImporter audienceImporter,
                      CampaignRunner runner,
                      MessageDispatcher dispatcher, EngineMaintenance maintenance,
                      ObjectProvider<EngagementSimulator> simulator,
                      @Value("${laoads.engine.dispatch-batch:500}") int batch) {
        this.db = db;
        this.segmentService = segmentService;
        this.audienceImporter = audienceImporter;
        this.runner = runner;
        this.dispatcher = dispatcher;
        this.maintenance = maintenance;
        this.simulator = simulator;
        this.batch = batch;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 15000)
    public void buildSegments() {
        if (!db.isMigrated()) {
            return;
        }
        for (Long id : safe(segmentService::pendingQueryBuilds, java.util.List.<Long>of())) {
            run("build tệp #" + id, () -> segmentService.buildQuerySegment(id));
        }
    }

    /**
     * Nạp tệp AUDIENCE từ Audience API (OCS / BCCS3). Stream có thể kéo dài nhiều phút nên chạy job riêng
     * (pool lập lịch 6 luồng) để không chặn build tệp QUERY hay gửi tin.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 18000)
    public void buildAudienceSegments() {
        if (!db.isMigrated()) {
            return;
        }
        for (Long id : safe(segmentService::pendingAudienceBuilds, java.util.List.<Long>of())) {
            run("nạp tệp AUDIENCE #" + id, () -> audienceImporter.build(id));
        }
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 20000)
    public void startRuns() {
        if (!db.isMigrated()) {
            return;
        }
        for (Long id : safe(() -> runner.dueCampaigns(Instant.now()), java.util.List.<Long>of())) {
            run("chạy lượt chiến dịch #" + id, () -> runner.startRun(id));
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 25000)
    public void dispatch() {
        if (!db.isMigrated()) {
            return;
        }
        for (int i = 0; i < 10; i++) {
            int n = safe(() -> dispatcher.dispatch(batch), 0);
            if (n < batch) {
                return;
            }
        }
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void maintain() {
        if (!db.isMigrated()) {
            return;
        }
        EngagementSimulator sim = simulator.getIfAvailable();
        if (sim != null) {
            run("giả lập phản hồi", sim::simulate);
        }
        run("đóng lượt", maintenance::finalizeRuns);
        for (Long id : safe(maintenance::completable, java.util.List.<Long>of())) {
            run("hoàn tất chiến dịch #" + id, () -> maintenance.complete(id));
        }
        run("đo chuyển đổi", maintenance::measureConversions);
        run("tổng hợp số liệu", maintenance::refreshStats);
    }

    private static void run(String what, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.warn("[ENGINE] Lỗi khi {}: {}", what, e.getMessage(), e);
        }
    }

    private static <T> T safe(java.util.function.Supplier<T> s, T fallback) {
        try {
            return s.get();
        } catch (RuntimeException e) {
            log.warn("[ENGINE] {}", e.getMessage(), e);
            return fallback;
        }
    }
}
