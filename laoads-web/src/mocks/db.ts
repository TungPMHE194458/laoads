/*
 * Dữ liệu mock in-memory (reset khi F5). Shape bám docs/API_CONTRACT_V1.md + SRS LaoAds.
 * Số liệu sinh bằng PRNG có seed → ổn định giữa các lần tải.
 */
import type {
  ActionType,
  AuditLog,
  CampaignDetail,
  CampaignGroup,
  CampaignStatus,
  Channel,
  Member,
  NotificationSettings,
  Permission,
  PermissionDef,
  PriceItem,
  PriceQuote,
  RoleDef,
  Segment,
  SegmentAttribute,
  SenderNumber,
  UserDto,
  Workspace,
} from '@/api/types'
import { ALL_PERMISSIONS, NOTIFICATION_ACTIONS, TELECOM_ACTIONS } from '@/api/types'

// ── PRNG có seed (mulberry32) ──
let seed = 20261115
export function rand() {
  seed |= 0
  seed = (seed + 0x6d2b79f5) | 0
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
}
export const rint = (min: number, max: number) => Math.floor(rand() * (max - min + 1)) + min
const pick = <T,>(arr: T[]) => arr[Math.floor(rand() * arr.length)]

const NOW = new Date('2026-11-15T09:00:00+07:00').getTime()
export const daysAgo = (d: number, h = 9) => new Date(NOW - d * 86400_000 + (h - 9) * 3600_000).toISOString()

// ── Người dùng ──
interface MockUser extends UserDto {
  password: string
}
export const users: MockUser[] = [
  { id: 1, username: 'superadmin', password: 'superadmin', fullName: 'Quản trị hệ thống', email: 'admin@unitel.com.la', phone: '8562055500001', systemRole: 'SUPER_ADMIN', status: 'ACTIVE', lastLoginAt: daysAgo(0), createdAt: daysAgo(120) },
  { id: 2, username: 'owner_bsd', password: 'owner', fullName: 'Souksavanh Phommachanh', email: 'souksavanh.p@unitel.com.la', phone: '8562055510002', systemRole: 'USER', status: 'ACTIVE', lastLoginAt: daysAgo(1), createdAt: daysAgo(110) },
  { id: 3, username: 'admin_bsd', password: 'admin', fullName: 'Nguyễn Thu Hà', email: 'ha.nt@unitel.com.la', phone: '8562055520003', systemRole: 'USER', status: 'ACTIVE', lastLoginAt: daysAgo(2), createdAt: daysAgo(100) },
  { id: 4, username: 'member_bsd', password: 'member', fullName: 'Khamphone Sisouk', email: 'khamphone.s@unitel.com.la', phone: '8562055530004', systemRole: 'USER', status: 'ACTIVE', lastLoginAt: daysAgo(0), createdAt: daysAgo(90) },
  { id: 5, username: 'vilay.k', password: 'member', fullName: 'Vilayphone Keomany', email: 'vilay.k@unitel.com.la', phone: '8562055540005', systemRole: 'USER', status: 'ACTIVE', lastLoginAt: daysAgo(6), createdAt: daysAgo(60) },
  { id: 6, username: 'tran.minh', password: 'member', fullName: 'Trần Văn Minh', email: 'minh.tv@unitel.com.la', phone: '8562055550006', systemRole: 'USER', status: 'LOCKED', lastLoginAt: daysAgo(40), createdAt: daysAgo(80) },
  { id: 7, username: 'noy.s', password: 'member', fullName: 'Noy Saysana', email: 'noy.s@unitel.com.la', phone: '8562055560007', systemRole: 'USER', status: 'ACTIVE', lastLoginAt: null, createdAt: daysAgo(5) },
]
export const publicUser = (u: MockUser): UserDto => {
  const { password: _pw, ...rest } = u
  void _pw
  return rest
}

