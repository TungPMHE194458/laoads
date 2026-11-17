import {
  CircleDollarSign,
  Coins,
  Gift,
  Mail,
  MessageCircle,
  MessageSquareText,
  Music,
  Package,
  Phone,
  PhoneCall,
  Smartphone,
  Tv,
  Wifi,
  type LucideIcon,
} from 'lucide-react'
import type { ActionGroup, ActionType, CampaignAction, Channel, MessageParam, SenderChannel } from '@/api/types'

/** Kênh đầu số của hành động: SMS (bắt buộc) / USSD / brand WhatsApp (không bắt buộc — như "Brand" của laoads.com). */
export const SENDER_CHANNEL_OF: Partial<Record<ActionType, SenderChannel>> = { NOTIFICATION_SMS: 'SMS', NOTIFICATION_USSD: 'USSD', NOTIFICATION_WHATSAPP: 'WHATSAPP' }

/** WhatsApp gửi bằng template đã duyệt (không cần nội dung). */
export const usesWhatsappTemplate = (a: Pick<CampaignAction, 'type' | 'whatsappMode'>) => a.type === 'NOTIFICATION_WHATSAPP' && a.whatsappMode === 'TEMPLATE'
/** Hành động cần nội dung văn bản trong cấu hình hiện tại (WhatsApp chế độ template thì không). */
export const needsContent = (a: Pick<CampaignAction, 'type' | 'whatsappMode'>) => NEEDS_CONTENT.includes(a.type) && !usesWhatsappTemplate(a)

/** Hành động SMS chưa chọn đầu số → đầu số SMS mặc định (đầu số đầu tiên workspace được dùng). */
export function withDefaultSender(groups: ActionGroup[], defaultSms: string | null): ActionGroup[] {
  if (!defaultSms) return groups
  return groups.map((g) => ({ ...g, actions: g.actions.map((a) => (a.type === 'NOTIFICATION_SMS' && !a.senderId ? { ...a, senderId: defaultSms } : a)) }))
}

const ICONS: Record<ActionType, LucideIcon> = {
  NOTIFICATION_SMS: MessageSquareText,
  NOTIFICATION_EMAIL: Mail,
  NOTIFICATION_WHATSAPP: MessageCircle,
  NOTIFICATION_USSD: Phone,
  TOP_UP: Smartphone,
  ADD_MONEY: CircleDollarSign,
  ADD_PROMOTION: Gift,
  ADD_DATA: Wifi,
  ADD_SMS: MessageSquareText,
  ADD_CALL_MINUTES: PhoneCall,
  ADD_PACKAGE: Package,
  REGISTER_CRBT: Music,
  REGISTER_LAOTV: Tv,
}
export const actionIcon = (t: ActionType): LucideIcon => ICONS[t] ?? Coins

export const ACTION_CHANNEL: Partial<Record<ActionType, Channel>> = {
  NOTIFICATION_SMS: 'SMS',
  NOTIFICATION_EMAIL: 'EMAIL',
  NOTIFICATION_WHATSAPP: 'WHATSAPP',
  NOTIFICATION_USSD: 'USSD',
}

/** Hành động cần nội dung văn bản (SRS 3.2.3.3–5: SMS / WhatsApp / Email nhập nội dung tự do). */
export const NEEDS_CONTENT: ActionType[] = ['NOTIFICATION_SMS', 'NOTIFICATION_WHATSAPP', 'NOTIFICATION_EMAIL', 'NOTIFICATION_USSD']
/** SRS 3.2.3.3–5: nội dung SMS / WhatsApp / Email tối đa 360 ký tự (BE CampaignService.CONTENT_MAX). */
export const CONTENT_MAX = 360
export const CONTENT_LIMITED: ActionType[] = ['NOTIFICATION_SMS', 'NOTIFICATION_WHATSAPP', 'NOTIFICATION_EMAIL']
/** SRS 3.2.1.4–6: mô tả chiến dịch tối đa 360 ký tự. */
export const DESCRIPTION_MAX = 360
/** Hành động viễn thông cần số lượng */
export const NEEDS_AMOUNT: ActionType[] = ['TOP_UP', 'ADD_MONEY', 'ADD_PROMOTION', 'ADD_DATA', 'ADD_SMS', 'ADD_CALL_MINUTES']
/**
 * Chi phí một khách hàng của hành động — giống BE CampaignService.costPerTarget: hành động viễn thông có số lượng tính
 * theo đơn vị (đơn giá × số Kip / MB / SMS / phút được cộng, như bảng giá laoads.com); còn lại tính theo lượt.
 */
export const actionCost = (a: Pick<CampaignAction, 'type' | 'amount'>, unitPrice: number) => unitPrice * (NEEDS_AMOUNT.includes(a.type) ? Number(a.amount) || 0 : 1)
export const AMOUNT_UNIT: Partial<Record<ActionType, string>> = {
  TOP_UP: 'Kip',
  ADD_MONEY: 'Kip',
  ADD_PROMOTION: 'Kip',
  ADD_DATA: 'MB',
  ADD_SMS: 'SMS',
  ADD_CALL_MINUTES: 'min',
}


// ───── Tham số nội dung tin: {{key}} hoặc {{key|mặc định}} — cùng cú pháp với BE (MessageParams.TOKEN) ─────
const PARAM_RE = /\{\{\s*([A-Za-z0-9_]+)\s*(?:\|([^{}]*))?\}\}/g

const byKey = (params: MessageParam[]) => new Map(params.map((p) => [p.key, p]))

/** Tham số ngoài danh mục, giữ nguyên cách viết (vd `{{name}}`), không trùng lặp. */
export function unknownParams(text: string | null | undefined, params: MessageParam[]): string[] {
  if (!text) return []
  const known = byKey(params)
  return [...new Set([...text.matchAll(PARAM_RE)].filter((m) => !known.has(m[1].toLowerCase())).map((m) => m[0]))]
}

export const hasParams = (text: string | null | undefined) => !!text && new RegExp(PARAM_RE.source).test(text)

/** Độ dài lớn nhất sau khi thay: tham số = độ dài tối đa của giá trị (hoặc của giá trị mặc định nếu dài hơn). */
export function expandedLength(text: string | null | undefined, params: MessageParam[]): number {
  if (!text) return 0
  const known = byKey(params)
  return text.replace(PARAM_RE, (_, key: string, def?: string) => 'x'.repeat(Math.max(known.get(key.toLowerCase())?.maxLength ?? 0, def?.trim().length ?? 0))).length
}

/** Xem trước với giá trị ví dụ; tham số lạ → mặc định hoặc rỗng (giống BE lúc gửi). */
export function previewParams(text: string, params: MessageParam[]): string {
  const known = byKey(params)
  return text.replace(PARAM_RE, (_, key: string, def?: string) => known.get(key.toLowerCase())?.example ?? def?.trim() ?? '')
}
