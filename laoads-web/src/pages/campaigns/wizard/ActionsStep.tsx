import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { ArrowDown, GripVertical, Plus, Trash2 } from 'lucide-react'
import type { ActionGroup, ActionType, CampaignAction } from '@/api/types'
import { NOTIFICATION_ACTIONS, TELECOM_ACTIONS } from '@/api/types'
import { useSenderNumbers } from '@/features/campaign/api'
import { Button } from '@/components/ui/button'
import { Input, Textarea } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Switch } from '@/components/ui/misc'
import { Badge } from '@/components/ui/badge'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Field } from '@/components/common/dialogs'
import { cn } from '@/lib/utils'
import { actionIcon, AMOUNT_UNIT, NEEDS_AMOUNT, NEEDS_CONTENT, SMS_PARAMS, WA_TEMPLATES } from './actionMeta'

const uid = () => Math.random().toString(36).slice(2, 9)

export function newAction(type: ActionType): CampaignAction {
  return { id: uid(), type, delayMinutes: 0, senderId: type === 'NOTIFICATION_SMS' ? '118' : null, subject: null, content: '', templateId: type === 'NOTIFICATION_WHATSAPP' ? WA_TEMPLATES[0] : null, amount: NEEDS_AMOUNT.includes(type) ? 0 : null, packageCode: null }
}
export function newGroup(name: string, allocationPercent: number, isControl = false): ActionGroup {
  return { id: uid(), name, allocationPercent, isControl, mode: 'SEQUENTIAL', actions: isControl ? [] : [newAction('NOTIFICATION_SMS')] }
}

/** Kiểm tra cấu hình hành động; trả về key lỗi i18n hoặc null. */
export function validateActions(groups: ActionGroup[]): string | null {
  const total = groups.reduce((s, g) => s + (Number(g.allocationPercent) || 0), 0)
  if (total !== 100) return 'campaign.wizard.err.allocation'
  if (!groups.some((g) => !g.isControl && g.actions.length)) return 'campaign.wizard.err.noAction'
  for (const g of groups)
    for (const a of g.actions) {
      if (NEEDS_CONTENT.includes(a.type) && !a.content?.trim()) return 'campaign.wizard.err.content'
      if (a.type === 'NOTIFICATION_SMS' && (a.content?.length ?? 0) > 459) return 'campaign.wizard.err.smsLong'
      if (a.type === 'NOTIFICATION_EMAIL' && !a.subject?.trim()) return 'campaign.wizard.err.subject'
      if (NEEDS_AMOUNT.includes(a.type) && !(Number(a.amount) > 0)) return 'campaign.wizard.err.amount'
    }
  return null
}