// ── Workspace ──
export const workspaces: Workspace[] = [
  { id: 1, code: 'BSD', name: 'BSD', description: 'Workspace khối kinh doanh dịch vụ — chiến dịch truyền thông & ưu đãi thuê bao di động', status: 'ACTIVE', contactName: 'Souksavanh Phommachanh', contactEmail: 'bsd@unitel.com.la', contactPhone: '8562055510002', memberCount: 0, createdAt: daysAgo(110), updatedAt: daysAgo(10) },
  { id: 2, code: 'BCD', name: 'BCD', description: 'Workspace mở rộng cho phòng ban BCD', status: 'ACTIVE', contactName: null, contactEmail: null, contactPhone: null, memberCount: 0, createdAt: daysAgo(30), updatedAt: null },
]

let memberSeq = 100
export const members: (Member & { workspaceId: number })[] = []
function addMember(workspaceId: number, userId: number, role: Member['role'], d: number) {
  const u = users.find((x) => x.id === userId)!
  members.push({ id: ++memberSeq, workspaceId, userId, username: u.username, fullName: u.fullName, email: u.email, phone: u.phone, role, status: 'ACTIVE', joinedAt: daysAgo(d) })
}
addMember(1, 2, 'OWNER', 110)
addMember(1, 3, 'ADMIN', 100)
addMember(1, 4, 'MEMBER', 90)
addMember(1, 5, 'MEMBER', 60)
addMember(1, 6, 'MEMBER', 80)
addMember(2, 4, 'MEMBER', 30)
addMember(2, 7, 'OWNER', 5)
export const nextMemberId = () => ++memberSeq

export function refreshMemberCounts() {
  for (const w of workspaces) w.memberCount = members.filter((m) => m.workspaceId === w.id && m.status === 'ACTIVE').length
}
refreshMemberCounts()

// ── Ma trận quyền ──
export const permissionDefs: PermissionDef[] = ALL_PERMISSIONS.map((code) => ({
  code,
  group: code.startsWith('WORKSPACE') ? 'WORKSPACE' : code.startsWith('MEMBER') ? 'MEMBER' : code.startsWith('CAMPAIGN') ? 'CAMPAIGN' : code.startsWith('SEGMENT') ? 'SEGMENT' : code.startsWith('SECRET') ? 'SECRET_KEY' : 'REPORT',
  description: code,
}))
export const roles: RoleDef[] = [
  { code: 'OWNER', name: 'Chủ sở hữu', permissions: [...ALL_PERMISSIONS] },
  { code: 'ADMIN', name: 'Quản trị viên', permissions: ALL_PERMISSIONS.filter((p) => p !== 'SECRET_KEY_MANAGE') },
  { code: 'MEMBER', name: 'Thành viên', permissions: ['WORKSPACE_VIEW', 'CAMPAIGN_VIEW', 'CAMPAIGN_CREATE', 'CAMPAIGN_UPDATE', 'CAMPAIGN_SUBMIT', 'SEGMENT_MANAGE', 'REPORT_VIEW'] as Permission[] },
]

// ── Bảng giá ──
const ALL_ACTIONS: ActionType[] = [...TELECOM_ACTIONS, ...NOTIFICATION_ACTIONS]
const BASE_PRICE: Record<ActionType, number> = {
  TOP_UP: 0, ADD_MONEY: 0, ADD_PROMOTION: 0, ADD_DATA: 120, ADD_SMS: 40, ADD_CALL_MINUTES: 90, ADD_PACKAGE: 250, REGISTER_CRBT: 150, REGISTER_LAOTV: 200,
  NOTIFICATION_SMS: 7, NOTIFICATION_EMAIL: 2, NOTIFICATION_WHATSAPP: 35, NOTIFICATION_USSD: 5,
}
const items = (factor: number): PriceItem[] =>
  ALL_ACTIONS.map((a) => ({ actionType: a, unitPrice: Math.round(BASE_PRICE[a] * factor), timePrice: a.startsWith('ADD_') || a === 'TOP_UP' ? 2 : 0, timePricePercentage: a.startsWith('ADD_') || a === 'TOP_UP' }))
