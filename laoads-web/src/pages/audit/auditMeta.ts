import type { BadgeVariant } from '@/components/ui/badge'

/** Giá trị BE thực ghi (@Auditable actionType / entityType). */
export const AUDIT_ACTIONS = ['LOGIN', 'CHANGE_PASSWORD', 'CREATE', 'UPDATE', 'DELETE', 'SUBMIT', 'APPROVE', 'REJECT', 'PAUSE', 'RESUME', 'CANCEL', 'LOCK', 'UNLOCK', 'CHANGE_ROLE', 'RESET_PASSWORD', 'IMPORT', 'EXPORT', 'TEST']
export const AUDIT_ENTITIES = ['USER', 'WORKSPACE', 'MEMBER', 'ROLE_PERMISSION', 'PRICE_QUOTE', 'CAMPAIGN', 'CAMPAIGN_GROUP', 'SEGMENT', 'SENDER_NUMBER', 'SETTING', 'API_KEY', 'AUDIT_LOG']
export const AUDIT_RESULTS = ['SUCCESS', 'FAILURE'] as const
export const AUDIT_TONE: Record<string, BadgeVariant> = { CREATE: 'success', APPROVE: 'success', UNLOCK: 'success', RESUME: 'success', DELETE: 'danger', REJECT: 'danger', CANCEL: 'danger', LOCK: 'warning', PAUSE: 'warning', UPDATE: 'info', CHANGE_ROLE: 'info', SUBMIT: 'brand' }