function ActionEditor({ a, index, sequential, onChange, onRemove }: { a: CampaignAction; index: number; sequential: boolean; onChange: (a: CampaignAction) => void; onRemove: () => void }) {
  const { t } = useTranslation()
  const senders = useSenderNumbers()
  const smsLen = a.content?.length ?? 0
  const segments = smsLen <= 160 ? 1 : Math.ceil(smsLen / 153)
  const insert = (p: string) => onChange({ ...a, content: `${a.content ?? ''}${a.content && !a.content.endsWith(' ') ? ' ' : ''}${p}` })
  return (
    <div className="rounded-xl border bg-card">
      <div className="flex items-center gap-3 border-b px-4 py-2.5">
        <GripVertical className="size-4 text-muted-foreground/60" />
        <span className="grid size-7 place-items-center rounded-md bg-accent text-brand-600">
          {React.createElement(actionIcon(a.type), { className: 'size-4' })}
        </span>
        <span className="text-sm font-semibold">
          {index + 1}. {t(`actionType.${a.type}`)}
        </span>
        {sequential && index > 0 && (
          <span className="ml-2 flex items-center gap-1.5 text-xs text-muted-foreground">
            {t('campaign.wizard.waitAfter')}
            <Input type="number" min={0} step="any" className="h-7 w-16 px-2 text-xs" value={Math.round(a.delayMinutes / 60)} onChange={(e) => onChange({ ...a, delayMinutes: Math.max(0, Number(e.target.value) || 0) * 60 })} />
            {t('campaign.wizard.hours')}
          </span>
        )}
        <Button variant="ghost" size="icon-sm" className="ml-auto text-muted-foreground hover:text-danger" onClick={onRemove} aria-label={t('common.delete')}>
          <Trash2 />
        </Button>
      </div>
      <div className="grid gap-4 p-4">
        {a.type === 'NOTIFICATION_SMS' && (
          <Field label={t('campaign.wizard.sender')} required>
            <Select value={a.senderId ?? ''} onValueChange={(v) => onChange({ ...a, senderId: v })} options={(senders.data ?? []).filter((s) => s.enabled).map((s) => ({ value: s.code, label: `${s.code} — ${s.description ?? ''}` }))} placeholder={t('campaign.wizard.senderPh')} />
          </Field>
        )}
        {a.type === 'NOTIFICATION_EMAIL' && (
          <Field label={t('campaign.wizard.subject')} required>
            <Input value={a.subject ?? ''} onChange={(e) => onChange({ ...a, subject: e.target.value })} />
          </Field>
        )}
        {a.type === 'NOTIFICATION_WHATSAPP' && (
          <Field label={t('campaign.wizard.waTemplate')} required hint={t('campaign.wizard.waHint')}>
            <Select value={a.templateId ?? ''} onValueChange={(v) => onChange({ ...a, templateId: v })} options={WA_TEMPLATES.map((w) => ({ value: w, label: w }))} />
          </Field>
        )}
        {NEEDS_CONTENT.includes(a.type) && (
          <Field
            label={t('campaign.wizard.content')}
            required
            hint={
              a.type === 'NOTIFICATION_SMS' ? (
                <span className={cn('tabular', smsLen > 459 && 'text-danger')}>{t('campaign.wizard.smsCounter', { n: smsLen, seg: segments })}</span>
              ) : undefined
            }
          >
            <Textarea rows={a.type === 'NOTIFICATION_EMAIL' ? 6 : 3} value={a.content ?? ''} onChange={(e) => onChange({ ...a, content: e.target.value })} placeholder={t('campaign.wizard.contentPh')} />
            <div className="flex flex-wrap gap-1.5">
              {SMS_PARAMS.map((p) => (
                <button key={p} type="button" onClick={() => insert(p)} className="cursor-pointer rounded-md border border-dashed px-2 py-0.5 font-mono text-[11px] text-muted-foreground transition hover:border-brand-300 hover:text-brand-600">
                  + {p}
                </button>
              ))}
            </div>
          </Field>
        )}
        {NEEDS_AMOUNT.includes(a.type) && (
          <Field label={`${t('campaign.wizard.amount')} (${AMOUNT_UNIT[a.type]})`} required>
            <Input type="number" min={0} step="any" value={a.amount ?? 0} onChange={(e) => onChange({ ...a, amount: Number(e.target.value) })} className="max-w-48" />
          </Field>
        )}
        {(a.type === 'ADD_PACKAGE' || a.type === 'REGISTER_CRBT' || a.type === 'REGISTER_LAOTV') && (
          <Field label={t('campaign.wizard.packageCode')}>
            <Input value={a.packageCode ?? ''} onChange={(e) => onChange({ ...a, packageCode: e.target.value })} className="max-w-60 font-mono" placeholder="M50" />
          </Field>
        )}
      </div>
    </div>
  )
}