export const priceQuotes: PriceQuote[] = [
  { id: 3, name: 'Bảng giá Q1/2027 (dự thảo)', note: 'Điều chỉnh đơn giá WhatsApp theo hợp đồng Meta mới', status: 'DRAFT', currency: 'LAK', items: items(1.1), lockedAt: null, lockedBy: null, createdAt: daysAgo(3), createdBy: 'superadmin', updatedAt: daysAgo(1) },
  { id: 2, name: 'Bảng giá Q4/2026', note: null, status: 'LOCKED', currency: 'LAK', items: items(1), lockedAt: daysAgo(45), lockedBy: 'superadmin', createdAt: daysAgo(50), createdBy: 'superadmin', updatedAt: daysAgo(45) },
  { id: 1, name: 'Bảng giá Q3/2026', note: null, status: 'ENDED', currency: 'LAK', items: items(0.95), lockedAt: daysAgo(130), lockedBy: 'superadmin', createdAt: daysAgo(135), createdBy: 'superadmin', updatedAt: daysAgo(45) },
]
export const priceSeq = { v: 3 }
export const activeUnitPrice = (a: ActionType) => priceQuotes.find((p) => p.status === 'LOCKED')?.items.find((i) => i.actionType === a)?.unitPrice ?? 0

// ── Tệp khách hàng ──
export const segmentAttributes: SegmentAttribute[] = [
  { field: 'province_code_active', group: 'PROFILE', dataType: 'STRING', options: ['VTE', 'LPB', 'SVK', 'CPS', 'XKH', 'OUD', 'BOK'] },
  { field: 'customer_age', group: 'PROFILE', dataType: 'NUMBER' },
  { field: 'gender', group: 'PROFILE', dataType: 'STRING', options: ['M', 'F'] },
  { field: 'tenure', group: 'PROFILE', dataType: 'NUMBER' },
  { field: 'act_status', group: 'PROFILE', dataType: 'STRING', options: ['00', '01', '02', '03'] },
  { field: 'device_type', group: 'DEVICE', dataType: 'STRING', options: ['SMARTPHONE', 'FEATURE_PHONE', 'MODEM'] },
  { field: 'infras_data_4g', group: 'DEVICE', dataType: 'BOOLEAN' },
  { field: 'is_smartphone', group: 'DEVICE', dataType: 'BOOLEAN' },
  { field: 'real_package_data', group: 'PACKAGE', dataType: 'STRING', options: ['D5K', 'D10K', 'M50', 'M99', 'M150', 'NONE'] },
  { field: 'data_package', group: 'PACKAGE', dataType: 'STRING', options: ['DAY', 'WEEK', 'MONTH', 'NONE'] },
  { field: 'g_volume', group: 'USAGE', dataType: 'NUMBER' },
  { field: 't_org_charge', group: 'USAGE', dataType: 'NUMBER' },
  { field: 'laostv_total_volume', group: 'USAGE', dataType: 'NUMBER' },
  { field: 'login_superapp', group: 'USAGE', dataType: 'NUMBER' },
  { field: 'active_umoney', group: 'BALANCE', dataType: 'BOOLEAN' },
  { field: 'balance_basic', group: 'BALANCE', dataType: 'NUMBER' },
]

