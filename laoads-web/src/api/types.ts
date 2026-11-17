/*
 * Kiểu dữ liệu API. Phần "Core" bám docs/API_CONTRACT_V1.md §1–8 (BE đợt 1).
 * Phần chiến dịch / tệp KH / dashboard bám §9 (BE đợt 2–3); chế độ mock dùng handlers/campaign.ts.
 * Khi BE export openapi.yaml → `npm run gen:api` sinh src/api/schema.ts; chuyển dần sang dùng schema.
 */

// ───────────── Envelope ─────────────
export interface PageMeta {
  totalPages: number
  currentPage: number
  pageSize: number
  totalItems: number
}
export interface Envelope<T> {
  success: boolean
  data: T
  message?: string | null
  statusCode?: string
  metaData?: PageMeta
  requestId?: string
  errors?: { field: string; message: string }[]
}
export interface Paged<T> {
  items: T[]
  meta: PageMeta
}
export interface PageQuery {
  page?: number
  size?: number
  query?: string
}

// ───────────── Core (contract v1) ─────────────
export type SystemRole = 'SUPER_ADMIN' | 'USER'
export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'MEMBER'
export type Permission =
  | 'WORKSPACE_VIEW'
  | 'WORKSPACE_UPDATE'
  | 'MEMBER_MANAGE'
  | 'CAMPAIGN_VIEW'
  | 'CAMPAIGN_CREATE'
  | 'CAMPAIGN_UPDATE'
  | 'CAMPAIGN_SUBMIT'
  | 'CAMPAIGN_APPROVE'
  | 'CAMPAIGN_DELETE'
  | 'SEGMENT_MANAGE'
  | 'SECRET_KEY_MANAGE'
  | 'REPORT_VIEW'

export const ALL_PERMISSIONS: Permission[] = [
  'WORKSPACE_VIEW',
  'WORKSPACE_UPDATE',
  'MEMBER_MANAGE',
  'CAMPAIGN_VIEW',
  'CAMPAIGN_CREATE',
  'CAMPAIGN_UPDATE',
  'CAMPAIGN_SUBMIT',
  'CAMPAIGN_APPROVE',
  'CAMPAIGN_DELETE',
  'SEGMENT_MANAGE',
  'SECRET_KEY_MANAGE',
  'REPORT_VIEW',
]

export interface UserDto {
  id: number
  username: string
  fullName: string | null
  email: string | null
  phone: string | null
  systemRole: SystemRole
  status: 'ACTIVE' | 'LOCKED'
  lastLoginAt: string | null
  createdAt: string
}
export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserDto
}
export interface MyWorkspace {
  id: number
  code: string
  name: string
  status: 'ACTIVE' | 'LOCKED'
  role: WorkspaceRole | null
  permissions: Permission[]
}
export interface MeResponse {
  user: UserDto
  workspaces: MyWorkspace[]
}
export interface VsaConfig {
  enabled: boolean
  loginUrl: string | null
}

export interface Workspace {
  id: number
  code: string
  name: string
  description: string | null
  status: 'ACTIVE' | 'LOCKED'
  contactName: string | null
  contactEmail: string | null
  contactPhone: string | null
  memberCount: number
  createdAt: string
  updatedAt: string | null
}
export interface WorkspaceUpdateInput {
  name: string
  description?: string | null
  contactName?: string | null
  contactEmail?: string | null
  contactPhone?: string | null
}

export interface Member {
  id: number
  userId: number
  username: string
  fullName: string | null
  email: string | null
  phone: string | null
  role: WorkspaceRole
  status: 'ACTIVE' | 'REVOKED'
  joinedAt: string
}

/** API key sự kiện của workspace — `key` chỉ có trong response tạo / xoay vòng */
export interface ApiKeyInfo {
  configured: boolean
  hint: string | null
  rotatedAt: string | null
  key: string | null
}

export interface PermissionDef {
  code: Permission
  group: 'WORKSPACE' | 'MEMBER' | 'CAMPAIGN' | 'SEGMENT' | 'SECRET_KEY' | 'REPORT'
  description: string
}
export interface RoleDef {
  code: WorkspaceRole
  name: string
  permissions: Permission[]
}

