/*
 * Mock dashboard, chiến dịch, nhóm, phê duyệt, tệp KH, cài đặt gửi tin, API key — BE đã có (đợt 2–3),
 * nên chỉ nạp ở VITE_API_MODE=mock (chạy UI không cần BE). Shape phải khớp BE thật.
 */
import { http, HttpResponse } from 'msw'
import type {
  AvailableSender,
  CampaignDetail,
  CampaignInput,
  CampaignStatus,
  Channel,
  ChannelStat,
  ConnectionTestResult,
  NotificationSettings,
  SegmentCondition,
  SenderChannel,
  SenderNumber,
  SenderNumberInput,
  TimePoint,
} from '@/api/types'
import { CHANNELS, SENDER_CHANNELS } from '@/api/types'
import type { AudienceRequest, AudienceSource, Segment } from '@/api/types'
import { activeUnitPrice, audienceCatalog, audit, campaigns, campaignSeq, eventTemplates, groups, messageParams, notificationSettings, rand, refreshGroupCounts, segmentAttributes, segments, segmentSeq, senderPasswords, senders, senderSeq, users, workspaces } from '../db'
import { currentUser, fail, guard, includes, ok, paged, q, workspaceId } from '../utils'
import { validateAudience } from '@/features/campaign/audience'

const B = '*/api/v1'
const apiKeys: Record<number, { configured: boolean; hint: string | null; rotatedAt: string | null; key: string | null }> = {}
const wsOf = (r: Request) => workspaceId(r) ?? 1
const actor = (r: Request) => currentUser(r)?.username ?? 'unknown'

// ───── Audience API (OCS / BCCS3): kiểm tra + mô phỏng nạp nền giống BE ─────
const AUD_ERR: Record<string, string> = {
  table: 'Chưa chọn bảng',
  phone: 'Cần đúng một cột số thuê bao (PHONE)',
  column: 'Chưa chọn cột',
  operator: 'Toán tử không áp dụng cho kiểu dữ liệu của cột',
  value: 'Giá trị không đúng kiểu dữ liệu',
}
const isAudienceSource = (v: unknown): v is AudienceSource => v === 'OCS' || v === 'BCCS3'
/** Lỗi cục bộ (400, field path request.*) — cùng luật với BE. */
function audienceErrors(req: AudienceRequest): FieldErr[] {
  return Object.entries(validateAudience(req)).map(([field, code]) => ({ field, message: AUD_ERR[code] ?? 'Giá trị không hợp lệ' }))
}
/** Service đối chiếu metadata thật: bảng / cột / kiểu dữ liệu phải khớp catalog (200, valid=false nếu sai). */
function audienceMismatch(source: AudienceSource, req: AudienceRequest): string | null {
  const cols = audienceCatalog[source][req.tableName]
  if (!cols) return `Bảng ${req.tableName} không tồn tại`
  for (const c of [...req.selectedColumns, ...req.conditions]) {
    const col = cols.find((x) => x.name === c.columnName)
    if (!col) return `Cột ${c.columnName} không có trong bảng ${req.tableName}`
    if (col.dataType !== c.dataType) return `Cột ${c.columnName} có kiểu ${col.dataType}, không phải ${c.dataType}`
  }
  return null
}
/** Mô phỏng job nền stream NDJSON: totalUsers / importedRows tăng dần rồi READY. */
function simulateAudienceBuild(s: Segment, startFrom = 0) {
  const target = 20_000 + Math.round(rand() * 140_000)
  let rows = startFrom
  const step = Math.ceil((target - startFrom) / 8)
  const timer = setInterval(() => {
    rows = Math.min(target, rows + step)
    s.importedRows = rows
    s.totalUsers = Math.round(rows * 0.985) // bỏ số trùng / không hợp lệ
    if (rows >= target) {
      clearInterval(timer)
      s.status = 'READY'
      s.errorMessage = null
      s.updatedAt = new Date().toISOString()
    }
  }, 800)
}

// ───── Đầu số: kiểm tra + ghi giống BE ─────
type FieldErr = { field: string; message: string }
const invalid = (errors: FieldErr[]) =>
  HttpResponse.json({ success: false, data: null, message: 'Dữ liệu nhập không hợp lệ', statusCode: 'VALIDATION_ERROR', errors }, { status: 400 })
const HH_MM = /^([01]\d|2[0-3]):[0-5]\d$/
const blank = (v: string | null | undefined) => !v || !v.trim()

function validateSender(b: SenderNumberInput, hadPassword: boolean): FieldErr[] {
  const e: FieldErr[] = []
  const bad = (field: string, message = 'Giá trị không hợp lệ') => e.push({ field, message })
  if (!/^[A-Za-z0-9_]{2,32}$/.test((b.code ?? '').trim())) bad('code')
  if (b.kind !== 'SHORTCODE' && b.kind !== 'BRANDNAME') bad('kind')
  if ((b.description ?? '').length > 255) bad('description', 'Tối đa 255 ký tự')
  const ch = b.channels ?? []
  if (!ch.length || ch.some((c) => c !== 'SMS' && c !== 'USSD')) bad('channels', 'Chọn ít nhất một kênh')
  else if (b.kind === 'BRANDNAME' && ch.includes('USSD')) bad('channels', 'Brandname chỉ dùng cho SMS')
  if (!b.allWorkspaces && !(b.workspaceIds ?? []).length) bad('workspaceIds', 'Chọn ít nhất một workspace')
  const g = b.gateway
  const pwd = g.password ?? ''
  if (g.protocol === 'SMPP') {
    if (blank(g.host) || (g.host ?? '').length > 255) bad('gateway.host')
    if (g.port == null || g.port < 1 || g.port > 65535) bad('gateway.port', 'Giá trị phải từ 1 đến 65535')
    if (blank(g.username) || (g.username ?? '').length > 15) bad('gateway.username', 'system_id bắt buộc, tối đa 15 ký tự')
    if (pwd.length > 8) bad('gateway.password', 'Mật khẩu SMPP tối đa 8 ký tự')
    if (!pwd && (!hadPassword || g.clearPassword)) bad('gateway.password', 'Cần mật khẩu SMPP')
    if ((g.systemType ?? '').length > 12) bad('gateway.systemType', 'Tối đa 12 ký tự')
  } else if (g.protocol === 'HTTP') {
    if (!/^https?:\/\/\S+$/.test(g.url ?? '') || (g.url ?? '').length > 1000) bad('gateway.url', 'URL phải bắt đầu bằng http:// hoặc https://')
    if ((g.username ?? '').length > 128) bad('gateway.username', 'Tối đa 128 ký tự')
    if (pwd.length > 256) bad('gateway.password', 'Tối đa 256 ký tự')
  } else if (g.protocol !== 'NONE') bad('gateway.protocol')
  if (g.protocol !== 'NONE' && g.tps != null && (g.tps < 1 || g.tps > 10000)) bad('gateway.tps', 'Giá trị phải từ 1 đến 10000')
  const p = b.policy
  if (p.retryMaxAttempts != null && (p.retryMaxAttempts < 0 || p.retryMaxAttempts > 10)) bad('policy.retryMaxAttempts', 'Giá trị phải từ 0 đến 10')
  if (p.retryIntervalMinutes != null && (p.retryIntervalMinutes < 1 || p.retryIntervalMinutes > 1440)) bad('policy.retryIntervalMinutes', 'Giá trị phải từ 1 đến 1440')
  if (p.silentHoursEnabled === true) {
    if (!HH_MM.test(p.silentFrom ?? '')) bad('policy.silentFrom', 'Giờ phải có dạng HH:mm')
    if (!HH_MM.test(p.silentTo ?? '')) bad('policy.silentTo', 'Giờ phải có dạng HH:mm')
  }
  if (p.dailyLimit != null && (p.dailyLimit < 1 || p.dailyLimit > 100_000_000)) bad('policy.dailyLimit', 'Giá trị phải từ 1 đến 100000000')
  return e
}