export const segments: (Segment & { workspaceId: number })[] = [
  { workspaceId: 1, id: 1, name: 'Thuê bao 4G chưa dùng data – T11/2026', description: 'CAMP_5: máy hỗ trợ 4G nhưng không phát sinh lưu lượng data 30 ngày', type: 'QUERY', source: 'OCS', status: 'READY', totalUsers: 233_332, fileName: null, conditions: [{ field: 'act_status', operator: 'EQ', value: '00', join: 'AND' }, { field: 'infras_data_4g', operator: 'EQ', value: 'true', join: 'AND' }, { field: 'g_volume', operator: 'EQ', value: '0', join: 'AND' }], referencedBy: 2, createdAt: daysAgo(21), createdBy: 'admin_bsd', updatedAt: daysAgo(2) },
  { workspaceId: 1, id: 2, name: 'Gói ngày → nâng cấp gói tháng (30K–50K Kip)', description: 'CAMP_4: cước gốc 30K–50K, PSLL data 5–15GB, chưa có gói tháng', type: 'QUERY', source: 'OCS', status: 'READY', totalUsers: 111_161, fileName: null, conditions: [{ field: 't_org_charge', operator: 'BETWEEN', value: '30000', valueTo: '50000', join: 'AND' }, { field: 'g_volume', operator: 'BETWEEN', value: '5', valueTo: '15', join: 'AND' }, { field: 'data_package', operator: 'NEQ', value: 'MONTH', join: 'AND' }], referencedBy: 1, createdAt: daysAgo(25), createdBy: 'owner_bsd', updatedAt: daysAgo(25) },
  { workspaceId: 1, id: 3, name: 'Chưa kích hoạt U-Money (chéo BU)', description: 'CAMP_1: có PS BU Telecom, chưa có tài khoản U-Money', type: 'QUERY', source: 'OCS', status: 'READY', totalUsers: 202_276, fileName: null, conditions: [{ field: 'active_umoney', operator: 'EQ', value: 'false', join: 'AND' }, { field: 't_org_charge', operator: 'GT', value: '0', join: 'AND' }], referencedBy: 1, createdAt: daysAgo(30), createdBy: 'admin_bsd', updatedAt: daysAgo(30) },
  { workspaceId: 1, id: 4, name: 'Danh sách VIP Viêng Chăn (file)', description: null, type: 'FILE', source: 'FILE', status: 'READY', totalUsers: 4_812, fileName: 'vip_vientiane_2026-11.xlsx', conditions: [], referencedBy: 0, createdAt: daysAgo(8), createdBy: 'member_bsd', updatedAt: daysAgo(8) },
  { workspaceId: 1, id: 5, name: 'KH chưa đạt mục tiêu – Chạy camp 4G vòng 1', description: 'Tự sinh từ kết quả chiến dịch (GOAL_NOT_ACHIEVED)', type: 'CAMPAIGN_OUTCOME', source: 'CAMPAIGN', status: 'READY', totalUsers: 203_716, fileName: null, conditions: [], referencedBy: 1, createdAt: daysAgo(12), createdBy: 'system', updatedAt: daysAgo(12) },
  { workspaceId: 1, id: 6, name: 'Thuê bao mới kích hoạt SIM (sự kiện)', description: 'Sự kiện BCCS3 sale transaction — D0', type: 'EVENT', source: 'EVENT', status: 'BUILDING', totalUsers: 6_718, fileName: null, conditions: [], referencedBy: 1, createdAt: daysAgo(1), createdBy: 'admin_bsd', updatedAt: daysAgo(0) },
  { workspaceId: 1, id: 7, name: 'Xem LaoTV > 2GB/tháng', description: null, type: 'QUERY', source: 'OCS', status: 'FAILED', totalUsers: 0, fileName: null, conditions: [{ field: 'laostv_total_volume', operator: 'GT', value: '2048', join: 'AND' }], referencedBy: 0, createdAt: daysAgo(4), createdBy: 'member_bsd', updatedAt: daysAgo(4) },
  { workspaceId: 1, id: 8, name: 'Danh sách test nội bộ', description: 'ISDN nhân viên dùng bắn thử', type: 'FILE', source: 'FILE', status: 'ARCHIVED', totalUsers: 24, fileName: 'test_isdn.xlsx', conditions: [], referencedBy: 0, createdAt: daysAgo(70), createdBy: 'owner_bsd', updatedAt: daysAgo(20) },
  { workspaceId: 2, id: 9, name: 'Khách hàng doanh nghiệp – Fiber', description: null, type: 'FILE', source: 'FILE', status: 'READY', totalUsers: 1_530, fileName: 'enterprise_fiber.xlsx', conditions: [], referencedBy: 0, createdAt: daysAgo(9), createdBy: 'noy.s', updatedAt: null },
]
export const segmentSeq = { v: 9 }