export type ActionType =
  | 'TOP_UP'
  | 'ADD_MONEY'
  | 'ADD_PROMOTION'
  | 'ADD_DATA'
  | 'ADD_SMS'
  | 'ADD_CALL_MINUTES'
  | 'ADD_PACKAGE'
  | 'REGISTER_CRBT'
  | 'REGISTER_LAOTV'
  | 'NOTIFICATION_SMS'
  | 'NOTIFICATION_EMAIL'
  | 'NOTIFICATION_WHATSAPP'
  | 'NOTIFICATION_USSD'

export const NOTIFICATION_ACTIONS: ActionType[] = [
  'NOTIFICATION_SMS',
  'NOTIFICATION_EMAIL',
  'NOTIFICATION_WHATSAPP',
  'NOTIFICATION_USSD',
]
export const TELECOM_ACTIONS: ActionType[] = [
  'TOP_UP',
  'ADD_MONEY',
  'ADD_PROMOTION',
  'ADD_DATA',
  'ADD_SMS',
  'ADD_CALL_MINUTES',
  'ADD_PACKAGE',
  'REGISTER_CRBT',
  'REGISTER_LAOTV',
]

export interface PriceItem {
  actionType: ActionType
  unitPrice: number
  timePrice: number
  timePricePercentage: boolean
}
export type PriceQuoteStatus = 'DRAFT' | 'LOCKED' | 'ENDED'
export interface PriceQuote {
  id: number
  name: string
  note: string | null
  status: PriceQuoteStatus
  currency: 'LAK'
  items: PriceItem[]
  lockedAt: string | null
  lockedBy: string | null
  createdAt: string
  createdBy: string | null
  updatedAt: string | null
}
export interface PriceQuoteInput {
  name: string
  note?: string | null
  items: PriceItem[]
}

export interface AuditLog {
  id: number
  actor: string
  actionType: string
  entityType: string
  entityId: string | null
  summary: string | null
  detailJson: string | null
  workspaceId: number | null
  ip: string | null
  createdAt: string
  /** SUCCESS | FAILURE (SRS 3.1.8) */
  result: 'SUCCESS' | 'FAILURE'
  errorCode: string | null
}

// ───────────── Chiến dịch / tệp KH / dashboard (BE đợt 2–3; mock: handlers/campaign.ts) ─────────────
export type Channel = 'SMS' | 'EMAIL' | 'WHATSAPP' | 'USSD'
export const CHANNELS: Channel[] = ['SMS', 'EMAIL', 'WHATSAPP', 'USSD']

export type CampaignStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'SCHEDULED'
  | 'RUNNING'
  | 'PAUSED'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED'
export type CampaignMode = 'OFFICIAL' | 'TEST'
export type CampaignType = 'STATIC' | 'EVENT'

export interface CampaignSummary {
  id: number
  code: string
  name: string
  mode: CampaignMode
  type: CampaignType
  status: CampaignStatus
  groupId: number | null
  groupName: string | null
  segmentId: number | null
  segmentName: string | null
  targetCount: number
  channels: Channel[]
  startAt: string | null
  endAt: string | null
  createdAt: string
  createdBy: string
  expectedCost: number
  sentRate: number
  openRate: number
  conversionRate: number
  uplift: number | null
}

