import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { Ban, Copy, Eye, MoreHorizontal, Pause, Pencil, Play, Send, Trash2 } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { CampaignStatus, CampaignSummary } from '@/api/types'
import { useCampaignCommand, useDeleteCampaign, type CampaignCommand } from '@/features/campaign/api'
import { useCan } from '@/features/auth/permissions'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { ConfirmDialog } from '@/components/common/dialogs'

export const EDITABLE: CampaignStatus[] = ['DRAFT', 'REJECTED']

/** Các thao tác hợp lệ theo trạng thái (máy trạng thái chiến dịch). */
export function useCampaignOps(c: Pick<CampaignSummary, 'id' | 'name' | 'status'>) {
  const { t } = useTranslation()
  const can = useCan()
  const cmd = useCampaignCommand()
  const del = useDeleteCampaign()
  const navigate = useNavigate()
  const [confirm, setConfirm] = React.useState<null | 'cancel' | 'delete'>(null)

  const run = (k: CampaignCommand) =>
    cmd.mutate(
      { id: c.id, cmd: k },
      {
        onSuccess: (res) => {
          toast.success(t(`campaign.ops.${k}Done`))
          if (k === 'duplicate') navigate(`/campaigns/${res.id}/edit`)
        },
        onError: (e) => toast.error(errorMessage(e)),
      },
    )

  const s = c.status
  const ops = {
    edit: EDITABLE.includes(s) && can('CAMPAIGN_UPDATE'),
    submit: EDITABLE.includes(s) && can('CAMPAIGN_SUBMIT'),
    pause: s === 'RUNNING' && can('CAMPAIGN_UPDATE'),
    resume: s === 'PAUSED' && can('CAMPAIGN_UPDATE'),
    cancel: ['SCHEDULED', 'RUNNING', 'PAUSED', 'PENDING_APPROVAL'].includes(s) && can('CAMPAIGN_UPDATE'),
    duplicate: can('CAMPAIGN_CREATE'),
    remove: ['DRAFT', 'REJECTED', 'CANCELLED'].includes(s) && can('CAMPAIGN_DELETE'),
  }

  const dialogs = (
    <>
      <ConfirmDialog
        open={confirm === 'cancel'}
        onOpenChange={(o) => !o && setConfirm(null)}
        destructive
        title={t('campaign.ops.cancelTitle')}
        description={t('campaign.ops.cancelDesc', { name: c.name })}
        confirmLabel={t('campaign.ops.cancel')}
        loading={cmd.isPending}
        onConfirm={() => {
          run('cancel')
          setConfirm(null)
        }}
      />
      <ConfirmDialog
        open={confirm === 'delete'}
        onOpenChange={(o) => !o && setConfirm(null)}
        destructive
        title={t('campaign.ops.deleteTitle')}
        description={t('campaign.ops.deleteDesc', { name: c.name })}
        confirmLabel={t('common.delete')}
        loading={del.isPending}
        onConfirm={() =>
          del.mutate(c.id, {
            onSuccess: () => {
              toast.success(t('campaign.ops.deleteDone'))
              setConfirm(null)
              navigate('/campaigns')
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      />
    </>
  )

  return { ops, run, setConfirm, dialogs, pending: cmd.isPending }
}

export function CampaignRowMenu({ c }: { c: CampaignSummary }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { ops, run, setConfirm, dialogs } = useCampaignOps(c)
  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon-sm" aria-label={t('common.actions')} onClick={(e) => e.stopPropagation()}>
            <MoreHorizontal />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" onClick={(e) => e.stopPropagation()}>
          <DropdownMenuItem onSelect={() => navigate(`/campaigns/${c.id}`)}>
            <Eye />
            {t('common.view')}
          </DropdownMenuItem>
          {ops.edit && (
            <DropdownMenuItem onSelect={() => navigate(`/campaigns/${c.id}/edit`)}>
              <Pencil />
              {t('common.edit')}
            </DropdownMenuItem>
          )}
          {ops.submit && (
            <DropdownMenuItem onSelect={() => run('submit')}>
              <Send />
              {t('campaign.ops.submit')}
            </DropdownMenuItem>
          )}
          {ops.pause && (
            <DropdownMenuItem onSelect={() => run('pause')}>
              <Pause />
              {t('campaign.ops.pause')}
            </DropdownMenuItem>
          )}
          {ops.resume && (
            <DropdownMenuItem onSelect={() => run('resume')}>
              <Play />
              {t('campaign.ops.resume')}
            </DropdownMenuItem>
          )}
          {ops.duplicate && (
            <DropdownMenuItem onSelect={() => run('duplicate')}>
              <Copy />
              {t('campaign.ops.duplicate')}
            </DropdownMenuItem>
          )}
          {(ops.cancel || ops.remove) && <DropdownMenuSeparator />}
          {ops.cancel && (
            <DropdownMenuItem variant="destructive" onSelect={() => setConfirm('cancel')}>
              <Ban />
              {t('campaign.ops.cancel')}
            </DropdownMenuItem>
          )}
          {ops.remove && (
            <DropdownMenuItem variant="destructive" onSelect={() => setConfirm('delete')}>
              <Trash2 />
              {t('common.delete')}
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
      {dialogs}
    </>
  )
}