// ── Nhóm chiến dịch ──
export const groups: (CampaignGroup & { workspaceId: number })[] = [
  { workspaceId: 1, id: 1, name: 'Phát triển Data 4G', campaignCount: 0, runningCount: 0, createdAt: daysAgo(40) },
  { workspaceId: 1, id: 2, name: 'Chuyển đổi chéo BU', campaignCount: 0, runningCount: 0, createdAt: daysAgo(35) },
  { workspaceId: 1, id: 3, name: 'Chăm sóc khách hàng', campaignCount: 0, runningCount: 0, createdAt: daysAgo(20) },
]

// ── Chiến dịch ──
type MockCampaign = CampaignDetail & { workspaceId: number }
const CH: Record<string, Channel[]> = { sms: ['SMS'], smsWa: ['SMS', 'WHATSAPP'], wa: ['WHATSAPP'], email: ['EMAIL'], ussd: ['USSD', 'SMS'], all: ['SMS', 'WHATSAPP', 'EMAIL'] }
const channelAction: Record<Channel, ActionType> = { SMS: 'NOTIFICATION_SMS', EMAIL: 'NOTIFICATION_EMAIL', WHATSAPP: 'NOTIFICATION_WHATSAPP', USSD: 'NOTIFICATION_USSD' }

function mkCampaign(
  id: number,
  name: string,
  status: CampaignStatus,
  segmentId: number,
  groupId: number | null,
  channels: Channel[],
  startDaysAgo: number,
  durationDays: number,
  opts: Partial<MockCampaign> = {},
): MockCampaign {
  const seg = segments.find((s) => s.id === segmentId)!
  const grp = groups.find((g) => g.id === groupId) ?? null
  const started = ['RUNNING', 'PAUSED', 'COMPLETED'].includes(status)
  const sentRate = started ? 0.86 + rand() * 0.12 : 0
  const openRate = started ? 0.18 + rand() * 0.3 : 0
  const conversionRate = started ? 0.02 + rand() * 0.09 : 0
  const actions = channels.map((c, i) => ({
    id: `a${id}-${i}`,
    type: channelAction[c],
    delayMinutes: i === 0 ? 0 : 60 * 24,
    senderId: c === 'SMS' ? '118' : null,
    subject: c === 'EMAIL' ? name : null,
    content: c === 'SMS' ? `[Unitel] ${name}. Soạn DK gửi 118 để nhận ưu đãi. Chi tiết: {{link}}` : c === 'WHATSAPP' ? null : `Ưu đãi dành riêng cho {{name}}`,
    templateId: c === 'WHATSAPP' ? 'unitel_promo_v3' : null,
  }))
  if (opts.type !== 'EVENT' && id % 3 === 0) actions.push({ id: `a${id}-gift`, type: 'ADD_DATA', delayMinutes: 0, senderId: null, subject: null, content: null, templateId: null, amount: 1024, packageCode: null } as never)
  const expectedCost = Math.round(seg.totalUsers * actions.reduce((s, a) => s + activeUnitPrice(a.type), 0) * 0.9)
  return {
    workspaceId: 1,
    id,
    code: `CP${String(2600 + id)}`,
    name,
    mode: 'OFFICIAL',
    type: 'STATIC',
    status,
    groupId,
    groupName: grp?.name ?? null,
    segmentId,
    segmentName: seg.name,
    targetCount: seg.totalUsers,
    channels,
    startAt: daysAgo(startDaysAgo),
    endAt: daysAgo(startDaysAgo - durationDays),
    // createdAt luôn ở quá khứ (kể cả chiến dịch lịch chạy tương lai)
    createdAt: daysAgo(Math.max(1, startDaysAgo + rint(2, 6)), 14),
    createdBy: pick(['admin_bsd', 'member_bsd', 'owner_bsd', 'vilay.k']),
    expectedCost,
    sentRate,
    openRate,
    conversionRate,
    uplift: started ? 0.4 + rand() * 3.2 : null,
    description: null,
    language: 'LO',
    testIsdns: [],
    actionGroups: [
      { id: `g${id}-a`, name: 'Nhóm A', allocationPercent: 90, isControl: false, mode: 'SEQUENTIAL', actions },
      { id: `g${id}-c`, name: 'Nhóm đối chứng', allocationPercent: 10, isControl: true, mode: 'SEQUENTIAL', actions: [] },
    ],
    schedule: { type: 'ONCE', startAt: daysAgo(startDaysAgo), endAt: daysAgo(startDaysAgo - durationDays), sendTime: '09:00', silentHours: { enabled: true, from: '21:00', to: '07:00' } },
    goal: { mode: 'AUTO_OCS', description: 'Thuê bao phát sinh hành vi mục tiêu trong 7 ngày sau khi nhận tin', attributionWindowDays: 7, stopOnGoal: true, maxNoResponse: 3 },
    budget: { expected: expectedCost, max: Math.round(expectedCost * 1.2) },
    rejectReason: status === 'REJECTED' ? 'Nội dung SMS vượt 160 ký tự và thiếu cú pháp từ chối nhận tin.' : null,
    history: [
      { at: daysAgo(startDaysAgo + 5, 14), actor: 'admin_bsd', action: 'CREATE' },
      ...(status !== 'DRAFT' ? [{ at: daysAgo(startDaysAgo + 3, 10), actor: 'admin_bsd', action: 'SUBMIT' }] : []),
      ...(started || status === 'SCHEDULED' ? [{ at: daysAgo(startDaysAgo + 2, 16), actor: 'owner_bsd', action: 'APPROVE' }] : []),
      ...(status === 'REJECTED' ? [{ at: daysAgo(startDaysAgo + 2, 16), actor: 'owner_bsd', action: 'REJECT', note: 'Nội dung SMS vượt 160 ký tự' }] : []),
    ],
    ...opts,
  }
}