export interface CampaignAction {
  id: string
  type: ActionType
  delayMinutes: number
  senderId?: string | null
  subject?: string | null
  content?: string | null
  templateId?: string | null
  amount?: number | null
  packageCode?: string | null
  /** Thay URL trong nội dung bằng link tracking /api/v1/t/{token} (SRS 3.2.3.6) */
  trackLinks?: boolean | null
  /** WhatsApp (như laoads.com): CONTENT = nhập nội dung, TEMPLATE = chọn template đã duyệt (templateId) */
  whatsappMode?: 'CONTENT' | 'TEMPLATE' | null
}
export interface ActionGroup {
  id: string
  name: string
  allocationPercent: number
  isControl: boolean
  mode: 'SEQUENTIAL' | 'PARALLEL'
  actions: CampaignAction[]
}
export interface CampaignSchedule {
  type: 'NOW' | 'ONCE' | 'RECURRING'
  startAt: string | null
  endAt: string | null
  every?: number
  unit?: 'DAY' | 'WEEK' | 'MONTH'
  weekdays?: number[]
  /** Nhiều khung thời gian (laoads.com "Khung thời gian 1, 2…") — có thì thay cho every / unit / weekdays / monthDays / sendTime */
  frames?: ScheduleFrame[] | null
  /** SRS 3.2.5.3: các ngày trong tháng (unit = MONTH), trống = ngày của startAt */
  monthDays?: number[] | null
  /** SRS 3.2.5.3: hoàn thành sau N lần lặp; null = chạy tới thời gian kết thúc */
  maxOccurrences?: number | null
  sendTime?: string
  silentHours?: { enabled: boolean; from: string; to: string }
}
export interface ScheduleFrame {
  every: number
  unit: 'DAY' | 'WEEK' | 'MONTH'
  weekdays?: number[] | null
  monthDays?: number[] | null
  sendTime: string
}
export type CampaignOutcome = 'GOAL_ACHIEVED' | 'GOAL_NOT_ACHIEVED' | 'WHATSAPP_FAILED'
export interface CampaignGoal {
  mode: 'AUTO_OCS' | 'MANUAL_FILE' | 'NONE'
  description: string
  attributionWindowDays: number
  stopOnGoal: boolean
  /** Điều kiện dừng (SRS 3.2.6.1): không phản hồi sau N thông báo liên tiếp; null = tắt */
  maxNoResponse: number | null
  /** Cho phép khách hàng bị loại quay lại chiến dịch sau N ngày; null = tắt (loại vĩnh viễn) */
  reentryDays?: number | null
  /** SRS 3.2.6.1: điều kiện đạt mục tiêu trên dữ liệu BI (all_report_stl_new); bắt buộc khi gửi duyệt AUTO_OCS */
  conditions?: SegmentCondition[] | null
  /** "Trích xuất khách hàng" (laoads.com): tự tạo tệp khách hàng cho các nhóm này khi chiến dịch hoàn tất */
  outcomes?: CampaignOutcome[] | null
}
export interface CampaignDetail extends CampaignSummary {
  description: string | null
  language: 'LO' | 'EN' | 'VI'
  testIsdns: string[]
  actionGroups: ActionGroup[]
  schedule: CampaignSchedule
  goal: CampaignGoal
  budget: { expected: number; max: number | null }
  /** Template sự kiện (bắt buộc khi type = EVENT, danh mục GET /event-templates) — SRS 3.2.1.6 */
  eventTemplate?: string | null
  /** Giới hạn số lượng khách hàng: TEST 1–1000 (mặc định 10, SRS 3.2.1.4); OFFICIAL tuỳ chọn như laoads.com (trống = cả tệp) */
  limit?: number | null
  rejectReason: string | null
  history: { at: string; actor: string; action: string; note?: string | null }[]
}
export type CampaignInput = Omit<
  CampaignDetail,
  | 'id'
  | 'code'
  | 'status'
  | 'createdAt'
  | 'createdBy'
  | 'sentRate'
  | 'openRate'
  | 'conversionRate'
  | 'uplift'
  | 'rejectReason'
  | 'history'
  | 'groupName'
  | 'segmentName'
  | 'expectedCost'
>

export interface CampaignGroup {
  id: number
  name: string
  /** tối đa 500 ký tự (như laoads.com) */
  description?: string | null
  campaignCount: number
  runningCount: number
  createdAt: string
}

export interface ChannelStat {
  channel: Channel
  /** tin gửi thành công (SENT + DELIVERED) */
  sent: number
  /** tin gửi lỗi — tỷ lệ gửi thành công = sent / (sent + failed) (SRS 3.2.8.2) */
  failed: number
  delivered: number
  opened: number
  clicked: number
  converted: number
}
export interface TimePoint {
  date: string
  sent: number
  opens: number
  clicks: number
  conversions: number
}
export interface CampaignReport {
  totals: { target: number; sent: number; delivered: number; failed: number; opens: number; clicks: number; conversions: number; spent: number }
  timeseries: TimePoint[]
  channels: ChannelStat[]
  /** Theo nhóm hành động (SRS 3.2.8.7): delivered = KH đã nhận ≥ 1 tin, sentRate = delivered / target, openRate = opens / delivered */
  groups: { groupName: string; isControl: boolean; target: number; delivered: number; sentRate: number; failed: number; opens: number; openRate: number; clicks: number; clickRate: number; conversions: number; conversionRate: number; uplift: number | null }[]
  /** Theo từng hành động (bảng "Hành động" của laoads.com) — đếm theo tin */
  actions: ActionStat[]
}
export interface ActionStat {
  groupKey: string
  groupName: string
  actionKey: string
  actionIndex: number
  actionType: ActionType
  channel: string
  sent: number
  failed: number
  delivered: number
  opens: number
  clicks: number
  conversions: number
}
/** Template WhatsApp đã duyệt ở nhà cung cấp (GET /whatsapp/templates) */
export interface WhatsAppTemplate {
  id: string
  name: string
  language: string
  status: 'APPROVED' | 'PENDING' | 'REJECTED'
}