function AddActionMenu({ onAdd }: { onAdd: (t: ActionType) => void }) {
  const { t } = useTranslation()
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" className="w-full border-dashed">
          <Plus />
          {t('campaign.wizard.addAction')}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="center" className="w-64">
        <DropdownMenuLabel>{t('campaign.wizard.notifyActions')}</DropdownMenuLabel>
        {NOTIFICATION_ACTIONS.map((a) => {
          const I = actionIcon(a)
          return (
            <DropdownMenuItem key={a} onSelect={() => onAdd(a)}>
              <I />
              {t(`actionType.${a}`)}
              {a === 'NOTIFICATION_USSD' && <Badge variant="brand" className="ml-auto">{t('common.new')}</Badge>}
            </DropdownMenuItem>
          )
        })}
        <DropdownMenuSeparator />
        <DropdownMenuLabel>{t('campaign.wizard.telecomActions')}</DropdownMenuLabel>
        {TELECOM_ACTIONS.map((a) => {
          const I = actionIcon(a)
          return (
            <DropdownMenuItem key={a} onSelect={() => onAdd(a)}>
              <I />
              {t(`actionType.${a}`)}
            </DropdownMenuItem>
          )
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export function ActionsStep({ groups, onChange }: { groups: ActionGroup[]; onChange: (g: ActionGroup[]) => void }) {
  const { t } = useTranslation()
  const total = groups.reduce((s, g) => s + (Number(g.allocationPercent) || 0), 0)
  const update = (id: string, patch: Partial<ActionGroup>) => onChange(groups.map((g) => (g.id === id ? { ...g, ...patch } : g)))
  const hasControl = groups.some((g) => g.isControl)

  return (
    <div className="grid gap-5">
      <div className={cn('flex items-center justify-between rounded-xl border px-4 py-3 text-sm', total === 100 ? 'bg-success-soft/60 border-success/20' : 'bg-warning-soft/70 border-warning/25')}>
        <span>{t('campaign.wizard.allocationHint')}</span>
        <span className="font-semibold tabular">{t('campaign.wizard.allocationTotal', { n: total })}</span>
      </div>

      {groups.map((g, gi) => (
        <section key={g.id} className={cn('rounded-2xl border p-4', g.isControl ? 'bg-muted/40' : 'bg-background')}>
          <div className="mb-4 flex flex-wrap items-end gap-3">
            <Field label={t('campaign.wizard.groupName')} className="min-w-48 flex-1">
              <Input value={g.name} onChange={(e) => update(g.id, { name: e.target.value })} />
            </Field>
            <Field label={t('campaign.wizard.allocation')} className="w-32">
              <div className="relative">
                <Input type="number" min={0} max={100} step="any" value={g.allocationPercent} onChange={(e) => update(g.id, { allocationPercent: Number(e.target.value) })} className="pr-7" />
                <span className="absolute top-1/2 right-3 -translate-y-1/2 text-sm text-muted-foreground">%</span>
              </div>
            </Field>
            {!g.isControl && (
              <Field label={t('campaign.wizard.execMode')} className="w-44">
                <Select value={g.mode} onValueChange={(v) => update(g.id, { mode: v as ActionGroup['mode'] })} options={(['SEQUENTIAL', 'PARALLEL'] as const).map((m) => ({ value: m, label: t(`campaign.wizard.mode.${m}`) }))} />
              </Field>
            )}
            {groups.length > 1 && (
              <Button variant="ghost" size="icon" className="text-muted-foreground hover:text-danger" onClick={() => onChange(groups.filter((x) => x.id !== g.id))} aria-label={t('common.delete')}>
                <Trash2 />
              </Button>
            )}
          </div>
          {g.isControl ? (
            <p className="text-sm text-muted-foreground">{t('campaign.wizard.controlNote')}</p>
          ) : (
            <div className="grid gap-2">
              {g.actions.map((a, i) => (
                <div key={a.id}>
                  {i > 0 && (
                    <div className="flex justify-center py-1 text-muted-foreground">
                      <ArrowDown className="size-4" />
                    </div>
                  )}
                  <ActionEditor
                    a={a}
                    index={i}
                    sequential={g.mode === 'SEQUENTIAL'}
                    onChange={(na) => update(g.id, { actions: g.actions.map((x) => (x.id === a.id ? na : x)) })}
                    onRemove={() => update(g.id, { actions: g.actions.filter((x) => x.id !== a.id) })}
                  />
                </div>
              ))}
              <div className="pt-2">
                <AddActionMenu onAdd={(type) => update(g.id, { actions: [...g.actions, newAction(type)] })} />
              </div>
            </div>
          )}
          {gi === 0 && null}
        </section>
      ))}

      <div className="flex flex-wrap gap-2">
        <Button variant="soft" onClick={() => onChange([...groups, newGroup(`${t('campaign.wizard.group')} ${String.fromCharCode(65 + groups.filter((x) => !x.isControl).length)}`, 0)])}>
          <Plus />
          {t('campaign.wizard.addGroup')}
        </Button>
        <label className="ml-auto flex items-center gap-2 text-sm">
          <Switch
            checked={hasControl}
            onCheckedChange={(on) => (on ? onChange([...groups, newGroup(t('campaign.wizard.control'), 0, true)]) : onChange(groups.filter((g) => !g.isControl)))}
          />
          {t('campaign.wizard.useControl')}
        </label>
      </div>
    </div>
  )
}
