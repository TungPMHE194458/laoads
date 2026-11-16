/* Trạng thái form đầu số (drawer) ↔ SenderNumberInput gửi BE. Số giữ dạng chuỗi để ô nhập bỏ trống được. */
import type { GatewayProtocol, SenderChannel, SenderKind, SenderNumber, SenderNumberInput, SenderPolicy } from '@/api/types'
import { SENDER_CHANNELS } from '@/api/types'

export type SilentMode = 'INHERIT' | 'CUSTOM' | 'OFF'
export const SILENT_MODES: SilentMode[] = ['INHERIT', 'CUSTOM', 'OFF']
export const PROTOCOLS: GatewayProtocol[] = ['NONE', 'SMPP', 'HTTP']
export type SenderTab = 'info' | 'connection' | 'scope' | 'policy'

export interface SenderForm {
  code: string
  kind: SenderKind
  description: string
  enabled: boolean
  channels: SenderChannel[]
  allWorkspaces: boolean
  workspaceIds: number[]
  protocol: GatewayProtocol
  host: string
  port: string
  systemType: string
  url: string
  username: string
  password: string
  clearPassword: boolean
  tps: string
  retryOn: boolean
  retryMax: string
  intervalOn: boolean
  retryInterval: string
  silentMode: SilentMode
  silentFrom: string
  silentTo: string
  dailyLimit: string
}

const str = (v: string | number | null | undefined) => (v == null ? '' : String(v))
const numOrNull = (v: string) => (v.trim() === '' ? null : Number(v))
const textOrNull = (v: string) => (v.trim() === '' ? null : v.trim())

export function formFromSender(s: SenderNumber | null): SenderForm {
  const g = s?.gateway
  const p = s?.policy
  return {
    code: s?.code ?? '',
    kind: s?.kind ?? 'SHORTCODE',
    description: s?.description ?? '',
    enabled: s?.enabled ?? true,
    channels: s?.channels ?? ['SMS'],
    allWorkspaces: s?.allWorkspaces ?? true,
    workspaceIds: s?.workspaceIds ?? [],
    protocol: g?.protocol ?? 'NONE',
    host: str(g?.host),
    port: str(g?.port ?? 2775),
    systemType: str(g?.systemType),
    url: str(g?.url),
    username: str(g?.username),
    password: '',
    clearPassword: false,
    tps: str(g?.tps),
    retryOn: p?.retryMaxAttempts != null,
    retryMax: str(p?.retryMaxAttempts),
    intervalOn: p?.retryIntervalMinutes != null,
    retryInterval: str(p?.retryIntervalMinutes),
    silentMode: p?.silentHoursEnabled == null ? 'INHERIT' : p.silentHoursEnabled ? 'CUSTOM' : 'OFF',
    silentFrom: p?.silentFrom ?? '21:00',
    silentTo: p?.silentTo ?? '07:00',
    dailyLimit: str(p?.dailyLimit),
  }
}

export function formToInput(f: SenderForm): SenderNumberInput {
  const smpp = f.protocol === 'SMPP'
  const http = f.protocol === 'HTTP'
  const none = f.protocol === 'NONE'
  return {
    code: f.code.trim().toUpperCase(),
    kind: f.kind,
    description: textOrNull(f.description),
    enabled: f.enabled,
    // Brandname chỉ gửi SMS — bỏ USSD nếu người dùng đổi loại sau khi đã tick
    channels: SENDER_CHANNELS.filter((c) => f.channels.includes(c) && (f.kind === 'SHORTCODE' || c === 'SMS')),
    allWorkspaces: f.allWorkspaces,
    workspaceIds: f.allWorkspaces ? [] : f.workspaceIds,
    gateway: {
      protocol: f.protocol,
      host: smpp ? textOrNull(f.host) : null,
      port: smpp ? numOrNull(f.port) : null,
      systemType: smpp ? textOrNull(f.systemType) : null,
      url: http ? textOrNull(f.url) : null,
      username: none ? null : textOrNull(f.username),
      password: none || f.clearPassword || f.password === '' ? null : f.password,
      clearPassword: none ? undefined : f.clearPassword || undefined,
      tps: none ? null : numOrNull(f.tps),
    },
    policy: {
      retryMaxAttempts: f.retryOn ? numOrNull(f.retryMax) : null,
      retryIntervalMinutes: f.intervalOn ? numOrNull(f.retryInterval) : null,
      silentHoursEnabled: f.silentMode === 'INHERIT' ? null : f.silentMode === 'CUSTOM',
      silentFrom: f.silentMode === 'CUSTOM' ? f.silentFrom : null,
      silentTo: f.silentMode === 'CUSTOM' ? f.silentTo : null,
      dailyLimit: numOrNull(f.dailyLimit),
    },
  }
}

/** Đầu số có trường chính sách nào ghi đè chính sách chung không. */
export const hasCustomPolicy = (p: SenderPolicy) =>
  p.retryMaxAttempts != null || p.retryIntervalMinutes != null || p.silentHoursEnabled != null || p.dailyLimit != null

/** Tab chứa field lỗi (theo `errors[].field` của BE). */
export function tabOfField(field: string): SenderTab {
  if (field.startsWith('gateway.')) return 'connection'
  if (field.startsWith('workspaceIds')) return 'scope'
  if (field.startsWith('policy.')) return 'policy'
  return 'info'
}