export interface Metric {
  total: number
  previousTotal: number | null
  growth: number | null
}
export interface DashboardSummary {
  runningCampaigns: Metric
  pendingApprovals: Metric
  totalCampaigns: Metric
  reachedCustomers: Metric
  periodDays: number
}

export interface TopWorkspace {
  workspaceId: number
  code: string
  name: string
  campaignCount: number
  runningCount: number
}
export interface AdminDashboard {
  activeWorkspaces: Metric
  totalUsers: Metric
  runningCampaigns: Metric
  totalCampaigns: Metric
  topWorkspaces: TopWorkspace[]
  periodDays: number
}

export type SegmentType = 'FILE' | 'QUERY' | 'EVENT' | 'CAMPAIGN_OUTCOME' | 'AUDIENCE'
export type SegmentStatus = 'BUILDING' | 'READY' | 'FAILED' | 'ARCHIVED'
export type SegmentSource = 'OCS' | 'DWH' | 'FILE' | 'CAMPAIGN' | 'EVENT' | 'BCCS3'

// ───── Audience API (OCS Audience / BCCS3 Audience) — tệp KH loại AUDIENCE ─────
export type AudienceSource = 'OCS' | 'BCCS3'
export const AUDIENCE_SOURCES: AudienceSource[] = ['OCS', 'BCCS3']
export type AudienceDataType = 'BOOLEAN' | 'INT' | 'LONG' | 'DOUBLE' | 'DATE' | 'STRING'
export type AudienceOperator = 'IS' | 'NOT' | 'LIKE' | 'NOT_LIKE' | 'GREATER_THAN' | 'GREATER_EQUAL_THAN' | 'LESS_THAN' | 'LESS_EQUAL_THAN'
export interface AudienceSourceInfo {
  source: AudienceSource
  /** false = chưa cấu hình URL / API key → không chọn được */
  configured: boolean
  mode: 'simulated' | 'http'
}
export interface AudienceColumn {
  name: string
  dataType: AudienceDataType
}
export interface AudienceSelectedColumn {
  columnName: string
  dataType: AudienceDataType
  /** Chữ HOA; đúng 1 cột mang 'PHONE' (số thuê bao) */
  targetField: string
}
export interface AudienceCondition {
  columnName: string
  operator: AudienceOperator
  dataType: AudienceDataType
  /** Nối với điều kiện kế tiếp; điều kiện cuối không cần */
  logicalOperator?: 'AND' | 'OR' | null
  /** Luôn là chuỗi: DATE "YYYY-MM-DD", BOOLEAN "true"/"false" */
  value: string
}
export interface AudienceRequest {
  tableName: string
  selectedColumns: AudienceSelectedColumn[]
  conditions: AudienceCondition[]
}
export interface AudienceVerifyResult {
  valid: boolean
  message: string | null
}
export interface SegmentCondition {
  field: string
  operator: 'EQ' | 'NEQ' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'BETWEEN' | 'IN' | 'CONTAINS' | 'IS_NULL'
  value: string
  valueTo?: string
  join: 'AND' | 'OR'
}
export interface Segment {
  id: number
  name: string
  description: string | null
  type: SegmentType
  source: SegmentSource
  status: SegmentStatus
  /** Tệp đang BUILDING (AUDIENCE): số thuê bao đã nạp tới thời điểm hiện tại */
  totalUsers: number
  fileName: string | null
  conditions: SegmentCondition[]
  /** Chỉ tệp AUDIENCE: request gửi Audience API (BE luôn trả, null với loại khác) */
  audience?: AudienceRequest | null
  /** Số dòng NDJSON đã xử lý (offset để chạy tiếp với OCS); loại khác = 0 */
  importedRows?: number
  referencedBy: number
  /** Lý do build thất bại (status FAILED) */
  errorMessage?: string | null
  /** Chỉ có khi vừa import file: số dòng không hợp lệ đã bỏ qua */
  invalidRows?: number | null
  createdAt: string
  createdBy: string
  updatedAt: string | null
}
export interface SegmentAttribute {
  field: string
  group: 'PROFILE' | 'DEVICE' | 'PACKAGE' | 'USAGE' | 'BALANCE'
  dataType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'DATE'
  options?: string[]
}
/** Tham số cá nhân hoá trong nội dung tin (GET /message-params): viết `{{key}}` hoặc `{{key|mặc định}}`. */
export interface MessageParam {
  key: string
  /** `{{key}}` — chuỗi chèn vào nội dung */
  token: string
  /** TARGET = lấy từ thuê bao nhận tin; SUBSCRIBER = dữ liệu BI (LADS_SUBSCRIBER), có thể trống */
  source: 'TARGET' | 'SUBSCRIBER'
  /** Độ dài tối đa của giá trị — để ước tính độ dài tin sau khi thay */
  maxLength: number
  example: string
}
export interface SegmentMember {
  recipientKey: string
  phone: string
  attributes: Record<string, string | number>
}