/** Ghi input đã hợp lệ vào bản ghi mock (mật khẩu lưu riêng ở senderPasswords). */
function applySender(s: SenderNumber, b: SenderNumberInput) {
  s.kind = b.kind
  s.description = blank(b.description) ? null : b.description!.trim()
  s.enabled = b.enabled
  s.channels = SENDER_CHANNELS.filter((c) => b.channels.includes(c))
  s.allWorkspaces = b.allWorkspaces
  s.workspaceIds = b.allWorkspaces ? [] : [...new Set(b.workspaceIds)]
  const g = b.gateway
  if (g.protocol === 'NONE' || g.clearPassword) delete senderPasswords[s.id]
  if (g.protocol !== 'NONE' && g.password) senderPasswords[s.id] = g.password
  s.gateway = {
    protocol: g.protocol,
    host: g.protocol === 'SMPP' ? g.host?.trim() ?? null : null,
    port: g.protocol === 'SMPP' ? g.port : null,
    systemType: g.protocol === 'SMPP' && !blank(g.systemType) ? g.systemType!.trim() : null,
    url: g.protocol === 'HTTP' ? g.url?.trim() ?? null : null,
    username: g.protocol !== 'NONE' && !blank(g.username) ? g.username!.trim() : null,
    hasPassword: s.id in senderPasswords,
    tps: g.protocol !== 'NONE' ? g.tps : null,
    lastTest: g.protocol === s.gateway.protocol ? s.gateway.lastTest : null,
  }
  const p = b.policy
  s.policy = {
    retryMaxAttempts: p.retryMaxAttempts,
    retryIntervalMinutes: p.retryIntervalMinutes,
    silentHoursEnabled: p.silentHoursEnabled,
    silentFrom: p.silentHoursEnabled === true ? p.silentFrom : null,
    silentTo: p.silentHoursEnabled === true ? p.silentTo : null,
    dailyLimit: p.dailyLimit,
  }
}

// Chuỗi thời gian ổn định theo seed riêng cho mỗi campaign
function series(days: number, base: number, seedN: number, end = '2026-11-15'): TimePoint[] {
  let s = seedN * 9301
  const r = () => ((s = (s * 9301 + 49297) % 233280) / 233280)
  const out: TimePoint[] = []
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(Date.parse(end + 'T00:00:00+07:00') - i * 86400_000)
    const wave = 0.75 + 0.35 * Math.sin((days - i) / 3.2) + r() * 0.25
    const sent = Math.round(base * wave)
    const opens = Math.round(sent * (0.22 + r() * 0.12))
    const clicks = Math.round(opens * (0.28 + r() * 0.1))
    out.push({ date: d.toISOString().slice(0, 10), sent, opens, clicks, conversions: Math.round(clicks * (0.18 + r() * 0.1)) })
  }
  return out
}

function channelStats(channels: Channel[], total: number): ChannelStat[] {
  const share: Record<Channel, number> = { SMS: 0.52, WHATSAPP: 0.28, EMAIL: 0.12, USSD: 0.08 }
  const deliv: Record<Channel, number> = { SMS: 0.96, WHATSAPP: 0.83, EMAIL: 0.91, USSD: 0.88 }
  const open: Record<Channel, number> = { SMS: 0.0, WHATSAPP: 0.64, EMAIL: 0.27, USSD: 0.41 }
  return channels.map((c) => {
    const sent = Math.round(total * share[c])
    const delivered = Math.round(sent * deliv[c])
    const opened = Math.round(delivered * open[c])
    const clicked = Math.round(delivered * (c === 'EMAIL' ? 0.06 : c === 'WHATSAPP' ? 0.11 : 0.04))
    return { channel: c, sent, failed: Math.round(sent * (1 - deliv[c]) * 0.4), delivered, opened, clicked, converted: Math.round(clicked * 0.32 + delivered * 0.01) }
  })
}

const listOf = (r: Request) => campaigns.filter((c) => c.workspaceId === wsOf(r))
/** Giống BE CampaignRepository.countByName: trùng tên trong workspace, không phân biệt hoa/thường. */
const nameTaken = (r: Request, name: string, exceptId?: number) =>
  listOf(r).some((c) => c.id !== exceptId && c.name.trim().toLowerCase() === name.trim().toLowerCase())
const summary = ({ actionGroups: _a, schedule: _s, goal: _g, budget: _b, history: _h, testIsdns: _t, description: _d, language: _l, rejectReason: _r, ...rest }: CampaignDetail) => {
  void [_a, _s, _g, _b, _h, _t, _d, _l, _r]
  return rest
}

function expectedCostOf(c: Pick<CampaignDetail, 'actionGroups' | 'targetCount'>) {
  return Math.round(
    c.actionGroups.reduce((sum, g) => sum + (g.isControl ? 0 : (c.targetCount * g.allocationPercent) / 100) * g.actions.reduce((s, a) => s + activeUnitPrice(a.type), 0), 0),
  )
}

