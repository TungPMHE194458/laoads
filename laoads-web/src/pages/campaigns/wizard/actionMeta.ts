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
import type { ActionGroup, ActionType, Channel, SenderChannel } from '@/api/types'

/** Kênh đầu số của hành động (chỉ SMS / USSD chọn đầu số). */
export const SENDER_CHANNEL_OF: Partial<Record<ActionType, SenderChannel>> = { NOTIFICATION_SMS: 'SMS', NOTIFICATION_USSD: 'USSD' }

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

/** Hành động cần nội dung văn bản */
export const NEEDS_CONTENT: ActionType[] = ['NOTIFICATION_SMS', 'NOTIFICATION_EMAIL', 'NOTIFICATION_USSD']
/** Hành động viễn thông cần số lượng */
export const NEEDS_AMOUNT: ActionType[] = ['TOP_UP', 'ADD_MONEY', 'ADD_PROMOTION', 'ADD_DATA', 'ADD_SMS', 'ADD_CALL_MINUTES']
export const AMOUNT_UNIT: Partial<Record<ActionType, string>> = {
  TOP_UP: 'Kip',
  ADD_MONEY: 'Kip',
  ADD_PROMOTION: 'Kip',
  ADD_DATA: 'MB',
  ADD_SMS: 'SMS',
  ADD_CALL_MINUTES: 'min',
}

export const WA_TEMPLATES = ['unitel_promo_v3', 'unitel_welcome_v2', 'unitel_data_offer', 'unitel_umoney_kyc']
export const EVENT_TEMPLATES = ['SIM_ACTIVATION_D0', 'TOPUP_FIRST_TIME', 'PACKAGE_EXPIRING_D3', 'BIRTHDAY']
export const SMS_PARAMS = ['{{name}}', '{{isdn}}', '{{package}}', '{{link}}']