// ───── Đầu số gửi tin (cấu hình từng đầu số) ─────
export type SenderKind = 'SHORTCODE' | 'BRANDNAME'
/** WHATSAPP = brand / tài khoản WhatsApp chính thức (chỉ loại BRANDNAME) */
export type SenderChannel = 'SMS' | 'USSD' | 'WHATSAPP'
export const SENDER_CHANNELS: SenderChannel[] = ['SMS', 'USSD', 'WHATSAPP']
export type GatewayProtocol = 'NONE' | 'SMPP' | 'HTTP'
export type ConnectionTestCode = 'OK' | 'TIMEOUT' | 'REFUSED' | 'UNKNOWN_HOST' | 'AUTH_FAILED' | 'REJECTED' | 'PROTOCOL_ERROR' | 'ERROR'
export interface ConnectionTestResult {
  ok: boolean
  code: ConnectionTestCode
  detail: string | null
  latencyMs: number | null
  testedAt: string
}
export interface SenderGateway {
  protocol: GatewayProtocol
  /** SMPP */
  host: string | null
  port: number | null
  systemType: string | null
  /** HTTP */
  url: string | null
  /** SMPP system_id (≤ 15 ký tự) / HTTP user (≤ 128) */
  username: string | null
  /** Mật khẩu không bao giờ trả về — chỉ biết đã lưu hay chưa */
  hasPassword: boolean
  /** null = không giới hạn */
  tps: number | null
  lastTest: ConnectionTestResult | null
}
/** Mọi trường null = theo chính sách chung (/settings/notification). */
export interface SenderPolicy {
  retryMaxAttempts: number | null
  retryIntervalMinutes: number | null
  /** null = theo chung; true = khung riêng; false = đầu số này không chặn giờ */
  silentHoursEnabled: boolean | null
  silentFrom: string | null
  silentTo: string | null
  /** null = không giới hạn */
  dailyLimit: number | null
}
export interface SenderNumber {
  id: number
  code: string
  kind: SenderKind
  description: string | null
  enabled: boolean
  /** không rỗng; BRANDNAME chỉ được ['SMS'] */
  channels: SenderChannel[]
  allWorkspaces: boolean
  workspaceIds: number[]
  gateway: SenderGateway
  policy: SenderPolicy
  /** số tin đã gửi qua đầu số trong ngày (giờ Lào) */
  sentToday: number
  createdAt: string
  createdBy: string | null
  updatedAt: string | null
  updatedBy: string | null
}
export interface SenderGatewayInput {
  protocol: GatewayProtocol
  host: string | null
  port: number | null
  systemType: string | null
  url: string | null
  username: string | null
  /** bỏ trống/null = giữ mật khẩu đã lưu; chuỗi khác rỗng = thay */
  password?: string | null
  /** true = xoá mật khẩu đã lưu */
  clearPassword?: boolean
  tps: number | null
}
export interface SenderNumberInput {
  code: string
  kind: SenderKind
  description: string | null
  enabled: boolean
  channels: SenderChannel[]
  allWorkspaces: boolean
  workspaceIds: number[]
  gateway: SenderGatewayInput
  policy: SenderPolicy
}
/** Đầu số workspace hiện tại được dùng (wizard chọn đầu số). */
export interface AvailableSender {
  id: number
  code: string
  kind: SenderKind
  description: string | null
  channels: SenderChannel[]
}
export interface NotificationSettings {
  retryMaxAttempts: number
  retryIntervalMinutes: number
  refundOnFailure: boolean
  silentHoursEnabled: boolean
  silentFrom: string
  silentTo: string
  defaultLanguage: 'LO' | 'EN' | 'VI'
}
