#!/usr/bin/env node
/**
 * Kiểm tra key i18n: quét `t('a.b')` (key tĩnh) trong src/ và đối chiếu với src/locales/{vi,en,lo}.json.
 * Key động (t(`status.${x}`)) được khai báo trong DYNAMIC bên dưới — thêm vào đây khi thêm enum mới.
 *   node scripts/check-i18n.mjs          → báo key thiếu / thừa, exit 1 nếu thiếu
 *   node scripts/check-i18n.mjs --keys   → in toàn bộ key đang dùng
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const SRC = path.join(root, 'src')

const DYNAMIC = {
  campaignStatus: ['DRAFT', 'PENDING_APPROVAL', 'SCHEDULED', 'RUNNING', 'PAUSED', 'COMPLETED', 'REJECTED', 'CANCELLED'],
  segmentStatus: ['BUILDING', 'READY', 'FAILED', 'ARCHIVED'],
  segmentType: ['FILE', 'QUERY', 'EVENT', 'CAMPAIGN_OUTCOME'],
  priceStatus: ['DRAFT', 'LOCKED', 'ENDED'],
  activeStatus: ['ACTIVE', 'LOCKED', 'REVOKED'],
  role: ['SUPER_ADMIN', 'USER', 'OWNER', 'ADMIN', 'MEMBER'],
  channel: ['SMS', 'EMAIL', 'WHATSAPP', 'USSD'],
  actionType: ['TOP_UP', 'ADD_MONEY', 'ADD_PROMOTION', 'ADD_DATA', 'ADD_SMS', 'ADD_CALL_MINUTES', 'ADD_PACKAGE', 'REGISTER_CRBT', 'REGISTER_LAOTV', 'NOTIFICATION_SMS', 'NOTIFICATION_EMAIL', 'NOTIFICATION_WHATSAPP', 'NOTIFICATION_USSD'],
  perm: ['WORKSPACE_VIEW', 'WORKSPACE_UPDATE', 'MEMBER_MANAGE', 'CAMPAIGN_VIEW', 'CAMPAIGN_CREATE', 'CAMPAIGN_UPDATE', 'CAMPAIGN_SUBMIT', 'CAMPAIGN_APPROVE', 'CAMPAIGN_DELETE', 'SEGMENT_MANAGE', 'SECRET_KEY_MANAGE', 'REPORT_VIEW'],
  permGroup: ['WORKSPACE', 'MEMBER', 'CAMPAIGN', 'SEGMENT', 'SECRET_KEY', 'REPORT'],
  'nav.group': ['operate', 'workspace', 'system'],
  nav: ['dashboard', 'campaigns', 'approvals', 'segments', 'workspaceInfo', 'members', 'priceQuotes', 'auditLogs', 'adminWorkspaces', 'adminUsers', 'adminRoles', 'adminSending'],
  'layout.themes': ['light', 'dark', 'system'],
  metric: ['sent', 'opens', 'clicks', 'conversions'],
  'campaign.mode': ['OFFICIAL', 'TEST', 'OFFICIAL_desc', 'TEST_desc'],
  'campaign.type': ['STATIC', 'EVENT', 'STATIC_desc', 'EVENT_desc'],
  'campaign.ops': ['submitDone', 'pauseDone', 'resumeDone', 'cancelDone', 'duplicateDone'],
  'campaign.detail.bucket': ['DAY', 'WEEK', 'MONTH'],
  'campaign.history': ['CREATE', 'SUBMIT', 'APPROVE', 'REJECT', 'PAUSE', 'RESUME', 'CANCEL', 'START', 'COMPLETE'],
  'campaign.event': ['SIM_ACTIVATION_D0', 'TOPUP_FIRST_TIME', 'PACKAGE_EXPIRING_D3', 'BIRTHDAY'],
  'campaign.wizard.steps': ['basics', 'audience', 'actions', 'schedule', 'goal', 'review'],
  'campaign.wizard.stepDesc': ['basics', 'audience', 'actions', 'schedule', 'goal', 'review'],
  'campaign.wizard.mode': ['SEQUENTIAL', 'PARALLEL'],
  'campaign.wizard.schedule': ['NOW', 'ONCE', 'RECURRING', 'NOW_desc', 'ONCE_desc', 'RECURRING_desc'],
  'campaign.wizard.unit': ['DAY', 'WEEK', 'MONTH'],
  'campaign.wizard.goal': ['AUTO_OCS', 'MANUAL_FILE', 'NONE', 'AUTO_OCS_desc', 'MANUAL_FILE_desc', 'NONE_desc'],
  'campaign.wizard.err': ['name', 'segment', 'allocation', 'noAction', 'sender', 'content', 'smsLong', 'subject', 'amount', 'start', 'endBeforeStart', 'budget'],
  weekday: ['0', '1', '2', '3', '4', '5', '6'],
  langName: ['LO', 'EN', 'VI'],
  op: ['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'BETWEEN', 'IN', 'IS_NULL'],
  attr: ['province_code_active', 'customer_age', 'gender', 'tenure', 'act_status', 'device_type', 'infras_data_4g', 'is_smartphone', 'real_package_data', 'data_package', 'g_volume', 't_org_charge', 'laostv_total_volume', 'login_superapp', 'active_umoney', 'balance_basic', 'province', 'package', 'g_volume_gb'],
  auditAction: ['LOGIN', 'CREATE', 'UPDATE', 'DELETE', 'LOCK', 'UNLOCK', 'APPROVE', 'REJECT', 'SUBMIT', 'CHANGE_ROLE', 'RESET_PASSWORD', 'IMPORT', 'PAUSE', 'RESUME', 'CANCEL', 'EXPORT', 'CHANGE_PASSWORD', 'TEST'],
  auditEntity: ['AUTH', 'USER', 'WORKSPACE', 'MEMBER', 'ROLE', 'PRICE_QUOTE', 'CAMPAIGN', 'SEGMENT', 'SENDER_NUMBER', 'SETTING', 'CAMPAIGN_GROUP', 'API_KEY', 'ROLE_PERMISSION', 'AUDIT_LOG'],
  'sending.kind': ['SHORTCODE', 'BRANDNAME'],
  'sending.status': ['enabled', 'disabled'],
  sending: ['toggledOn', 'toggledOff'],
  'sending.sheet.tab': ['info', 'connection', 'scope', 'policy'],
  'sending.protocol': ['NONE', 'SMPP', 'HTTP', 'NONE_desc', 'SMPP_desc', 'HTTP_desc'],
  'sending.test': ['OK', 'TIMEOUT', 'REFUSED', 'UNKNOWN_HOST', 'AUTH_FAILED', 'REJECTED', 'PROTOCOL_ERROR', 'ERROR'],
  'sending.pol.silentMode': ['INHERIT', 'CUSTOM', 'OFF'],
  chart: ['chart', 'table'],
  // key nằm trong biểu thức điều kiện, regex không bắt được
  dashboard: ['morning', 'afternoon', 'evening'],
  adminWs: ['lockDesc', 'unlockDesc'],
}

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((d) => {
    const p = path.join(dir, d.name)
    if (d.isDirectory()) return d.name === 'mocks' || d.name === 'locales' ? [] : walk(p)
    return /\.(tsx?|jsx?)$/.test(d.name) ? [p] : []
  })
}

const used = new Set()
const re = /\bt\(\s*['"]([a-zA-Z0-9_.]+)['"]/g
for (const f of walk(SRC)) {
  const src = fs.readFileSync(f, 'utf8')
  for (const m of src.matchAll(re)) used.add(m[1])
}
for (const [prefix, vals] of Object.entries(DYNAMIC)) for (const v of vals) used.add(`${prefix}.${v}`)

if (process.argv.includes('--keys')) {
  console.log([...used].sort().join('\n'))
  process.exit(0)
}

const flat = (o, p = '') => Object.entries(o).flatMap(([k, v]) => (v && typeof v === 'object' ? flat(v, `${p}${k}.`) : [`${p}${k}`]))
let missingAny = false
for (const lang of ['vi', 'en', 'lo']) {
  const file = path.join(SRC, 'locales', `${lang}.json`)
  const have = new Set(flat(JSON.parse(fs.readFileSync(file, 'utf8'))))
  // key i18next plural: a_one / a_other
  const has = (k) => have.has(k) || have.has(`${k}_one`) || have.has(`${k}_other`)
  const missing = [...used].filter((k) => !has(k)).sort()
  const unused = [...have].filter((k) => !used.has(k) && !used.has(k.replace(/_(one|other)$/, ''))).sort()
  if (missing.length) missingAny = true
  console.log(`[${lang}] dùng ${used.size} key · thiếu ${missing.length} · không dùng ${unused.length}`)
  if (missing.length) console.log('  thiếu: ' + missing.join(', '))
  if (unused.length && process.argv.includes('--unused')) console.log('  không dùng: ' + unused.join(', '))
}
process.exit(missingAny ? 1 : 0)