export const campaigns: MockCampaign[] = [
  mkCampaign(1, 'Phát triển thuê bao Data 4G – Vòng 2', 'RUNNING', 5, 1, CH.smsWa, 3, 14),
  mkCampaign(2, 'Nâng cấp gói Data tháng – T11/2026', 'RUNNING', 2, 1, CH.sms, 6, 21),
  mkCampaign(3, 'Kích hoạt U-Money nhận 10.000 Kip', 'RUNNING', 3, 2, CH.all, 10, 30),
  mkCampaign(4, 'Chào mừng thuê bao mới – D0', 'RUNNING', 6, 3, CH.ussd, 1, 60, { type: 'EVENT' }),
  mkCampaign(5, 'Phát triển thuê bao Data 4G – Vòng 1', 'COMPLETED', 1, 1, CH.smsWa, 30, 14),
  mkCampaign(6, 'Tri ân khách hàng VIP Viêng Chăn', 'SCHEDULED', 4, 3, CH.email, -2, 7),
  mkCampaign(7, 'Ưu đãi LaoTV cuối năm', 'PENDING_APPROVAL', 1, 2, CH.wa, -5, 10),
  mkCampaign(8, 'Đăng ký Super App tặng 1GB', 'PENDING_APPROVAL', 3, 2, CH.sms, -4, 14),
  mkCampaign(9, 'Nhắc gia hạn gói tháng', 'PAUSED', 2, 3, CH.sms, 12, 30),
  mkCampaign(10, 'Khuyến mãi nạp thẻ cuối tuần', 'REJECTED', 3, null, CH.sms, -1, 3),
  mkCampaign(11, 'Thử nghiệm nội dung WhatsApp mới', 'DRAFT', 8, null, CH.wa, -7, 2, { mode: 'TEST' }),
  mkCampaign(12, 'Chiến dịch Tết Lào 2027 (nháp)', 'DRAFT', 1, null, CH.all, -150, 14),
  mkCampaign(13, 'Đổi gói ngày sang gói tuần', 'COMPLETED', 2, 1, CH.sms, 55, 21),
  mkCampaign(14, 'Thông báo bảo trì hệ thống', 'CANCELLED', 4, 3, CH.sms, 18, 1),
]
export const campaignSeq = { v: 14 }