function transition(c: CampaignDetail, to: CampaignStatus, who: string, action: string, note?: string) {
  c.status = to
  c.history.push({ at: new Date().toISOString(), actor: who, action, note: note ?? null })
  refreshGroupCounts()
}

export const campaignHandlers = [
  // ───── Dashboard ─────
  http.get(`${B}/dashboard/summary`, ({ request }) => {
    const g = guard(request, 'REPORT_VIEW')
    if (g) return g
    const list = listOf(request)
    const running = list.filter((c) => c.status === 'RUNNING').length
    const pending = list.filter((c) => c.status === 'PENDING_APPROVAL').length
    const reached = list.filter((c) => ['RUNNING', 'COMPLETED', 'PAUSED'].includes(c.status)).reduce((s, c) => s + Math.round(c.targetCount * c.sentRate), 0)
    const m = (total: number, prev: number | null) => ({ total, previousTotal: prev, growth: prev ? ((total - prev) / prev) * 100 : null })
    return ok({ runningCampaigns: m(running, 3), pendingApprovals: m(pending, 3), totalCampaigns: m(list.length, 11), reachedCustomers: m(reached, Math.round(reached * 0.82)), periodDays: 7 })
  }),
  http.get(`${B}/dashboard/trend`, ({ request }) => {
    const g = guard(request, 'REPORT_VIEW')
    if (g) return g
    const days = Number(new URL(request.url).searchParams.get('days') ?? 30)
    return ok(series(days, 42_000 * (wsOf(request) === 1 ? 1 : 0.08), 7 + wsOf(request)))
  }),
  http.get(`${B}/dashboard/channels`, ({ request }) => {
    const g = guard(request, 'REPORT_VIEW')
    if (g) return g
    return ok(channelStats(CHANNELS, wsOf(request) === 1 ? 1_260_000 : 92_000))
  }),

  // ───── Nhóm chiến dịch ─────
  http.get(`${B}/campaign-groups`, ({ request }) => guard(request, 'CAMPAIGN_VIEW') ?? ok(groups.filter((g) => g.workspaceId === wsOf(request)))),

  // ───── Chiến dịch ─────
  http.get(`${B}/campaigns`, ({ request }) => {
    const g = guard(request, 'CAMPAIGN_VIEW')
    if (g) return g
    const url = new URL(request.url)
    const list = listOf(request)
      .filter(
        (c) =>
          (includes(c.name, q(url, 'query')) || includes(c.code, q(url, 'query'))) &&
          (!q(url, 'status') || c.status === q(url, 'status')) &&
          (!q(url, 'mode') || c.mode === q(url, 'mode')) &&
          (!q(url, 'groupId') || c.groupId === Number(q(url, 'groupId'))),
      )
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(summary)
    return paged(list, url)
  }),
  http.post(`${B}/campaigns/test-send`, async ({ request }) => {
    const g = guard(request, 'CAMPAIGN_CREATE')
    if (g) return g
    const { isdns } = (await request.json()) as { isdns: string[] }
    return ok(
      isdns.flatMap((isdn) =>
        (['SMS', 'WHATSAPP'] as const).map((channel) => {
          const okSend = !isdn.endsWith('0') || channel === 'SMS'
          return { isdn, channel, status: okSend ? 'SENT' : 'FAILED', detail: okSend ? 'Đã gửi tới gateway' : 'Thuê bao không có tài khoản WhatsApp' }
        }),
      ),
    )
  }),
  http.get(`${B}/campaigns/:id`, ({ request, params }) => {
    const g = guard(request, 'CAMPAIGN_VIEW')
    if (g) return g
    const c = listOf(request).find((x) => x.id === Number(params.id))
    return c ? ok(c) : fail(404, 'NOT_FOUND', 'Không tìm thấy chiến dịch')
  }),
  http.get(`${B}/campaigns/:id/report`, ({ request, params }) => {
    const g = guard(request, 'REPORT_VIEW')
    if (g) return g
    const c = listOf(request).find((x) => x.id === Number(params.id))
    if (!c) return fail(404, 'NOT_FOUND', 'Không tìm thấy chiến dịch')
    const started = ['RUNNING', 'PAUSED', 'COMPLETED'].includes(c.status)
    const url = new URL(request.url)
    const bucket = url.searchParams.get('bucket') ?? 'DAY'
    // khoảng ngày (SRS 3.2.8.1): mock sinh số mốc theo khoảng, kết thúc ở ngày "to"
    const to = url.searchParams.get('to') ?? '2026-11-15'
    const from = url.searchParams.get('from')
    const span = from ? Math.max(1, Math.round((Date.parse(to) - Date.parse(from)) / 86400_000) + 1) : 14
    const days = Math.min(60, bucket === 'MONTH' ? Math.ceil(span / 30) : bucket === 'WEEK' ? Math.ceil(span / 7) : span)
    const scale = (bucket === 'DAY' ? 1 : bucket === 'WEEK' ? 5 : 18) * (url.searchParams.get('channel') ? 0.35 : 1)
    const ts = started ? series(days, Math.max(400, (c.targetCount / 14) * scale), c.id, to) : []
    const target = c.targetCount
    const sent = Math.round(target * c.sentRate * 0.9)
    const delivered = Math.round(sent * 0.93)
    const opens = Math.round(delivered * c.openRate)
    const clicks = Math.round(opens * 0.31)
    const conversions = Math.round(target * c.conversionRate * 0.9)
    return ok({
      totals: { target, sent, delivered, failed: Math.round(sent * 0.03), opens, clicks, conversions, spent: started ? Math.round(c.expectedCost * 0.62) : 0 },
      timeseries: ts,
      channels: started ? channelStats(c.channels, sent) : [],
      groups: c.actionGroups.map((g) => ({
        groupName: g.name,
        isControl: g.isControl,
        target: Math.round((target * g.allocationPercent) / 100),
        delivered: g.isControl ? 0 : Math.round(((target * g.allocationPercent) / 100) * c.sentRate),
        sentRate: g.isControl ? 0 : c.sentRate,
        opens: g.isControl ? 0 : Math.round(((target * g.allocationPercent) / 100) * c.sentRate * c.openRate),
        openRate: g.isControl ? 0 : c.openRate,
        conversions: Math.round(((target * g.allocationPercent) / 100) * (g.isControl ? c.conversionRate * 0.35 : c.conversionRate)),
        conversionRate: g.isControl ? c.conversionRate * 0.35 : c.conversionRate,
        uplift: g.isControl ? null : c.uplift,
      })),
    })
  }),
  http.post(`${B}/campaigns`, async ({ request }) => {
    const g = guard(request, 'CAMPAIGN_CREATE')
    if (g) return g
    const body = (await request.json()) as CampaignInput
    if (nameTaken(request, body.name)) return fail(409, 'CAMPAIGN_NAME_DUPLICATE', 'Tên chiến dịch đã tồn tại trong workspace, vui lòng đặt tên khác')
    const submit = new URL(request.url).searchParams.get('submit') === 'true'
    const seg = segments.find((s) => s.id === body.segmentId)
    const grp = groups.find((x) => x.id === body.groupId)
    const id = ++campaignSeq.v
    const c: (typeof campaigns)[number] = {
      ...body,
      workspaceId: wsOf(request),
      id,
      code: `CP${2600 + id}`,
      status: submit ? 'PENDING_APPROVAL' : 'DRAFT',
      groupName: grp?.name ?? null,
      segmentName: seg?.name ?? null,
      targetCount: body.mode === 'TEST' ? Math.min(seg?.totalUsers ?? 0, 1000) : (seg?.totalUsers ?? 0),
      createdAt: new Date().toISOString(),
      createdBy: actor(request),
      expectedCost: 0,
      sentRate: 0,
      openRate: 0,
      conversionRate: 0,
      uplift: null,
      rejectReason: null,
      history: [{ at: new Date().toISOString(), actor: actor(request), action: 'CREATE' }, ...(submit ? [{ at: new Date().toISOString(), actor: actor(request), action: 'SUBMIT' }] : [])],
    }
    c.expectedCost = expectedCostOf(c)
    campaigns.unshift(c)
    refreshGroupCounts()
    audit(actor(request), submit ? 'SUBMIT' : 'CREATE', 'CAMPAIGN', String(id), `${submit ? 'Tạo & gửi duyệt' : 'Tạo nháp'} chiến dịch "${c.name}"`, c.workspaceId)
    return ok(c, 201)
  }),
  http.put(`${B}/campaigns/:id`, async ({ request, params }) => {
    const g = guard(request, 'CAMPAIGN_UPDATE')
    if (g) return g
    const c = listOf(request).find((x) => x.id === Number(params.id))
    if (!c) return fail(404, 'NOT_FOUND', 'Không tìm thấy chiến dịch')
    if (!['DRAFT', 'REJECTED'].includes(c.status)) return fail(409, 'CAMPAIGN_LOCKED', 'Chỉ sửa được chiến dịch Nháp hoặc Bị từ chối')
    const body = (await request.json()) as CampaignInput
    if (nameTaken(request, body.name, c.id)) return fail(409, 'CAMPAIGN_NAME_DUPLICATE', 'Tên chiến dịch đã tồn tại trong workspace, vui lòng đặt tên khác')
    Object.assign(c, body)
    c.expectedCost = expectedCostOf(c)
    if (new URL(request.url).searchParams.get('submit') === 'true') transition(c, 'PENDING_APPROVAL', actor(request), 'SUBMIT')
    return ok(c)
  }),
  http.delete(`${B}/campaigns/:id`, ({ request, params }) => {
    const g = guard(request, 'CAMPAIGN_DELETE')
    if (g) return g
    const idx = campaigns.findIndex((x) => x.id === Number(params.id))
    if (idx < 0) return fail(404, 'NOT_FOUND', 'Không tìm thấy chiến dịch')
    if (!['DRAFT', 'REJECTED', 'CANCELLED'].includes(campaigns[idx].status)) return fail(409, 'CAMPAIGN_LOCKED', 'Chỉ xoá được chiến dịch Nháp / Bị từ chối / Đã huỷ')
    campaigns.splice(idx, 1)
    refreshGroupCounts()
    return ok(null)
  }),
  http.post(`${B}/campaigns/:id/:cmd`, ({ request, params }) => {
    const cmd = String(params.cmd)
    const need = cmd === 'submit' ? 'CAMPAIGN_SUBMIT' : cmd === 'duplicate' ? 'CAMPAIGN_CREATE' : 'CAMPAIGN_UPDATE'
    const g = guard(request, need)
    if (g) return g
    const c = listOf(request).find((x) => x.id === Number(params.id))
    if (!c) return fail(404, 'NOT_FOUND', 'Không tìm thấy chiến dịch')
    const who = actor(request)
    const allowed: Record<string, CampaignStatus[]> = { submit: ['DRAFT', 'REJECTED'], pause: ['RUNNING'], resume: ['PAUSED'], cancel: ['SCHEDULED', 'RUNNING', 'PAUSED', 'PENDING_APPROVAL'], duplicate: [] }
    if (cmd !== 'duplicate' && !allowed[cmd]?.includes(c.status)) return fail(409, 'INVALID_TRANSITION', 'Không thể thực hiện thao tác ở trạng thái hiện tại')
    if (cmd === 'duplicate') {
      const id = ++campaignSeq.v
      const copy = { ...structuredClone(c), id, code: `CP${2600 + id}`, name: `${c.name} (bản sao)`, status: 'DRAFT' as const, createdAt: new Date().toISOString(), createdBy: who, sentRate: 0, openRate: 0, conversionRate: 0, uplift: null, history: [{ at: new Date().toISOString(), actor: who, action: 'CREATE' }] }
      campaigns.unshift(copy)
      refreshGroupCounts()
      return ok(copy, 201)
    }
    const map: Record<string, [CampaignStatus, string]> = { submit: ['PENDING_APPROVAL', 'SUBMIT'], pause: ['PAUSED', 'PAUSE'], resume: ['RUNNING', 'RESUME'], cancel: ['CANCELLED', 'CANCEL'] }
    transition(c, map[cmd][0], who, map[cmd][1])
    audit(who, map[cmd][1], 'CAMPAIGN', String(c.id), `${map[cmd][1]} chiến dịch "${c.name}"`, c.workspaceId)
    return ok(c)
  }),

  // ───── Phê duyệt ─────
  http.get(`${B}/campaign-approvals`, ({ request }) => {
    const g = guard(request, 'CAMPAIGN_APPROVE')
    if (g) return g
    const url = new URL(request.url)
    const list = listOf(request)
      .filter((c) => c.status === 'PENDING_APPROVAL' && includes(c.name, q(url, 'query')) && (!q(url, 'mode') || c.mode === q(url, 'mode')))
      .map(summary)
    return paged(list, url)
  }),
  http.post(`${B}/campaign-approvals/:id/:decision`, async ({ request, params }) => {
    const g = guard(request, 'CAMPAIGN_APPROVE')
    if (g) return g
    const c = listOf(request).find((x) => x.id === Number(params.id))
    if (!c) return fail(404, 'NOT_FOUND', 'Không tìm thấy chiến dịch')
    if (c.status !== 'PENDING_APPROVAL') return fail(409, 'INVALID_TRANSITION', 'Chiến dịch không ở trạng thái chờ duyệt')
    const { reason } = ((await request.json().catch(() => ({}))) ?? {}) as { reason?: string }
    const who = actor(request)
    if (params.decision === 'approve') {
      const startsNow = !c.startAt || c.startAt <= new Date().toISOString() || c.schedule.type === 'NOW'
      transition(c, startsNow ? 'RUNNING' : 'SCHEDULED', who, 'APPROVE')
      audit(who, 'APPROVE', 'CAMPAIGN', String(c.id), `Duyệt chiến dịch "${c.name}"`, c.workspaceId)
    } else {
      if (!reason?.trim()) return fail(400, 'VALIDATION_ERROR', 'Vui lòng nhập lý do từ chối')
      c.rejectReason = reason
      transition(c, 'REJECTED', who, 'REJECT', reason)
      audit(who, 'REJECT', 'CAMPAIGN', String(c.id), `Từ chối chiến dịch "${c.name}"`, c.workspaceId)
    }
    return ok(c)
  }),

  // ───── Tệp khách hàng ─────
  http.get(`${B}/segment-attributes`, () => ok(segmentAttributes)),
  http.get(`${B}/message-params`, () => ok(messageParams)),
  http.get(`${B}/event-templates`, () => ok(eventTemplates)),

  // ───── Audience API (OCS / BCCS3) ─────
  http.get(`${B}/segments/audience/sources`, ({ request }) => {
    const g = guard(request, 'CAMPAIGN_VIEW')
    if (g) return g
    return ok([
      { source: 'OCS', configured: true, mode: 'simulated' },
      { source: 'BCCS3', configured: true, mode: 'simulated' },
    ])
  }),
  http.get(`${B}/segments/audience/:source/tables`, ({ request, params }) => {
    const g = guard(request, 'SEGMENT_MANAGE')
    if (g) return g
    if (!isAudienceSource(params.source)) return fail(400, 'AUDIENCE_NOT_CONFIGURED', 'Nguồn dữ liệu chưa được cấu hình')
    return ok(Object.keys(audienceCatalog[params.source]))
  }),
  http.get(`${B}/segments/audience/:source/tables/:table/columns`, ({ request, params }) => {
    const g = guard(request, 'SEGMENT_MANAGE')
    if (g) return g
    if (!isAudienceSource(params.source)) return fail(400, 'AUDIENCE_NOT_CONFIGURED', 'Nguồn dữ liệu chưa được cấu hình')
    const cols = audienceCatalog[params.source][String(params.table)]
    return cols ? ok(cols) : fail(503, 'AUDIENCE_UNAVAILABLE', `Bảng ${String(params.table)} không tồn tại ở nguồn ${params.source}`)
  }),
  http.post(`${B}/segments/audience/:source/verify`, async ({ request, params }) => {
    const g = guard(request, 'SEGMENT_MANAGE')
    if (g) return g
    if (!isAudienceSource(params.source)) return fail(400, 'AUDIENCE_NOT_CONFIGURED', 'Nguồn dữ liệu chưa được cấu hình')
    const req = (await request.json()) as AudienceRequest
    const errs = audienceErrors(req)
    if (errs.length) return invalid(errs)
    const mismatch = audienceMismatch(params.source, req)
    return ok({ valid: !mismatch, message: mismatch })
  }),
  http.post(`${B}/segments/audience`, async ({ request }) => {
    const g = guard(request, 'SEGMENT_MANAGE')
    if (g) return g
    const b = (await request.json()) as { name: string; description?: string | null; source: AudienceSource; request: AudienceRequest }
    if (!isAudienceSource(b.source)) return fail(400, 'AUDIENCE_NOT_CONFIGURED', 'Nguồn dữ liệu chưa được cấu hình')
    const errs = audienceErrors(b.request)
    if (errs.length) return invalid(errs)
    const mismatch = audienceMismatch(b.source, b.request)
    if (mismatch) return invalid([{ field: 'request', message: mismatch }])
    const name = (b.name ?? '').trim()
    if (!name) return invalid([{ field: 'name', message: 'Không được để trống' }])
    if (segments.some((x) => x.workspaceId === wsOf(request) && x.name.trim().toLowerCase() === name.toLowerCase())) return fail(409, 'SEGMENT_NAME_DUPLICATE', 'Tên tệp khách hàng đã tồn tại trong workspace')
    const s = {
      workspaceId: wsOf(request),
      id: ++segmentSeq.v,
      name,
      description: b.description?.trim() || null,
      type: 'AUDIENCE' as const,
      source: b.source,
      status: 'BUILDING' as Segment['status'],
      totalUsers: 0,
      fileName: null,
      conditions: [],
      audience: b.request,
      importedRows: 0,
      errorMessage: null as string | null,
      referencedBy: 0,
      createdAt: new Date().toISOString(),
      createdBy: actor(request),
      updatedAt: null as string | null,
    }
    segments.unshift(s)
    simulateAudienceBuild(s)
    audit(actor(request), 'CREATE', 'SEGMENT', String(s.id), `Tạo tệp KH "${s.name}" từ ${b.source} (${b.request.tableName})`, s.workspaceId)
    return ok(s)
  }),
  http.post(`${B}/segments/:id/retry`, ({ request, params }) => {
    const g = guard(request, 'SEGMENT_MANAGE')
    if (g) return g
    const s = segments.find((x) => x.id === Number(params.id) && x.workspaceId === wsOf(request))
    if (!s) return fail(404, 'NOT_FOUND', 'Không tìm thấy tệp khách hàng')
    if (s.status !== 'FAILED' || (s.type !== 'AUDIENCE' && s.type !== 'QUERY')) return fail(409, 'INVALID_TRANSITION', 'Không thể thực hiện thao tác ở trạng thái hiện tại')
    s.status = 'BUILDING'
    s.errorMessage = null
    if (s.type === 'AUDIENCE') {
      // OCS chạy tiếp từ offset đã nạp; BCCS3 không có offset → nạp lại từ đầu
      const from = s.source === 'OCS' ? (s.importedRows ?? 0) : 0
      if (s.source !== 'OCS') {
        s.importedRows = 0
        s.totalUsers = 0
      }
      simulateAudienceBuild(s, from)
    } else {
      setTimeout(() => {
        s.status = 'READY'
        s.totalUsers = 48_000 + Math.round(rand() * 160_000)
      }, 4000)
    }
    audit(actor(request), 'UPDATE', 'SEGMENT', String(s.id), `Build lại tệp KH "${s.name}"`, s.workspaceId)
    return ok(s)
  }),
  http.get(`${B}/segments`, ({ request }) => {
    const g = guard(request, 'CAMPAIGN_VIEW')
    if (g) return g
    const url = new URL(request.url)
    const list = segments
      .filter((s) => s.workspaceId === wsOf(request) && includes(s.name, q(url, 'query')) && (!q(url, 'type') || s.type === q(url, 'type')) && (!q(url, 'status') || s.status === q(url, 'status')))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    return paged(list, url)
  }),
  http.post(`${B}/segments/estimate`, async ({ request }) => {
    const { conditions } = (await request.json()) as { conditions: SegmentCondition[] }
    const base = 1_840_000
    const est = conditions.reduce((n, c, i) => Math.round(n * (c.join === 'OR' && i > 0 ? 1.35 : 0.22 + ((c.field.length * 7 + c.value.length * 13) % 50) / 100)), base)
    return ok({ estimatedCount: Math.max(0, Math.min(base, est)) })
  }),
  http.post(`${B}/segments/bulk-delete`, async ({ request }) => {
    const g = guard(request, 'SEGMENT_MANAGE')
    if (g) return g
    const { ids } = (await request.json()) as { ids: number[] }
    const failed: { id: number; reason: string }[] = []
    let deleted = 0
    for (const id of ids) {
      const idx = segments.findIndex((s) => s.id === id)
      if (idx < 0) failed.push({ id, reason: 'Không tồn tại' })
      else if (segments[idx].referencedBy > 0) failed.push({ id, reason: 'Đang được chiến dịch sử dụng' })
      else {
        segments.splice(idx, 1)
        deleted++
      }
    }
    return ok({ deleted, failed })
  }),
  http.get(`${B}/segments/:id`, ({ request, params }) => {
    const g = guard(request, 'CAMPAIGN_VIEW')
    if (g) return g
    const s = segments.find((x) => x.id === Number(params.id) && x.workspaceId === wsOf(request))
    return s ? ok(s) : fail(404, 'NOT_FOUND', 'Không tìm thấy tệp khách hàng')
  }),
  http.get(`${B}/segments/:id/members`, ({ request, params }) => {
    const s = segments.find((x) => x.id === Number(params.id))
    if (!s) return fail(404, 'NOT_FOUND', 'Không tìm thấy tệp khách hàng')
    if (s.status !== 'READY') return fail(409, 'SEGMENT_VERSION0002', 'Phiên bản tệp khách hàng chưa sẵn sàng')
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 0)
    const all = Array.from({ length: Math.min(s.totalUsers, 120) }, (_, i) => {
      const n = (s.id * 7919 + i * 104729) % 9_000_000
      return {
        recipientKey: (s.id * 1e6 + i).toString(16).padStart(12, '0'),
        phone: `85620${String(1_000_000 + n).slice(0, 3)}xxxxx`,
        attributes: { province: ['VTE', 'LPB', 'SVK', 'CPS'][i % 4], package: ['D5K', 'M50', 'NONE'][i % 3], g_volume_gb: Number((rand() * 12).toFixed(1)) },
      }
    })
    void page
    return paged(all, url)
  }),
  http.post(`${B}/segments`, async ({ request }) => {
    const g = guard(request, 'SEGMENT_MANAGE')
    if (g) return g
    const b = (await request.json()) as { name: string; description?: string; type: 'QUERY' | 'FILE'; conditions?: SegmentCondition[]; fileName?: string; rowCount?: number }
    if (segments.some((s) => s.workspaceId === wsOf(request) && s.name.trim().toLowerCase() === b.name.trim().toLowerCase())) return fail(409, 'SEGMENT_NAME_DUPLICATE', 'Tên tệp khách hàng đã tồn tại trong workspace')
    const s = {
      workspaceId: wsOf(request),
      id: ++segmentSeq.v,
      name: b.name,
      description: b.description ?? null,
      type: b.type,
      source: b.type === 'FILE' ? ('FILE' as const) : ('OCS' as const),
      status: 'BUILDING' as const,
      totalUsers: b.type === 'FILE' ? (b.rowCount ?? 0) : 0,
      fileName: b.fileName ?? null,
      conditions: b.conditions ?? [],
      referencedBy: 0,
      createdAt: new Date().toISOString(),
      createdBy: actor(request),
      updatedAt: null,
    }
    segments.unshift(s)
    // Giả lập job build bất đồng bộ
    setTimeout(() => {
      s.status = 'READY' as never
      if (s.type === 'QUERY') s.totalUsers = 48_000 + Math.round(rand() * 160_000)
    }, 4000)
    audit(actor(request), b.type === 'FILE' ? 'IMPORT' : 'CREATE', 'SEGMENT', String(s.id), `Tạo tệp KH "${s.name}"`, s.workspaceId)
    return ok(s, 201)
  }),

  // ───── Cài đặt gửi tin: đầu số (giống BE — danh sách/chi tiết/ghi chỉ Super Admin) ─────
  // `available` phải đăng ký trước `:id`
  http.get(`${B}/settings/sender-numbers/available`, ({ request }) => {
    const g = guard(request, 'CAMPAIGN_VIEW')
    if (g) return g
    const ws = wsOf(request)
    const channel = new URL(request.url).searchParams.get('channel') as SenderChannel | null
    const list: AvailableSender[] = senders
      .filter((s) => s.enabled && (s.allWorkspaces || s.workspaceIds.includes(ws)) && (!channel || s.channels.includes(channel)))
      .map(({ id, code, kind, description, channels }) => ({ id, code, kind, description, channels }))
    return ok(list)
  }),
  http.get(`${B}/settings/sender-numbers`, ({ request }) => guard(request, 'SUPER_ADMIN') ?? ok(senders)),
  http.get(`${B}/settings/sender-numbers/:id`, ({ request, params }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const s = senders.find((x) => x.id === Number(params.id))
    return s ? ok(s) : fail(404, 'NOT_FOUND', `Không tìm thấy đầu số (id=${params.id})`)
  }),
  http.post(`${B}/settings/sender-numbers`, async ({ request }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const b = (await request.json()) as SenderNumberInput
    const errors = validateSender(b, false)
    if (errors.length) return invalid(errors)
    const code = b.code.trim().toUpperCase()
    if (senders.some((s) => s.code === code)) return fail(409, 'SENDER_CODE_DUPLICATE', 'Đầu số đã tồn tại')
    const s: SenderNumber = {
      id: ++senderSeq.v,
      code,
      kind: b.kind,
      description: null,
      enabled: b.enabled,
      channels: [],
      allWorkspaces: true,
      workspaceIds: [],
      gateway: { protocol: 'NONE', host: null, port: null, systemType: null, url: null, username: null, hasPassword: false, tps: null, lastTest: null },
      policy: b.policy,
      sentToday: 0,
      createdAt: new Date().toISOString(),
      createdBy: actor(request),
      updatedAt: null,
      updatedBy: null,
    }
    applySender(s, b)
    senders.push(s)
    audit(actor(request), 'CREATE', 'SENDER_NUMBER', String(s.id), `Thêm đầu số ${s.code}`, null)
    return ok(s)
  }),
  http.put(`${B}/settings/sender-numbers/:id`, async ({ request, params }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const s = senders.find((x) => x.id === Number(params.id))
    if (!s) return fail(404, 'NOT_FOUND', `Không tìm thấy đầu số (id=${params.id})`)
    const b = (await request.json()) as SenderNumberInput
    const errors = validateSender(b, s.gateway.hasPassword)
    if (errors.length) return invalid(errors)
    const code = b.code.trim().toUpperCase()
    if (senders.some((x) => x.code === code && x.id !== s.id)) return fail(409, 'SENDER_CODE_DUPLICATE', 'Đầu số đã tồn tại')
    // Đổi kết nối thì kết quả kiểm tra cũ không còn đúng
    const connChanged = (['protocol', 'host', 'port', 'systemType', 'url', 'username'] as const).some((k) => (b.gateway[k] ?? null) !== s.gateway[k]) || !!b.gateway.password || !!b.gateway.clearPassword
    s.code = code
    applySender(s, b)
    if (connChanged) s.gateway.lastTest = null
    s.updatedAt = new Date().toISOString()
    s.updatedBy = actor(request)
    audit(actor(request), 'UPDATE', 'SENDER_NUMBER', String(s.id), `Cập nhật đầu số ${s.code}`, null)
    return ok(s)
  }),
  http.patch(`${B}/settings/sender-numbers/:id/enabled`, async ({ request, params }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const s = senders.find((x) => x.id === Number(params.id))
    if (!s) return fail(404, 'NOT_FOUND', `Không tìm thấy đầu số (id=${params.id})`)
    s.enabled = !!((await request.json()) as { enabled: boolean }).enabled
    s.updatedAt = new Date().toISOString()
    s.updatedBy = actor(request)
    audit(actor(request), 'UPDATE', 'SENDER_NUMBER', String(s.id), `${s.enabled ? 'Bật' : 'Tắt'} đầu số ${s.code}`, null)
    return ok(s)
  }),
  // Giống BE: chiến dịch chưa kết thúc / huỷ (kể cả Nháp, Bị từ chối) còn dùng đầu số → 409 SENDER_IN_USE
  http.delete(`${B}/settings/sender-numbers/:id`, ({ request, params }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const idx = senders.findIndex((x) => x.id === Number(params.id))
    if (idx < 0) return fail(404, 'NOT_FOUND', 'Không tìm thấy đầu số')
    const code = senders[idx].code.toUpperCase()
    const used = campaigns
      .filter((c) => c.status !== 'COMPLETED' && c.status !== 'CANCELLED')
      .filter((c) => c.actionGroups.some((grp) => (grp.actions ?? []).some((a) => a.senderId?.trim().toUpperCase() === code)))
      .map((c) => c.code)
      .sort()
    if (used.length) {
      const preview = used.slice(0, 5).join(', ') + (used.length > 5 ? ` (+${used.length - 5})` : '')
      return fail(409, 'SENDER_IN_USE', `Đầu số đang được dùng ở chiến dịch ${preview}. Hãy tắt đầu số thay vì xoá`)
    }
    const [removed] = senders.splice(idx, 1)
    delete senderPasswords[removed.id]
    audit(actor(request), 'DELETE', 'SENDER_NUMBER', String(removed.id), `Xoá đầu số ${removed.code}`, null)
    return ok(removed)
  }),
  // Kiểm tra cấu hình ĐÃ LƯU. Mock: host localhost/127.* → REFUSED, host có "invalid" → UNKNOWN_HOST,
  // thiếu mật khẩu → AUTH_FAILED, còn lại OK.
  http.post(`${B}/settings/sender-numbers/:id/test-connection`, async ({ request, params }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const s = senders.find((x) => x.id === Number(params.id))
    if (!s) return fail(404, 'NOT_FOUND', `Không tìm thấy đầu số (id=${params.id})`)
    const gw = s.gateway
    if (gw.protocol === 'NONE') return invalid([{ field: 'gateway.protocol', message: 'Đầu số chưa cấu hình kết nối' }])
    await new Promise((r) => setTimeout(r, 600))
    const target = gw.protocol === 'SMPP' ? gw.host ?? '' : (() => { try { return new URL(gw.url ?? '').hostname } catch { return '' } })()
    const latencyMs = 20 + Math.round(rand() * 120)
    let result: ConnectionTestResult
    if (/^(localhost|127\.)/.test(target)) result = { ok: false, code: 'REFUSED', detail: `Connection refused: ${target}${gw.port ? ':' + gw.port : ''}`, latencyMs: null, testedAt: new Date().toISOString() }
    else if (target.includes('invalid')) result = { ok: false, code: 'UNKNOWN_HOST', detail: target, latencyMs: null, testedAt: new Date().toISOString() }
    else if (!gw.hasPassword) result = { ok: false, code: 'AUTH_FAILED', detail: gw.protocol === 'SMPP' ? 'command_status=0x0000000E' : 'HTTP 401', latencyMs, testedAt: new Date().toISOString() }
    else result = { ok: true, code: 'OK', detail: gw.protocol === 'SMPP' ? 'bind_transceiver OK' : 'HTTP 200', latencyMs, testedAt: new Date().toISOString() }
    gw.lastTest = result
    audit(actor(request), 'TEST', 'SENDER_NUMBER', String(s.id), `Kiểm tra kết nối đầu số ${s.code}: ${result.code}`, null)
    return ok(result)
  }),
  http.get(`${B}/settings/notification`, () => ok(notificationSettings)),
  http.put(`${B}/settings/notification`, async ({ request }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    Object.assign(notificationSettings, (await request.json()) as NotificationSettings)
    return ok(notificationSettings)
  }),

  // ───── File mẫu import (BE trả .xlsx 2 sheet; mock chỉ trả nội dung CSV như các mock xuất file khác) ─────
  http.get(`${B}/segments/import/template`, ({ request }) => {
    const g = guard(request, 'SEGMENT_MANAGE')
    if (g) return g
    return new HttpResponse('isdn\n', { headers: { 'Content-Type': 'application/octet-stream' } })
  }),

  // ───── Tạo tệp từ file (multipart) ─────
  http.post(`${B}/segments/import`, async ({ request }) => {
    const g = guard(request, 'SEGMENT_MANAGE')
    if (g) return g
    const form = await request.formData()
    const file = form.get('file') as File | null
    const name = String(form.get('name') ?? '').trim()
    if (!file || !name) return fail(400, 'FILE_INVALID', 'File không hợp lệ')
    if (segments.some((s) => s.workspaceId === wsOf(request) && s.name.trim().toLowerCase() === name.toLowerCase())) return fail(409, 'SEGMENT_NAME_DUPLICATE', 'Tên tệp khách hàng đã tồn tại trong workspace')
    const lines = (await file.text()).split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
    const valid = lines.filter((l) => /\d{8,}/.test(l.replace(/\D/g, ''))).length
    const s = {
      workspaceId: wsOf(request), id: ++segmentSeq.v, name, description: (form.get('description') as string) || null, type: 'FILE' as const, source: 'FILE' as const,
      status: 'READY' as const, totalUsers: valid, fileName: file.name, conditions: [], referencedBy: 0, createdAt: new Date().toISOString(), createdBy: actor(request), updatedAt: null,
    }
    segments.unshift(s)
    audit(actor(request), 'IMPORT', 'SEGMENT', String(s.id), `Import file ${file.name} (${valid} số)`, s.workspaceId)
    return ok({ ...s, invalidRows: lines.length - valid })
  }),

  // ───── Nhóm chiến dịch ─────
  http.post(`${B}/campaign-groups`, async ({ request }) => {
    const g = guard(request, 'CAMPAIGN_CREATE')
    if (g) return g
    const { name } = (await request.json()) as { name: string }
    if (groups.some((x) => x.workspaceId === wsOf(request) && x.name.toLowerCase() === name.trim().toLowerCase())) return fail(409, 'GROUP_NAME_DUPLICATE', 'Tên nhóm chiến dịch đã tồn tại trong workspace')
    const grp = { workspaceId: wsOf(request), id: Math.max(0, ...groups.map((x) => x.id)) + 1, name: name.trim(), campaignCount: 0, runningCount: 0, createdAt: new Date().toISOString() }
    groups.push(grp)
    return ok(grp)
  }),

  // ───── Import danh sách chuyển đổi (MANUAL_FILE) ─────
  http.post(`${B}/campaigns/:id/conversions/import`, async ({ request }) => {
    const g = guard(request, 'CAMPAIGN_UPDATE')
    if (g) return g
    const file = (await request.formData()).get('file') as File | null
    const rows = file ? (await file.text()).split(/\r?\n/).filter((l) => /\d{8,}/.test(l.replace(/\D/g, ''))).length : 0
    return ok({ matched: Math.round(rows * 0.7), rows, invalidRows: 0 })
  }),

  // ───── Dashboard Super Admin ─────
  http.get(`${B}/admin/dashboard`, ({ request }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const m = (total: number, prev: number | null) => ({ total, previousTotal: prev, growth: prev ? ((total - prev) / prev) * 100 : null })
    const top = workspaces
      .map((w) => ({ workspaceId: w.id, code: w.code, name: w.name, campaignCount: campaigns.filter((c) => c.workspaceId === w.id).length, runningCount: campaigns.filter((c) => c.workspaceId === w.id && c.status === 'RUNNING').length }))
      .sort((a, b) => b.campaignCount - a.campaignCount)
    return ok({
      activeWorkspaces: m(workspaces.filter((w) => w.status === 'ACTIVE').length, workspaces.length - 1),
      totalUsers: m(users.length, users.length - 1),
      runningCampaigns: m(campaigns.filter((c) => c.status === 'RUNNING').length, null),
      totalCampaigns: m(campaigns.length, campaigns.length - 2),
      topWorkspaces: top,
      periodDays: 7,
    })
  }),
  http.get(`${B}/admin/dashboard/top-workspaces/export`, ({ request }) => {
    const g = guard(request, 'SUPER_ADMIN')
    if (g) return g
    const csv = 'code,name,campaigns\n' + workspaces.map((w) => `${w.code},${w.name},${campaigns.filter((c) => c.workspaceId === w.id).length}`).join('\n')
    return new HttpResponse(csv, { headers: { 'Content-Type': 'application/octet-stream' } })
  }),

  // ───── API key sự kiện ─────
  http.get(`${B}/workspaces/:id/api-key`, ({ request, params }) => {
    const g = guard(request, 'SECRET_KEY_MANAGE')
    if (g) return g
    return ok(apiKeys[Number(params.id)] ?? { configured: false, hint: null, rotatedAt: null, key: null })
  }),
  http.post(`${B}/workspaces/:id/api-key`, ({ request, params }) => {
    const g = guard(request, 'SECRET_KEY_MANAGE')
    if (g) return g
    const key = 'lads_' + Array.from({ length: 32 }, () => 'abcdefghijkmnpqrstuvwxyz23456789'[Math.floor(rand() * 32)]).join('')
    apiKeys[Number(params.id)] = { configured: true, hint: '…' + key.slice(-4), rotatedAt: new Date().toISOString(), key: null }
    return ok({ ...apiKeys[Number(params.id)], key })
  }),
  http.delete(`${B}/workspaces/:id/api-key`, ({ request, params }) => {
    const g = guard(request, 'SECRET_KEY_MANAGE')
    if (g) return g
    delete apiKeys[Number(params.id)]
    return ok(null)
  }),
]
