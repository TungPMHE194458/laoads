import type { CampaignInput, CampaignSchedule, ScheduleFrame } from '@/api/types'
import { fmtDateTime } from '@/lib/format'

type T = (k: string, o?: Record<string, unknown>) => string

/** Tối đa 10 khung thời gian (BE CampaignService.validateSchedule). */
export const FRAMES_MAX = 10

/** Các khung thời gian thực dùng — giống BE ScheduleDef.effectiveFrames(): không có `frames` thì là 1 khung từ các trường chính. */
export function effectiveFrames(s: CampaignSchedule): ScheduleFrame[] {
  if (s.frames?.length) return s.frames
  return [{ every: s.every ?? 1, unit: s.unit ?? 'WEEK', weekdays: s.weekdays ?? [], monthDays: s.monthDays ?? null, sendTime: s.sendTime ?? '09:00' }]
}

/** "Mỗi 1 tuần · T2, T5 lúc 09:00" cho một khung. */
export function frameRule(f: ScheduleFrame, t: T): string {
  const unit = t(`campaign.wizard.unit.${f.unit ?? 'WEEK'}`)
  const days =
    f.unit === 'WEEK' && f.weekdays?.length
      ? ' · ' + [...f.weekdays].sort((a, b) => ((a + 6) % 7) - ((b + 6) % 7)).map((d) => t(`weekday.${d}`)).join(', ')
      : f.unit === 'MONTH' && f.monthDays?.length
        ? ' · ' + t('campaign.detail.onDays', { days: [...f.monthDays].sort((a, b) => a - b).join(', ') })
        : ''
  return t('campaign.detail.ruleRecurring', { every: f.every ?? 1, unit, days, time: f.sendTime ?? '09:00' })
}

/** SRS 3.2.8.8 #11: diễn giải lịch chạy thành câu; nhiều khung thời gian nối bằng "; ". */
export function scheduleRule(c: Pick<CampaignInput, 'type' | 'schedule' | 'eventTemplate'>, t: T): string {
  const s = c.schedule
  if (c.type === 'EVENT') return t('campaign.detail.ruleEvent', { event: c.eventTemplate ? t(`campaign.event.${c.eventTemplate}`, { defaultValue: c.eventTemplate }) : '—' })
  if (s.type === 'NOW') return t('campaign.detail.ruleNow')
  if (s.type === 'ONCE') return t('campaign.detail.ruleOnce', { at: fmtDateTime(s.startAt) })
  const rule = effectiveFrames(s)
    .map((f) => frameRule(f, t))
    .join('; ')
  return s.maxOccurrences ? `${rule} · ${t('campaign.detail.ruleMax', { n: s.maxOccurrences })}` : rule
}
