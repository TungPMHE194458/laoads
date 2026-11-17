import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { ArrowDown, Check, ChevronDown, ChevronUp, FileText, Link2, PenLine, Plus, Trash2 } from 'lucide-react'
import type { ActionGroup, ActionType, CampaignAction, MessageParam, SenderChannel } from '@/api/types'
import { NOTIFICATION_ACTIONS, TELECOM_ACTIONS } from '@/api/types'
import { useAvailableSenders, useMessageParams, useWhatsappTemplates } from '@/features/campaign/api'
import { SearchInput } from '@/components/common/SearchInput'
import { Button } from '@/components/ui/button'
import { Input, Textarea } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton, Switch, Tabs, TabsList, TabsTrigger } from '@/components/ui/misc'
import { Badge } from '@/components/ui/badge'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Field } from '@/components/common/dialogs'
import { cn } from '@/lib/utils'
import { actionIcon, AMOUNT_UNIT, CONTENT_LIMITED, CONTENT_MAX, expandedLength, hasParams, NEEDS_AMOUNT, NEEDS_CONTENT, needsContent, previewParams, SENDER_CHANNEL_OF, unknownParams, usesWhatsappTemplate } from './actionMeta'

const uid = () => Math.random().toString(36).slice(2, 9)

/** `smsSender`: đầu số SMS mặc định (đầu tiên workspace được dùng); null = để trống, wizard tự điền khi lưu. */
export function newAction(type: ActionType, smsSender: string | null = null): CampaignAction {
  return {
    id: uid(),
    type,
    delayMinutes: 0,
    senderId: type === 'NOTIFICATION_SMS' ? smsSender : null,
    subject: null,
    content: '',
    templateId: null,
    amount: NEEDS_AMOUNT.includes(type) ? 0 : null,
    packageCode: null,
    whatsappMode: type === 'NOTIFICATION_WHATSAPP' ? 'CONTENT' : null,
  }
}
export function newGroup(name: string, allocationPercent: number, isControl = false, smsSender: string | null = null): ActionGroup {
  return { id: uid(), name, allocationPercent, isControl, mode: 'SEQUENTIAL', actions: isControl ? [] : [newAction('NOTIFICATION_SMS', smsSender)] }
}

/**
 * Kiểm tra cấu hình hành động; trả về key lỗi i18n hoặc null.
 * `params`: danh mục tham số nội dung — rỗng (chưa tải) thì bỏ qua kiểm tra tham số lạ, BE vẫn chặn khi gửi duyệt.
 */
export function validateActions(groups: ActionGroup[], params: MessageParam[] = []): string | null {
  const total = groups.reduce((s, g) => s + (Number(g.allocationPercent) || 0), 0)
  if (total !== 100) return 'campaign.wizard.err.allocation'
  if (!groups.some((g) => !g.isControl && g.actions.length)) return 'campaign.wizard.err.noAction'
  for (const g of groups)
    for (const a of g.actions) {
      if (a.type === 'NOTIFICATION_SMS' && !a.senderId) return 'campaign.wizard.err.sender'
      if (usesWhatsappTemplate(a)) {
        if (!a.templateId) return 'campaign.wizard.err.whatsappTemplate'
        continue
      }
      if (NEEDS_CONTENT.includes(a.type) && !a.content?.trim()) return 'campaign.wizard.err.content'
      if (params.length && !g.isControl && (unknownParams(a.content, params).length || unknownParams(a.subject, params).length)) return 'campaign.wizard.err.unknownParam'
      if (CONTENT_LIMITED.includes(a.type) && expandedLength(a.content, params) > CONTENT_MAX) return 'campaign.wizard.err.contentLong'
      if (a.type === 'NOTIFICATION_EMAIL' && !a.subject?.trim()) return 'campaign.wizard.err.subject'
      if (NEEDS_AMOUNT.includes(a.type) && !(Number(a.amount) > 0)) return 'campaign.wizard.err.amount'
    }
  return null
}