export function refreshGroupCounts() {
  for (const g of groups) {
    const cs = campaigns.filter((c) => c.groupId === g.id)
    g.campaignCount = cs.length
    g.runningCount = cs.filter((c) => c.status === 'RUNNING').length
  }
}
refreshGroupCounts()

// ── Nhật ký ──
export const auditLogs: AuditLog[] = []
let auditSeq = 0
export function audit(actor: string, actionType: string, entityType: string, entityId: string | null, summary: string, workspaceId: number | null = 1, at?: string) {
  auditLogs.unshift({ id: ++auditSeq, actor, actionType, entityType, entityId, summary, detailJson: null, workspaceId, ip: '10.120.30.' + rint(10, 250), createdAt: at ?? new Date().toISOString() })
}
;[
  ['superadmin', 'LOCK', 'PRICE_QUOTE', '2', 'Khoá bảng giá "Bảng giá Q4/2026"', null, 45],
  ['owner_bsd', 'APPROVE', 'CAMPAIGN', '5', 'Duyệt chiến dịch "Phát triển thuê bao Data 4G – Vòng 1"', 1, 32],
  ['admin_bsd', 'CREATE', 'SEGMENT', '1', 'Tạo tệp KH "Thuê bao 4G chưa dùng data – T11/2026"', 1, 21],
  ['superadmin', 'CREATE', 'WORKSPACE', '2', 'Tạo workspace BCD', null, 30],
  ['owner_bsd', 'CHANGE_ROLE', 'MEMBER', '102', 'Đổi vai trò Nguyễn Thu Hà → Quản trị viên', 1, 15],
  ['owner_bsd', 'REJECT', 'CAMPAIGN', '10', 'Từ chối chiến dịch "Khuyến mãi nạp thẻ cuối tuần"', 1, 2],
  ['admin_bsd', 'SUBMIT', 'CAMPAIGN', '7', 'Gửi duyệt "Ưu đãi LaoTV cuối năm"', 1, 1],
  ['member_bsd', 'SUBMIT', 'CAMPAIGN', '8', 'Gửi duyệt "Đăng ký Super App tặng 1GB"', 1, 1],
  ['member_bsd', 'LOGIN', 'AUTH', null, 'Đăng nhập', null, 0],
  ['superadmin', 'UPDATE', 'ROLE', 'MEMBER', 'Cập nhật quyền vai trò Thành viên', null, 9],
  ['admin_bsd', 'IMPORT', 'SEGMENT', '4', 'Import file vip_vientiane_2026-11.xlsx (4.812 dòng)', 1, 8],
].reverse().forEach(([a, t, e, id, s, w, d]) => audit(a as string, t as string, e as string, id as string | null, s as string, w as number | null, daysAgo(d as number, rint(8, 17))))

// ── Cài đặt gửi tin ──
export const senders: SenderNumber[] = [
  { id: 1, code: '118', kind: 'SHORTCODE', description: 'Đầu số CSKH chính', enabled: true },
  { id: 2, code: '209', kind: 'SHORTCODE', description: 'Đầu số khuyến mãi', enabled: true },
  { id: 3, code: 'UNITEL', kind: 'BRANDNAME', description: 'Brandname chính thức', enabled: true },
  { id: 4, code: 'UNITEL_LOGS', kind: 'BRANDNAME', description: 'Thông báo hệ thống/kỹ thuật', enabled: false },
  { id: 5, code: '1555', kind: 'SHORTCODE', description: 'Dịch vụ giá trị gia tăng', enabled: true },
]
export const senderSeq = { v: 5 }
export const notificationSettings: NotificationSettings = {
  retryMaxAttempts: 3,
  retryIntervalMinutes: 30,
  refundOnFailure: true,
  silentHoursEnabled: true,
  silentFrom: '21:00',
  silentTo: '07:00',
  defaultLanguage: 'LO',
}
