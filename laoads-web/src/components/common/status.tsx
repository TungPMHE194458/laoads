import { useTranslation } from 'react-i18next'
import { Mail, MessageCircle, MessageSquareText, Phone } from 'lucide-react'
import { StatusDot, type BadgeVariant } from '@/components/ui/badge'
import type { CampaignStatus, Channel, PriceQuoteStatus, SegmentStatus } from '@/api/types'
import { cn } from '@/lib/utils'

const CAMPAIGN: Record<CampaignStatus, BadgeVariant> = {
  DRAFT: 'neutral',
  PENDING_APPROVAL: 'warning',
  SCHEDULED: 'info',
  RUNNING: 'success',
  PAUSED: 'outline',
  COMPLETED: 'brand',
  REJECTED: 'danger',
  CANCELLED: 'neutral',
}
export function CampaignStatusBadge({ status }: { status: CampaignStatus }) {
  const { t } = useTranslation()
  return <StatusDot variant={CAMPAIGN[status]}>{t(`campaignStatus.${status}`)}</StatusDot>
}

const SEGMENT: Record<SegmentStatus, BadgeVariant> = { BUILDING: 'info', READY: 'success', FAILED: 'danger', ARCHIVED: 'neutral' }
export function SegmentStatusBadge({ status }: { status: SegmentStatus }) {
  const { t } = useTranslation()
  return <StatusDot variant={SEGMENT[status]}>{t(`segmentStatus.${status}`)}</StatusDot>
}

const PRICE: Record<PriceQuoteStatus, BadgeVariant> = { DRAFT: 'neutral', LOCKED: 'success', ENDED: 'outline' }
export function PriceStatusBadge({ status }: { status: PriceQuoteStatus }) {
  const { t } = useTranslation()
  return <StatusDot variant={PRICE[status]}>{t(`priceStatus.${status}`)}</StatusDot>
}

export function ActiveBadge({ status }: { status: 'ACTIVE' | 'LOCKED' | 'REVOKED' }) {
  const { t } = useTranslation()
  return <StatusDot variant={status === 'ACTIVE' ? 'success' : 'danger'}>{t(`activeStatus.${status}`)}</StatusDot>
}

/** Biểu tượng + màu cố định theo kênh (màu theo entity, không theo thứ hạng). */
export const CHANNEL_META: Record<Channel, { icon: typeof Mail; color: string }> = {
  SMS: { icon: MessageSquareText, color: 'var(--series-1)' },
  EMAIL: { icon: Mail, color: 'var(--series-2)' },
  WHATSAPP: { icon: MessageCircle, color: 'var(--series-3)' },
  USSD: { icon: Phone, color: 'var(--series-4)' },
}

export function ChannelChips({ channels, className }: { channels: Channel[]; className?: string }) {
  const { t } = useTranslation()
  return (
    <div className={cn('flex flex-wrap gap-1', className)}>
      {channels.map((c) => {
        const M = CHANNEL_META[c]
        return (
          <span
            key={c}
            title={t(`channel.${c}`)}
            className="inline-flex items-center gap-1 rounded-md border bg-card px-1.5 py-0.5 text-[11px] font-medium text-foreground/80"
          >
            <M.icon className="size-3" style={{ color: M.color }} />
            {t(`channel.${c}`)}
          </span>
        )
      })}
    </div>
  )
}