/** Chọn đầu số cho hành động SMS (bắt buộc) / USSD (không bắt buộc) — chỉ đầu số workspace được dùng. */
function SenderSelect({ a, channel, defaultSms, onChange }: { a: CampaignAction; channel: SenderChannel; defaultSms: string | null; onChange: (a: CampaignAction) => void }) {
  const { t } = useTranslation()
  const list = useAvailableSenders(channel)
  const required = channel === 'SMS'
  const value = a.senderId ?? (required ? defaultSms : null) ?? ''
  const available = (list.data ?? []).map((s) => ({ value: s.code, label: `${s.code} — ${s.description ?? t(`sending.kind.${s.kind}`)}` }))
  // Đầu số đã lưu nhưng không còn dùng được (bị tắt, xoá, ra khỏi phạm vi) — vẫn hiện để người dùng thấy và đổi
  const unavailable = !!a.senderId && !list.isLoading && !available.some((o) => o.value === a.senderId)
  const options = unavailable ? [{ value: a.senderId!, label: `${a.senderId} (${t('campaign.wizard.senderUnavailable')})` }, ...available] : available
  const brand = channel === 'WHATSAPP'
  return (
    <Field
      label={brand ? t('campaign.wizard.brand') : t('campaign.wizard.sender')}
      required={required}
      error={unavailable ? t('campaign.wizard.senderUnavailableHint') : null}
      hint={!list.isLoading && !list.data?.length ? t(brand ? 'campaign.wizard.noBrand' : 'campaign.wizard.noSender') : undefined}
    >
      <Select
        value={value}
        onValueChange={(v) => onChange({ ...a, senderId: v || null })}
        options={options}
        allLabel={required ? undefined : t(brand ? 'campaign.wizard.brandDefault' : 'campaign.wizard.senderNone')}
        placeholder={t('campaign.wizard.senderPh')}
      />
    </Field>
  )
}

/** "Chọn template" WhatsApp (như laoads.com): tìm theo tên, chỉ template đã được duyệt. */
function WhatsappTemplatePicker({ value, onChange }: { value: string | null | undefined; onChange: (id: string) => void }) {
  const { t } = useTranslation()
  const [query, setQuery] = React.useState('')
  const list = useWhatsappTemplates(query)
  const items = list.data ?? []
  return (
    <Field label={t('campaign.wizard.waTemplate')} required hint={t('campaign.wizard.waTemplateHint')}>
      <SearchInput value={query} onChange={setQuery} placeholder={t('campaign.wizard.waTemplateSearch')} className="w-full" />
      {list.isLoading ? (
        <Skeleton className="h-40" />
      ) : !items.length ? (
        <p className="rounded-lg border border-dashed px-3 py-6 text-center text-sm text-muted-foreground">{query ? t('common.noResult') : t('campaign.wizard.waNoTemplate')}</p>
      ) : (
        <div role="listbox" aria-label={t('campaign.wizard.waTemplate')} className="max-h-60 divide-y overflow-y-auto rounded-lg border">
          {items.map((tp) => {
            const on = tp.id === value
            return (
              <button
                key={tp.id}
                type="button"
                role="option"
                aria-selected={on}
                onClick={() => onChange(tp.id)}
                className={cn('flex w-full cursor-pointer items-center gap-3 px-3 py-2 text-left text-sm transition', on ? 'bg-accent text-accent-foreground' : 'hover:bg-muted/70')}
              >
                <FileText className="size-4 shrink-0 text-muted-foreground" />
                <span className="min-w-0 flex-1 truncate font-mono text-[13px]">{tp.name}</span>
                <Badge variant="outline" className="uppercase">
                  {tp.language}
                </Badge>
                {on && <Check className="size-4 text-brand-600" />}
              </button>
            )
          })}
        </div>
      )}
      {value && !items.some((x) => x.id === value) && (
        <p className="text-xs text-muted-foreground">
          {t('campaign.wizard.waSelected')}: <span className="font-mono text-foreground">{value}</span>
        </p>
      )}
    </Field>
  )
}

