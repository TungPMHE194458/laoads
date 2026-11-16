package com.unitel.laoads.domain.campaign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ActionGroupDef;
import com.unitel.laoads.domain.campaign.CampaignDefinition.ScheduleDef;
import com.unitel.laoads.domain.campaign.CampaignEnums.Channel;
import com.unitel.laoads.domain.campaign.CampaignEnums.Mode;
import com.unitel.laoads.domain.campaign.CampaignEnums.Status;
import com.unitel.laoads.domain.campaign.CampaignEnums.Type;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.FieldViolation;
import com.unitel.laoads.domain.common.Isdns;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.Texts;
import com.unitel.laoads.domain.pricing.ActionType;
import com.unitel.laoads.domain.pricing.PriceQuoteItem;
import com.unitel.laoads.domain.pricing.PriceQuoteService;
import com.unitel.laoads.domain.segment.Segment;
import com.unitel.laoads.domain.segment.SegmentRepository;
import com.unitel.laoads.domain.settings.SenderNumber;
import com.unitel.laoads.domain.settings.SenderNumberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Chiến dịch (SRS 3.2) + phê duyệt + nhóm chiến dịch. Vòng đời:
 * DRAFT/REJECTED → (submit) PENDING_APPROVAL → (approve) SCHEDULED/RUNNING → COMPLETED;
 * RUNNING ⇄ PAUSED; huỷ được khi PENDING_APPROVAL/SCHEDULED/RUNNING/PAUSED. Engine thực thi đọc
 * {@code nextRunAt} để chạy từng lượt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    public static final int TEST_LIMIT = 1000;
    private static final Pattern HH_MM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Set<String> SCHEDULE_TYPES = Set.of("NOW", "ONCE", "RECURRING");
    private static final Set<String> UNITS = Set.of("DAY", "WEEK", "MONTH");
    private static final Set<String> LANGUAGES = Set.of("LO", "EN", "VI");
    private static final Set<Status> EDITABLE = EnumSet.of(Status.DRAFT, Status.REJECTED);
    private static final Set<Status> DELETABLE = EnumSet.of(Status.DRAFT, Status.REJECTED, Status.CANCELLED);
    private static final Set<Status> CANCELLABLE =
            EnumSet.of(Status.PENDING_APPROVAL, Status.SCHEDULED, Status.RUNNING, Status.PAUSED);
    /** Chiến dịch còn có thể gửi tin: gồm cả Nháp / Bị từ chối vì vẫn sửa và gửi duyệt lại được. */
    private static final Set<Status> NOT_FINISHED = EnumSet.complementOf(EnumSet.of(Status.COMPLETED, Status.CANCELLED));

    private final CampaignRepository repo;
    private final CampaignHistoryRepository historyRepo;
    private final CampaignGroupRepository groupRepo;
    private final SegmentRepository segmentRepo;
    private final SenderNumberRepository senderRepo;
    private final PriceQuoteService priceQuoteService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    // ───────────── Đọc ─────────────

    @Transactional(readOnly = true)
    public Page<Campaign> list(long ws, String query, Status status, Mode mode, Long groupId, Pageable pageable) {
        return repo.search(ws, Texts.likePattern(query), status, mode, groupId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Campaign> pendingApprovals(long ws, String query, Mode mode, Pageable pageable) {
        return repo.search(ws, Texts.likePattern(query), Status.PENDING_APPROVAL, mode, null, pageable);
    }

    @Transactional(readOnly = true)
    public Campaign get(long ws, long id) {
        return repo.findByIdAndWorkspaceId(id, ws).orElseThrow(() -> notFound(id));
    }

    @Transactional(readOnly = true)
    public List<CampaignHistory> history(long campaignId) {
        return historyRepo.findByCampaignIdOrderByActionAtAscIdAsc(campaignId);
    }

    public CampaignDefinition definitionOf(Campaign c) {
        try {
            return mapper.readValue(c.getDefinitionJson(), CampaignDefinition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DEFINITION_JSON hỏng ở chiến dịch #" + c.getId(), e);
        }
    }

    /**
     * Mã các chiến dịch chưa kết thúc (mọi workspace) có hành động gửi bằng đầu số này — chặn xoá đầu số.
     * Đầu số được tham chiếu bằng mã trong DEFINITION_JSON, không có khoá ngoại.
     */
    @Transactional(readOnly = true)
    public List<String> codesUsingSender(String senderCode) {
        return repo.findByStatusIn(NOT_FINISHED).stream()
                .filter(c -> {
                    if (c.getDefinitionJson() == null) {
                        return false;
                    }
                    try {
                        return definitionOf(c).usesSender(senderCode);
                    } catch (IllegalStateException e) {
                        log.warn("[SENDER] bỏ qua chiến dịch #{} khi kiểm tra đầu số: {}", c.getId(), e.getMessage());
                        return false;
                    }
                })
                .map(Campaign::getCode)
                .sorted()
                .toList();
    }

    /** Tên nhóm / tệp KH cho danh sách chiến dịch (tránh N+1). */
    @Transactional(readOnly = true)
    public Map<Long, String> groupNames(long ws) {
        return groupRepo.findByWorkspaceIdOrderByNameAsc(ws).stream()
                .collect(Collectors.toMap(CampaignGroup::getId, CampaignGroup::getName));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> segmentNames(List<Long> segmentIds) {
        Map<Long, String> out = new HashMap<>();
        segmentRepo.findAllById(segmentIds.stream().distinct().toList()).forEach(s -> out.put(s.getId(), s.getName()));
        return out;
    }

    // ───────────── Ghi ─────────────

    @Transactional
    public Campaign create(long ws, CampaignInput in, boolean submit, String actor) {
        Validated v = validate(ws, in, submit);
        Campaign c = new Campaign();
        c.setWorkspaceId(ws);
        c.setCode("N" + System.nanoTime());
        c.setStatus(Status.DRAFT);
        c.setCreatedAt(Instant.now());
        c.setCreatedBy(actor);
        apply(c, in, v);
        c = repo.save(c);
        c.setCode("CP" + (2600 + c.getId()));
        historyRepo.save(CampaignHistory.of(c.getId(), actor, "CREATE", null));
        if (submit) {
            transition(c, Status.PENDING_APPROVAL, actor, "SUBMIT", null);
            c.setSubmittedAt(Instant.now());
        }
        log.info("[CAMPAIGN] {} tạo chiến dịch #{} {} '{}' ({})", actor, c.getId(), c.getCode(), c.getName(), c.getStatus());
        return c;
    }

    @Transactional
    public Campaign update(long ws, long id, CampaignInput in, boolean submit, String actor) {
        Campaign c = get(ws, id);
        if (!EDITABLE.contains(c.getStatus())) {
            throw new LaoadsException(ErrorCode.CAMPAIGN_LOCKED, "Chỉ sửa được chiến dịch Nháp hoặc Bị từ chối");
        }
        Validated v = validate(ws, in, submit);
        apply(c, in, v);
        c.setUpdatedAt(Instant.now());
        c.setUpdatedBy(actor);
        if (submit) {
            transition(c, Status.PENDING_APPROVAL, actor, "SUBMIT", null);
            c.setSubmittedAt(Instant.now());
            c.setRejectReason(null);
        }
        return c;
    }

    @Transactional
    public Campaign delete(long ws, long id, String actor) {
        Campaign c = get(ws, id);
        if (!DELETABLE.contains(c.getStatus())) {
            throw new LaoadsException(ErrorCode.CAMPAIGN_LOCKED, "Chỉ xoá được chiến dịch Nháp / Bị từ chối / Đã huỷ");
        }
        repo.delete(c);
        log.info("[CAMPAIGN] {} xoá chiến dịch #{} '{}'", actor, id, c.getName());
        return c;
    }

    @Transactional
    public Campaign submit(long ws, long id, String actor) {
        Campaign c = get(ws, id);
        requireStatus(c, EDITABLE);
        validate(ws, inputOf(c), true);
        c.setRejectReason(null);
        c.setSubmittedAt(Instant.now());
        return transition(c, Status.PENDING_APPROVAL, actor, "SUBMIT", null);
    }

    @Transactional
    public Campaign pause(long ws, long id, String actor) {
        Campaign c = get(ws, id);
        requireStatus(c, EnumSet.of(Status.RUNNING, Status.SCHEDULED));
        return transition(c, Status.PAUSED, actor, "PAUSE", null);
    }

    @Transactional
    public Campaign resume(long ws, long id, String actor) {
        Campaign c = get(ws, id);
        requireStatus(c, EnumSet.of(Status.PAUSED));
        boolean started = c.getRunCount() > 0 || c.getType() == Type.EVENT
                || c.getNextRunAt() == null || !c.getNextRunAt().isAfter(Instant.now());
        return transition(c, started ? Status.RUNNING : Status.SCHEDULED, actor, "RESUME", null);
    }

    @Transactional
    public Campaign cancel(long ws, long id, String actor) {
        Campaign c = get(ws, id);
        requireStatus(c, CANCELLABLE);
        c.setNextRunAt(null);
        jdbc.update("UPDATE LADS_CAMPAIGN_MESSAGE SET STATUS = 'CANCELLED' WHERE CAMPAIGN_ID = ? AND STATUS = 'PENDING'", id);
        jdbc.update("UPDATE LADS_CAMPAIGN_RUN SET STATUS = 'CANCELLED', FINISHED_AT = SYS_EXTRACT_UTC(SYSTIMESTAMP) "
                + "WHERE CAMPAIGN_ID = ? AND STATUS = 'RUNNING'", id);
        return transition(c, Status.CANCELLED, actor, "CANCEL", null);
    }

    @Transactional
    public Campaign duplicate(long ws, long id, String actor) {
        Campaign src = get(ws, id);
        Campaign c = new Campaign();
        c.setWorkspaceId(ws);
        c.setCode("N" + System.nanoTime());
        c.setName(truncate(src.getName() + " (bản sao)", 255));
        c.setDescription(src.getDescription());
        c.setMode(src.getMode());
        c.setType(src.getType());
        c.setStatus(Status.DRAFT);
        c.setGroupId(src.getGroupId());
        c.setSegmentId(src.getSegmentId());
        c.setTargetCount(src.getTargetCount());
        c.setChannels(src.getChannels());
        c.setStartAt(src.getStartAt());
        c.setEndAt(src.getEndAt());
        c.setLanguage(src.getLanguage());
        c.setTestIsdns(src.getTestIsdns());
        c.setDefinitionJson(src.getDefinitionJson());
        c.setExpectedCost(src.getExpectedCost());
        c.setBudgetMax(src.getBudgetMax());
        c.setCreatedAt(Instant.now());
        c.setCreatedBy(actor);
        c = repo.save(c);
        c.setCode("CP" + (2600 + c.getId()));
        historyRepo.save(CampaignHistory.of(c.getId(), actor, "CREATE", "Nhân bản từ " + src.getCode()));
        return c;
    }

    // ───────────── Phê duyệt ─────────────

    @Transactional
    public Campaign approve(long ws, long id, String actor) {
        Campaign c = get(ws, id);
        requireStatus(c, EnumSet.of(Status.PENDING_APPROVAL));
        Instant now = Instant.now();
        c.setApprovedAt(now);
        c.setApprovedBy(actor);
        c.setRejectReason(null);
        if (c.getType() == Type.EVENT) {
            c.setNextRunAt(null);
            return transition(c, Status.RUNNING, actor, "APPROVE", null);
        }
        Instant first = SchedulePlanner.firstRun(definitionOf(c).schedule(), now);
        if (first == null) {
            throw new LaoadsException(ErrorCode.INVALID_TRANSITION, "Lịch lặp lại không còn lượt chạy nào");
        }
        c.setNextRunAt(first);
        return transition(c, first.isAfter(now) ? Status.SCHEDULED : Status.RUNNING, actor, "APPROVE", null);
    }

    @Transactional
    public Campaign reject(long ws, long id, String reason, String actor) {
        Campaign c = get(ws, id);
        requireStatus(c, EnumSet.of(Status.PENDING_APPROVAL));
        String r = Texts.trimToNull(reason);
        if (r == null) {
            throw LaoadsException.validation(List.of(new FieldViolation("reason", "error.validation.rejectReason",
                    "Vui lòng nhập lý do từ chối")));
        }
        c.setRejectReason(truncate(r, 1000));
        return transition(c, Status.REJECTED, actor, "REJECT", r);
    }

    // ───────────── Nhóm chiến dịch ─────────────

    public record GroupView(CampaignGroup group, long campaignCount, long runningCount) {
    }

    @Transactional(readOnly = true)
    public List<GroupView> groups(long ws) {
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : repo.groupCounts(ws)) {
            counts.put((Long) row[0], new long[]{((Number) row[1]).longValue(), row[2] == null ? 0 : ((Number) row[2]).longValue()});
        }
        return groupRepo.findByWorkspaceIdOrderByNameAsc(ws).stream()
                .map(g -> {
                    long[] n = counts.getOrDefault(g.getId(), new long[]{0, 0});
                    return new GroupView(g, n[0], n[1]);
                }).toList();
    }

    @Transactional
    public CampaignGroup createGroup(long ws, String name, String actor) {
        String n = groupName(ws, name, null);
        CampaignGroup g = new CampaignGroup();
        g.setWorkspaceId(ws);
        g.setName(n);
        g.setCreatedAt(Instant.now());
        g.setCreatedBy(actor);
        return groupRepo.save(g);
    }

    @Transactional
    public CampaignGroup renameGroup(long ws, long id, String name) {
        CampaignGroup g = groupRepo.findByIdAndWorkspaceId(id, ws).orElseThrow(() -> groupNotFound(id));
        g.setName(groupName(ws, name, id));
        return g;
    }

    @Transactional
    public CampaignGroup deleteGroup(long ws, long id) {
        CampaignGroup g = groupRepo.findByIdAndWorkspaceId(id, ws).orElseThrow(() -> groupNotFound(id));
        if (repo.countByGroupId(id) > 0) {
            throw new LaoadsException(ErrorCode.GROUP_IN_USE, "Nhóm đang có chiến dịch, không thể xoá");
        }
        groupRepo.delete(g);
        return g;
    }

    private String groupName(long ws, String name, Long exceptId) {
        String n = Texts.trimToNull(name);
        if (n == null || n.length() > 255) {
            throw LaoadsException.validation(List.of(new FieldViolation("name", "error.validation.invalidValue",
                    "Giá trị không hợp lệ")));
        }
        if (groupRepo.countByName(ws, n.toLowerCase(Locale.ROOT), exceptId) > 0) {
            throw new LaoadsException(ErrorCode.GROUP_NAME_DUPLICATE, "Tên nhóm chiến dịch đã tồn tại trong workspace");
        }
        return n;
    }

    // ───────────── Chi phí ─────────────

    /** Đơn giá theo loại hành động từ bảng giá đang áp dụng (0 nếu chưa có bảng LOCKED). */
    @Transactional(readOnly = true)
    public Map<ActionType, BigDecimal> unitPrices() {
        Map<ActionType, BigDecimal> m = new EnumMap<>(ActionType.class);
        priceQuoteService.active().ifPresent(q -> {
            for (PriceQuoteItem i : q.getItems()) {
                m.put(i.getActionType(), i.getUnitPrice() == null ? BigDecimal.ZERO : i.getUnitPrice());
            }
        });
        return m;
    }

    /** Chi phí dự kiến = Σ nhóm nhận tin (target × tỷ lệ phân bổ) × Σ đơn giá hành động. */
    public static BigDecimal expectedCost(CampaignDefinition def, int target, Map<ActionType, BigDecimal> prices) {
        BigDecimal total = BigDecimal.ZERO;
        for (ActionGroupDef g : def.groups()) {
            if (g.isControl() || g.actions() == null) {
                continue;
            }
            BigDecimal perTarget = g.actions().stream()
                    .map(a -> prices.getOrDefault(a.type(), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal recipients = BigDecimal.valueOf(target).multiply(BigDecimal.valueOf(g.allocationPercent()))
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            total = total.add(recipients.multiply(perTarget));
        }
        return total.setScale(0, RoundingMode.HALF_UP);
    }

    // ───────────── nội bộ ─────────────

    private record Validated(Segment segment, List<String> testIsdns, int targetCount, String channels) {
    }

    private Validated validate(long ws, CampaignInput in, boolean strict) {
        List<FieldViolation> errors = new ArrayList<>();
        String name = Texts.trimToNull(in.name());
        if (name == null || name.length() > 255) {
            errors.add(new FieldViolation("name", "error.validation.invalidValue", "Giá trị không hợp lệ"));
        }
        if (in.mode() == null || in.type() == null) {
            errors.add(new FieldViolation("mode", "error.validation.invalidValue", "Giá trị không hợp lệ"));
        }
        if (in.language() != null && !LANGUAGES.contains(in.language())) {
            errors.add(new FieldViolation("language", "error.validation.invalidValue", "Giá trị không hợp lệ"));
        }
        if (in.groupId() != null && groupRepo.findByIdAndWorkspaceId(in.groupId(), ws).isEmpty()) {
            errors.add(new FieldViolation("groupId", "error.validation.invalidValue", "Giá trị không hợp lệ"));
        }
        Segment segment = null;
        if (in.segmentId() != null) {
            segment = segmentRepo.findByIdAndWorkspaceId(in.segmentId(), ws).orElse(null);
            if (segment == null) {
                errors.add(new FieldViolation("segmentId", "error.validation.invalidValue", "Giá trị không hợp lệ"));
            }
        }
        List<String> isdns = new ArrayList<>(new LinkedHashSet<>(normalizeIsdns(in.testIsdns(), errors)));

        CampaignDefinition def = in.definition();
        if (def == null || def.groups().isEmpty()) {
            errors.add(new FieldViolation("actionGroups", "error.validation.noAction", "Cần ít nhất một hành động"));
        } else {
            validateGroups(def, errors);
            if (strict) {
                validateSenders(ws, def, errors);
            }
            if (in.type() != Type.EVENT) {
                validateSchedule(def.schedule(), errors);
            }
        }
        if (strict && in.type() == Type.STATIC) {
            if (in.mode() == Mode.OFFICIAL) {
                if (segment == null) {
                    errors.add(new FieldViolation("segmentId", "error.validation.segmentRequired",
                            "Chiến dịch chính thức cần chọn tệp khách hàng"));
                } else if (!segment.isReady()) {
                    errors.add(new FieldViolation("segmentId", "error.validation.segmentNotReady",
                            "Tệp khách hàng chưa sẵn sàng"));
                }
            } else if (in.mode() == Mode.TEST && isdns.isEmpty() && (segment == null || !segment.isReady())) {
                errors.add(new FieldViolation("testIsdns", "error.validation.testListRequired",
                        "Chiến dịch thử nghiệm cần danh sách thuê bao thử hoặc tệp khách hàng"));
            }
        }
        if (!errors.isEmpty()) {
            throw LaoadsException.validation(errors);
        }
        int target = targetCount(in, segment, isdns);
        return new Validated(segment, isdns, target, channelsOf(def));
    }

    /**
     * Khi gửi duyệt: hành động SMS phải chọn đầu số; đầu số đã chọn phải tồn tại, đang bật, hỗ trợ kênh của hành động
     * và workspace được phép dùng (cấu hình ở trang Cấu hình gửi tin).
     */
    private void validateSenders(long ws, CampaignDefinition def, List<FieldViolation> errors) {
        Map<String, SenderNumber> senders = null;
        for (int gi = 0; gi < def.groups().size(); gi++) {
            ActionGroupDef g = def.groups().get(gi);
            if (g.isControl() || g.actions() == null) {
                continue;
            }
            for (int ai = 0; ai < g.actions().size(); ai++) {
                ActionDef a = g.actions().get(ai);
                if (a.type() == null) {
                    continue;
                }
                String path = "actionGroups[" + gi + "].actions[" + ai + "].senderId";
                String code = a.senderId() == null || a.senderId().isBlank() ? null
                        : a.senderId().trim().toUpperCase(Locale.ROOT);
                if (code == null) {
                    if (a.type() == ActionType.NOTIFICATION_SMS) {
                        errors.add(new FieldViolation(path, "error.validation.senderRequired",
                                "Hành động SMS cần chọn đầu số gửi"));
                    }
                    continue;
                }
                if (senders == null) {
                    senders = senderRepo.findAll().stream().collect(Collectors.toMap(
                            s -> s.getCode().toUpperCase(Locale.ROOT), s -> s, (x, y) -> x));
                }
                SenderNumber s = senders.get(code);
                if (s == null || !s.isEnabled() || !s.allowedFor(ws) || !s.supports(a.channel())) {
                    errors.add(new FieldViolation(path, "error.validation.senderUnavailable",
                            "Đầu số " + code + " không dùng được cho hành động này", code));
                }
            }
        }
    }

    private static void validateGroups(CampaignDefinition def, List<FieldViolation> errors) {
        int total = 0;
        boolean anyAction = false;
        for (int gi = 0; gi < def.groups().size(); gi++) {
            ActionGroupDef g = def.groups().get(gi);
            if (g.allocationPercent() < 0 || g.allocationPercent() > 100) {
                errors.add(new FieldViolation("actionGroups[" + gi + "].allocationPercent", "error.validation.range",
                        "Giá trị phải từ 0 đến 100", 0, 100));
            }
            total += g.allocationPercent();
            if (g.isControl() || g.actions() == null) {
                continue;
            }
            for (int ai = 0; ai < g.actions().size(); ai++) {
                ActionDef a = g.actions().get(ai);
                String path = "actionGroups[" + gi + "].actions[" + ai + "]";
                if (a.type() == null) {
                    errors.add(new FieldViolation(path + ".type", "error.validation.invalidValue", "Giá trị không hợp lệ"));
                    continue;
                }
                anyAction = true;
                if (a.delayMinutes() < 0 || a.delayMinutes() > 60 * 24 * 30) {
                    errors.add(new FieldViolation(path + ".delayMinutes", "error.validation.range",
                            "Giá trị phải từ 0 đến 43200", 0, 43200));
                }
                if (a.channel().isNotification() && Texts.trimToNull(a.content()) == null
                        && Texts.trimToNull(a.templateId()) == null) {
                    errors.add(new FieldViolation(path + ".content", "error.validation.contentRequired",
                            "Nội dung tin không được để trống"));
                }
            }
        }
        if (total != 100) {
            errors.add(new FieldViolation("actionGroups", "error.validation.allocationTotal",
                    "Tổng tỷ lệ phân bổ các nhóm phải bằng 100%", total));
        }
        if (!anyAction) {
            errors.add(new FieldViolation("actionGroups", "error.validation.noAction",
                    "Cần ít nhất một hành động trong nhóm nhận tin"));
        }
    }

    private static void validateSchedule(ScheduleDef s, List<FieldViolation> errors) {
        if (s == null || s.type() == null || !SCHEDULE_TYPES.contains(s.type())) {
            errors.add(new FieldViolation("schedule.type", "error.validation.invalidValue", "Giá trị không hợp lệ"));
            return;
        }
        if (!"NOW".equals(s.type()) && s.startAt() == null) {
            errors.add(new FieldViolation("schedule.startAt", "error.validation.startRequired", "Cần chọn thời gian bắt đầu"));
        }
        if (s.startAt() != null && s.endAt() != null && !s.endAt().isAfter(s.startAt())) {
            errors.add(new FieldViolation("schedule.endAt", "error.validation.endBeforeStart",
                    "Thời gian kết thúc phải sau thời gian bắt đầu"));
        }
        if ("RECURRING".equals(s.type()) && (s.every() == null || s.every() < 1 || s.unit() == null
                || !UNITS.contains(s.unit()) || s.sendTime() == null || !HH_MM.matcher(s.sendTime()).matches())) {
            errors.add(new FieldViolation("schedule", "error.validation.recurringRule",
                    "Lịch lặp lại cần chu kỳ và giờ gửi hợp lệ"));
        }
        if (s.silentHours() != null && s.silentHours().enabled()
                && (s.silentHours().from() == null || !HH_MM.matcher(s.silentHours().from()).matches()
                || s.silentHours().to() == null || !HH_MM.matcher(s.silentHours().to()).matches())) {
            errors.add(new FieldViolation("schedule.silentHours", "error.validation.timeFormat", "Giờ phải có dạng HH:mm"));
        }
    }

    private static List<String> normalizeIsdns(List<String> raw, List<FieldViolation> errors) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String r : raw) {
            if (r == null || r.isBlank()) {
                continue;
            }
            var n = Isdns.normalize(r);
            if (n.isPresent()) {
                out.add(n.get());
            } else {
                errors.add(new FieldViolation("testIsdns", "error.validation.isdnInvalid", "Số thuê bao không hợp lệ", r));
            }
        }
        if (out.size() > 200) {
            errors.add(new FieldViolation("testIsdns", "error.validation.range", "Giá trị phải từ 0 đến 200", 0, 200));
        }
        return out;
    }

    private static int targetCount(CampaignInput in, Segment segment, List<String> isdns) {
        if (in.type() == Type.EVENT) {
            return 0;
        }
        if (in.mode() == Mode.TEST) {
            if (!isdns.isEmpty()) {
                return isdns.size();
            }
            return segment == null ? 0 : Math.min(segment.getTotalUsers(), TEST_LIMIT);
        }
        return segment == null ? 0 : segment.getTotalUsers();
    }

    private static String channelsOf(CampaignDefinition def) {
        if (def == null) {
            return null;
        }
        Set<Channel> set = EnumSet.noneOf(Channel.class);
        def.groups().forEach(g -> {
            if (g.actions() != null) {
                g.actions().stream().filter(a -> a.type() != null).map(ActionDef::channel)
                        .filter(Channel::isNotification).forEach(set::add);
            }
        });
        return set.isEmpty() ? null : set.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private void apply(Campaign c, CampaignInput in, Validated v) {
        CampaignDefinition def = in.definition();
        c.setName(Texts.trimToNull(in.name()));
        c.setDescription(truncate(Texts.trimToNull(in.description()), 2000));
        c.setMode(in.mode());
        c.setType(in.type());
        c.setGroupId(in.groupId());
        c.setSegmentId(in.segmentId());
        c.setLanguage(in.language() == null ? "LO" : in.language());
        c.setTestIsdns(v.testIsdns().isEmpty() ? null : truncate(String.join(",", v.testIsdns()), 4000));
        c.setTargetCount(v.targetCount());
        c.setChannels(v.channels());
        ScheduleDef s = def.schedule();
        c.setStartAt(s == null ? null : s.startAt());
        c.setEndAt(s == null ? null : s.endAt());
        c.setBudgetMax(def.budget() == null ? null : def.budget().max());
        c.setExpectedCost(expectedCost(def, v.targetCount(), unitPrices()));
        try {
            c.setDefinitionJson(mapper.writeValueAsString(def));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private CampaignInput inputOf(Campaign c) {
        List<String> isdns = c.getTestIsdns() == null ? List.of() : Arrays.asList(c.getTestIsdns().split(","));
        return new CampaignInput(c.getName(), c.getDescription(), c.getMode(), c.getType(), c.getGroupId(),
                c.getSegmentId(), c.getLanguage(), isdns, definitionOf(c));
    }

    private Campaign transition(Campaign c, Status to, String actor, String action, String note) {
        c.setStatus(to);
        c.setUpdatedAt(Instant.now());
        c.setUpdatedBy(actor);
        historyRepo.save(CampaignHistory.of(c.getId(), actor, action, note));
        log.info("[CAMPAIGN] {} {} chiến dịch #{} → {}", actor, action, c.getId(), to);
        return c;
    }

    private static void requireStatus(Campaign c, Set<Status> allowed) {
        if (!allowed.contains(c.getStatus())) {
            throw new LaoadsException(ErrorCode.INVALID_TRANSITION, "Không thể thực hiện thao tác ở trạng thái hiện tại");
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static LaoadsException notFound(long id) {
        return LaoadsException.notFound("error.notFound.campaign", "Không tìm thấy chiến dịch", id);
    }

    private static LaoadsException groupNotFound(long id) {
        return LaoadsException.notFound("error.notFound.campaignGroup", "Không tìm thấy nhóm chiến dịch", id);
    }
}
