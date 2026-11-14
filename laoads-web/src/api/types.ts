/*
 * Kiểu dữ liệu API. Phần "Core" bám đúng docs/API_CONTRACT_V1.md (BE đợt 1 đã có).
 * Phần "Pending BE" là shape FE đề xuất theo SRS — hiện được MSW mock (src/mocks).
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
}

// ───────────── Pending BE (MSW mock, shape theo SRS) ─────────────
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
  sendTime?: string
  silentHours?: { enabled: boolean; from: string; to: string }
}
export interface CampaignGoal {
  mode: 'AUTO_OCS' | 'MANUAL_FILE' | 'NONE'
  description: string
  attributionWindowDays: number
  stopOnGoal: boolean
  maxNoResponse: number | null
}
export interface CampaignDetail extends CampaignSummary {
  description: string | null
  language: 'LO' | 'EN' | 'VI'
  testIsdns: string[]
  actionGroups: ActionGroup[]
  schedule: CampaignSchedule
  goal: CampaignGoal
  budget: { expected: number; max: number | null }
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
  campaignCount: number
  runningCount: number
  createdAt: string
}

export interface ChannelStat {
  channel: Channel
  sent: number
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
  groups: { groupName: string; isControl: boolean; target: number; sentRate: number; openRate: number; conversionRate: number; uplift: number | null }[]
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

export type SegmentType = 'FILE' | 'QUERY' | 'EVENT' | 'CAMPAIGN_OUTCOME'
export type SegmentStatus = 'BUILDING' | 'READY' | 'FAILED' | 'ARCHIVED'
export interface SegmentCondition {
  field: string
  operator: 'EQ' | 'NEQ' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'BETWEEN' | 'IN' | 'IS_NULL'
  value: string
  valueTo?: string
  join: 'AND' | 'OR'
}
export interface Segment {
  id: number
  name: string
  description: string | null
  type: SegmentType
  source: 'OCS' | 'DWH' | 'FILE' | 'CAMPAIGN' | 'EVENT'
  status: SegmentStatus
  totalUsers: number
  fileName: string | null
  conditions: SegmentCondition[]
  referencedBy: number
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
export interface SegmentMember {
  recipientKey: string
  phone: string
  attributes: Record<string, string | number>
}

export interface SenderNumber {
  id: number
  code: string
  kind: 'SHORTCODE' | 'BRANDNAME'
  description: string | null
  enabled: boolean
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