function ActionEditor({
  a,
  index,
  count,
  sequential,
  defaultSms,
  onChange,
  onRemove,
  onMove,
}: {
  a: CampaignAction
  index: number
  count: number
  sequential: boolean
  defaultSms: string | null
  onChange: (a: CampaignAction) => void
  onRemove: () => void
  onMove: (dir: -1 | 1) => void
}) {
  const { t } = useTranslation()
  const senderChannel = SENDER_CHANNEL_OF[a.type]
  const params = useMessageParams().data ?? []
  const withParams = hasParams(a.content)
  // có tham số → đếm theo độ dài tối đa sau khi thay (giá trị thật mỗi thuê bao có thể ngắn hơn)
  const len = expandedLength(a.content, params)
  const segments = len <= 160 ? 1 : Math.ceil(len / 153)
  const limited = CONTENT_LIMITED.includes(a.type)
  const counterKey = a.type === 'NOTIFICATION_SMS' ? (withParams ? 'campaign.wizard.smsCounterParams' : 'campaign.wizard.smsCounter') : withParams ? 'campaign.wizard.contentCounterParams' : 'campaign.wizard.contentCounter'
  const [link, setLink] = React.useState('')
  const linkOk = /^https?:\/\/\S+$/i.test(link.trim())
  // SRS 3.2.3.6: chèn link vào cuối nội dung và bật theo dõi click (engine thay bằng link tracking khi gửi)
  const insertLink = () => {
    if (!linkOk) return
    onChange({ ...a, content: `${a.content ?? ''}${a.content && !a.content.endsWith(' ') ? ' ' : ''}${link.trim()}`, trackLinks: true })
    setLink('')
  }
  // SRS 3.2.3 TH4: đổi loại hành động tại chỗ — giữ id và thời gian chờ, các trường khác theo loại mới
  const changeType = (type: ActionType) => onChange({ ...newAction(type, defaultSms), id: a.id, delayMinutes: a.delayMinutes, content: NEEDS_CONTENT.includes(type) ? a.content : '' })
  const waTemplate = usesWhatsappTemplate(a)
  const unknown = [...unknownParams(a.content, params), ...unknownParams(a.subject, params)]
  const sampleParam = params.find((p) => p.source === 'SUBSCRIBER') ?? params[0]
  const insert = (p: string) => onChange({ ...a, content: `${a.content ?? ''}${a.content && !a.content.endsWith(' ') ? ' ' : ''}${p}` })
  return (
    <div className="rounded-xl border bg-card">
      <div className="flex flex-wrap items-center gap-3 border-b px-4 py-2.5">
        {/* Đổi thứ tự hành động trong nhóm (laoads.com kéo thả) */}
        <span className="flex flex-col">
          <button type="button" disabled={index === 0} onClick={() => onMove(-1)} aria-label={t('campaign.wizard.moveUp')} className="cursor-pointer rounded text-muted-foreground hover:text-foreground disabled:cursor-not-allowed disabled:opacity-30">
            <ChevronUp className="size-3.5" />
          </button>
          <button type="button" disabled={index === count - 1} onClick={() => onMove(1)} aria-label={t('campaign.wizard.moveDown')} className="cursor-pointer rounded text-muted-foreground hover:text-foreground disabled:cursor-not-allowed disabled:opacity-30">
            <ChevronDown className="size-3.5" />
          </button>
        </span>
        <span className="grid size-7 place-items-center rounded-md bg-accent text-brand-600">
          {React.createElement(actionIcon(a.type), { className: 'size-4' })}
        </span>
        <span className="text-sm font-semibold tabular">{index + 1}.</span>
        <Select
          className="h-8 w-56 text-sm font-semibold"
          aria-label={t('campaign.wizard.changeType')}
          value={a.type}
          onValueChange={(v) => v !== a.type && changeType(v as ActionType)}
          options={[...NOTIFICATION_ACTIONS, ...TELECOM_ACTIONS].map((x) => ({ value: x, label: t(`actionType.${x}`) }))}
        />
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
        {senderChannel && <SenderSelect a={a} channel={senderChannel} defaultSms={defaultSms} onChange={onChange} />}
        {a.type === 'NOTIFICATION_EMAIL' && (
          <Field label={t('campaign.wizard.subject')} required>
            <Input value={a.subject ?? ''} onChange={(e) => onChange({ ...a, subject: e.target.value })} />
          </Field>
        )}
        {a.type === 'NOTIFICATION_WHATSAPP' && (
          <Tabs value={waTemplate ? 'TEMPLATE' : 'CONTENT'} onValueChange={(v) => onChange({ ...a, whatsappMode: v as 'CONTENT' | 'TEMPLATE' })}>
            <TabsList>
              <TabsTrigger value="CONTENT">
                <PenLine />
                {t('campaign.wizard.waModeContent')}
              </TabsTrigger>
              <TabsTrigger value="TEMPLATE">
                <FileText />
                {t('campaign.wizard.waModeTemplate')}
              </TabsTrigger>
            </TabsList>
          </Tabs>
        )}
        {waTemplate && <WhatsappTemplatePicker value={a.templateId} onChange={(id) => onChange({ ...a, templateId: id })} />}
        {needsContent(a) && (
          <Field
            label={t('campaign.wizard.content')}
            required
            hint={limited ? <span className={cn('tabular', len > CONTENT_MAX && 'text-danger')}>{t(counterKey, { n: len, max: CONTENT_MAX, seg: segments })}</span> : undefined}
          >
            <Textarea rows={a.type === 'NOTIFICATION_EMAIL' ? 6 : 3} maxLength={limited ? CONTENT_MAX : undefined} value={a.content ?? ''} onChange={(e) => onChange({ ...a, content: e.target.value })} placeholder={t('campaign.wizard.contentPh')} />
            {unknown.length > 0 && <p className="text-xs text-danger">{t('campaign.wizard.unknownParams', { list: unknown.join(', ') })}</p>}
            {withParams && a.content && (
              <p className="rounded-md bg-muted/60 px-2.5 py-1.5 text-xs text-muted-foreground">
                <span className="font-medium text-foreground">{t('campaign.wizard.preview')}:</span> {previewParams(a.content, params)}
              </p>
            )}
            <div className="flex flex-wrap gap-1.5">
              {params.map((p) => (
                <button
                  key={p.key}
                  type="button"
                  onClick={() => insert(p.token)}
                  title={t('campaign.wizard.paramExample', { v: p.example })}
                  className="cursor-pointer rounded-md border border-dashed px-2 py-0.5 text-[11px] text-muted-foreground transition hover:border-brand-300 hover:text-brand-600"
                >
                  + <span className="font-mono">{p.token}</span> {t(`campaign.param.${p.key}`)}
                </button>
              ))}
            </div>
            {sampleParam && <p className="text-xs text-muted-foreground">{t('campaign.wizard.paramHint', { example: `{{${sampleParam.key}|${t('campaign.wizard.paramDefaultSample')}}}` })}</p>}
            <div className="flex flex-col gap-2 sm:flex-row">
              <Input value={link} onChange={(e) => setLink(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), insertLink())} placeholder="https://…" className="h-8 font-mono text-xs" aria-label={t('campaign.wizard.trackLinkUrl')} />
              <Button type="button" variant="outline" size="sm" disabled={!linkOk} onClick={insertLink}>
                <Link2 />
                {t('campaign.wizard.insertTrackLink')}
              </Button>
            </div>
            <label className="flex cursor-pointer items-center gap-2 text-xs text-muted-foreground">
              <Switch checked={!!a.trackLinks} onCheckedChange={(v) => onChange({ ...a, trackLinks: v })} />
              {t('campaign.wizard.trackLinks')}
            </label>
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

export function ActionsStep({ groups, defaultSms, onChange }: { groups: ActionGroup[]; defaultSms: string | null; onChange: (g: ActionGroup[]) => void }) {
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
                    count={g.actions.length}
                    sequential={g.mode === 'SEQUENTIAL'}
                    defaultSms={defaultSms}
                    onChange={(na) => update(g.id, { actions: g.actions.map((x) => (x.id === a.id ? na : x)) })}
                    onRemove={() => update(g.id, { actions: g.actions.filter((x) => x.id !== a.id) })}
                    onMove={(dir) => {
                      const next = [...g.actions]
                      const j = i + dir
                      ;[next[i], next[j]] = [next[j], next[i]]
                      update(g.id, { actions: next })
                    }}
                  />
                </div>
              ))}
              <div className="pt-2">
                <AddActionMenu onAdd={(type) => update(g.id, { actions: [...g.actions, newAction(type, defaultSms)] })} />
              </div>
            </div>
          )}
          {gi === 0 && null}
        </section>
      ))}

      <div className="flex flex-wrap gap-2">
        <Button variant="soft" onClick={() => onChange([...groups, newGroup(`${t('campaign.wizard.group')} ${String.fromCharCode(65 + groups.filter((x) => !x.isControl).length)}`, 0, false, defaultSms)])}>
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
