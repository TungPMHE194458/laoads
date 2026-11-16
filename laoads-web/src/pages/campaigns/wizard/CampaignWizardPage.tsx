import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { ArrowLeft, ArrowRight, Check, CheckCircle2, FlaskConical, ListChecks, Plus, Rocket, Send, Users2, XCircle, Zap } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { CampaignDetail, CampaignInput, CampaignSchedule } from '@/api/types'
import { useAvailableSenders, useCampaign, useCampaignGroups, useCreateCampaignGroup, useNotificationSettings, useSaveCampaign, useSegments, useTestSend } from '@/features/campaign/api'
import { useActivePriceQuote } from '@/features/price/api'
import { PageHeader } from '@/components/common/PageHeader'
import { Field, FormDialog } from '@/components/common/dialogs'
import { SegmentStatusBadge, ChannelChips } from '@/components/common/status'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input, Textarea } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { RadioCard, RadioGroup, Skeleton, Switch } from '@/components/ui/misc'
import { Badge } from '@/components/ui/badge'
import { fmtKip, fmtNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import { ActionsStep, newGroup, validateActions } from './ActionsStep'
import { ACTION_CHANNEL, EVENT_TEMPLATES, withDefaultSender } from './actionMeta'

const STEPS = ['basics', 'audience', 'actions', 'schedule', 'goal', 'review'] as const
type Step = (typeof STEPS)[number]

const toLocal = (iso: string | null | undefined) => (iso ? new Date(iso).toLocaleString('sv-SE').slice(0, 16).replace(' ', 'T') : '')
const fromLocal = (v: string) => (v ? new Date(v).toISOString() : null)

function emptyDraft(mode: 'OFFICIAL' | 'TEST', t: (k: string) => string): CampaignInput {
  const start = new Date(Date.now() + 86400_000)
  start.setHours(9, 0, 0, 0)
  const end = new Date(start.getTime() + 14 * 86400_000)
  return {
    name: '',
    mode,
    type: 'STATIC',
    groupId: null,
    segmentId: null,
    targetCount: 0,
    channels: [],
    startAt: start.toISOString(),
    endAt: end.toISOString(),
    description: '',
    language: 'LO',
    testIsdns: [],
    actionGroups: [newGroup(`${t('campaign.wizard.group')} A`, 100)],
    schedule: { type: 'ONCE', startAt: start.toISOString(), endAt: end.toISOString(), sendTime: '09:00', every: 1, unit: 'WEEK', weekdays: [1], silentHours: { enabled: true, from: '21:00', to: '07:00' } },
    goal: { mode: 'AUTO_OCS', description: '', attributionWindowDays: 7, stopOnGoal: true, maxNoResponse: 3 },
    budget: { expected: 0, max: null },
  }
}

function fromDetail(c: CampaignDetail): CampaignInput {
  const { id: _i, code: _c, status: _s, createdAt: _ca, createdBy: _cb, sentRate: _sr, openRate: _or, conversionRate: _cr, uplift: _u, rejectReason: _rr, history: _h, groupName: _gn, segmentName: _sn, expectedCost: _ec, ...rest } = c
  void [_i, _c, _s, _ca, _cb, _sr, _or, _cr, _u, _rr, _h, _gn, _sn, _ec]
  return rest
}

function Stepper({ step, done, onGo }: { step: Step; done: Set<Step>; onGo: (s: Step) => void }) {
  const { t } = useTranslation()
  const cur = STEPS.indexOf(step)
  return (
    <ol className="flex gap-1 overflow-x-auto pb-1 lg:flex-col lg:gap-0.5 lg:overflow-visible">
      {STEPS.map((s, i) => {
        const active = s === step
        const isDone = done.has(s) && !active
        const reachable = i <= cur || done.has(STEPS[i - 1])
        return (
          <li key={s} className="shrink-0">
            <button
              disabled={!reachable}
              onClick={() => onGo(s)}
              className={cn(
                'flex w-full cursor-pointer items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition disabled:cursor-not-allowed disabled:opacity-50',
                active ? 'bg-card font-semibold shadow-card ring-1 ring-border' : 'hover:bg-muted/70',
              )}
            >
              <span
                className={cn(
                  'grid size-7 shrink-0 place-items-center rounded-full text-xs font-semibold tabular',
                  active ? 'bg-primary text-primary-foreground' : isDone ? 'bg-success-soft text-success' : 'bg-muted text-muted-foreground',
                )}
              >
                {isDone ? <Check className="size-3.5" strokeWidth={3} /> : i + 1}
              </span>
              <span className="whitespace-nowrap">{t(`campaign.wizard.steps.${s}`)}</span>
            </button>
          </li>
        )
      })}
    </ol>
  )
}

export default function CampaignWizardPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { id } = useParams()
  const [sp] = useSearchParams()
  const editId = id ? Number(id) : null
  const existing = useCampaign(editId)
  const [draft, setDraft] = React.useState<CampaignInput>(() => emptyDraft(sp.get('mode') === 'TEST' ? 'TEST' : 'OFFICIAL', t))
  const [step, setStep] = React.useState<Step>('basics')
  const [done, setDone] = React.useState<Set<Step>>(new Set())
  const [error, setError] = React.useState<string | null>(null)
  const loaded = React.useRef(false)

  React.useEffect(() => {
    if (existing.data && !loaded.current) {
      loaded.current = true
      setDraft(fromDetail(existing.data))
      setDone(new Set(STEPS))
    }
  }, [existing.data])

  const groups = useCampaignGroups()
  const createGroup = useCreateCampaignGroup()
  const [groupDialog, setGroupDialog] = React.useState(false)
  const [groupName, setGroupName] = React.useState('')
  const segments = useSegments({ page: 0, size: 100, status: 'READY' })
  const price = useActivePriceQuote()
  const notif = useNotificationSettings()
  const save = useSaveCampaign()
  const testSend = useTestSend()
  const [testList, setTestList] = React.useState('')
  // Hành động SMS chưa chọn đầu số dùng đầu số SMS đầu tiên workspace được dùng (điền khi kiểm tra / lưu / bắn thử)
  const smsSenders = useAvailableSenders('SMS')
  const defaultSms = smsSenders.data?.[0]?.code ?? null
  const actionGroups = withDefaultSender(draft.actionGroups, defaultSms)

  const set = <K extends keyof CampaignInput>(k: K, v: CampaignInput[K]) => setDraft((d) => ({ ...d, [k]: v }))
  const setSchedule = (p: Partial<CampaignSchedule>) => setDraft((d) => ({ ...d, schedule: { ...d.schedule, ...p } }))
  const segment = segments.data?.items.find((s) => s.id === draft.segmentId)

  // Ước tính chi phí theo bảng giá đang khoá
  const unit = (type: string) => price.data?.items.find((i) => i.actionType === type)?.unitPrice ?? 0
  const target = draft.mode === 'TEST' ? Math.min(segment?.totalUsers ?? 0, 1000) : (segment?.totalUsers ?? 0)
  const expected = Math.round(draft.actionGroups.reduce((s, g) => s + (g.isControl ? 0 : ((target * g.allocationPercent) / 100) * g.actions.reduce((x, a) => x + unit(a.type), 0)), 0))

  const validate = (s: Step): string | null => {
    if (s === 'basics') return draft.name.trim().length < 3 ? 'campaign.wizard.err.name' : null
    if (s === 'audience') return !draft.segmentId ? 'campaign.wizard.err.segment' : null
    if (s === 'actions') return validateActions(actionGroups)
    if (s === 'schedule') {
      if (draft.schedule.type !== 'NOW' && !draft.schedule.startAt) return 'campaign.wizard.err.start'
      if (draft.schedule.endAt && draft.schedule.startAt && draft.schedule.endAt <= draft.schedule.startAt) return 'campaign.wizard.err.endBeforeStart'
    }
    if (s === 'review' && draft.budget.max != null && draft.budget.max > 0 && draft.budget.max < expected) return 'campaign.wizard.err.budget'
    return null
  }

  const go = (to: Step) => {
    setError(null)
    setStep(to)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  const next = () => {
    const err = validate(step)
    if (err) return setError(t(err))
    setDone((d) => new Set(d).add(step))
    go(STEPS[STEPS.indexOf(step) + 1])
  }

  const submit = (submitForApproval: boolean) => {
    for (const s of STEPS) {
      const err = validate(s)
      if (err) {
        setStep(s)
        return setError(t(err))
      }
    }
    const channels = [...new Set(draft.actionGroups.flatMap((g) => g.actions.map((a) => ACTION_CHANNEL[a.type]).filter(Boolean)))] as CampaignInput['channels']
    const payload: CampaignInput = {
      ...draft,
      actionGroups,
      channels,
      targetCount: target,
      startAt: draft.schedule.type === 'NOW' ? new Date().toISOString() : draft.schedule.startAt,
      endAt: draft.schedule.endAt,
      budget: { ...draft.budget, expected },
    }
    save.mutate(
      { ...payload, id: editId ?? undefined, submit: submitForApproval },
      {
        onSuccess: (c) => {
          toast.success(submitForApproval ? t('campaign.wizard.submitted') : t('campaign.wizard.saved'))
          navigate(`/campaigns/${c.id}`, { replace: true })
        },
        onError: (e) => toast.error(errorMessage(e)),
      },
    )
  }

  if (editId && existing.isLoading) return <Skeleton className="h-96 w-full rounded-xl" />

  return (
    <>
      <PageHeader
        crumbs={[{ label: t('nav.campaigns'), to: '/campaigns' }, { label: editId ? t('campaign.wizard.editTitle') : t('campaign.wizard.newTitle') }]}
        icon={draft.mode === 'TEST' ? <FlaskConical /> : <Rocket />}
        title={editId ? draft.name || t('campaign.wizard.editTitle') : t('campaign.wizard.newTitle')}
        description={
          <span className="flex items-center gap-2">
            <Badge variant={draft.mode === 'TEST' ? 'info' : 'brand'}>{t(`campaign.mode.${draft.mode}`)}</Badge>
            {t('campaign.wizard.subtitle')}
          </span>
        }
        actions={
          <Button variant="ghost" asChild>
            <Link to={editId ? `/campaigns/${editId}` : '/campaigns'}>{t('common.cancel')}</Link>
          </Button>
        }
      />
      <div className="grid gap-6 lg:grid-cols-[240px_minmax(0,1fr)]">
        <aside className="lg:sticky lg:top-24 lg:self-start">
          <Stepper step={step} done={done} onGo={go} />
        </aside>

        <div className="min-w-0">
          <Card>
            <CardContent className="p-6 md:p-8">
              <div className="mb-6">
                <h2 className="text-lg font-semibold">{t(`campaign.wizard.steps.${step}`)}</h2>
                <p className="mt-1 text-sm text-muted-foreground">{t(`campaign.wizard.stepDesc.${step}`)}</p>
              </div>

              {step === 'basics' && (
                <div className="grid gap-6">
                  <RadioGroup value={draft.mode} onValueChange={(v) => set('mode', v as CampaignInput['mode'])} className="grid gap-3 sm:grid-cols-2">
                    <RadioCard value="OFFICIAL" icon={<Rocket className="size-5" />} title={t('campaign.mode.OFFICIAL')} description={t('campaign.mode.OFFICIAL_desc')} />
                    <RadioCard value="TEST" icon={<FlaskConical className="size-5" />} title={t('campaign.mode.TEST')} description={t('campaign.mode.TEST_desc')} />
                  </RadioGroup>
                  <RadioGroup value={draft.type} onValueChange={(v) => set('type', v as CampaignInput['type'])} className="grid gap-3 sm:grid-cols-2">
                    <RadioCard value="STATIC" icon={<ListChecks className="size-5" />} title={t('campaign.type.STATIC')} description={t('campaign.type.STATIC_desc')} />
                    <RadioCard value="EVENT" icon={<Zap className="size-5" />} title={t('campaign.type.EVENT')} description={t('campaign.type.EVENT_desc')} />
                  </RadioGroup>
                  <div className="grid gap-4 md:grid-cols-2">
                    <Field label={t('campaign.wizard.name')} required className="md:col-span-2">
                      <Input value={draft.name} onChange={(e) => set('name', e.target.value)} placeholder={t('campaign.wizard.namePh')} maxLength={150} />
                    </Field>
                    {draft.type === 'EVENT' && (
                      <Field label={t('campaign.wizard.eventTemplate')} required>
                        <Select value={draft.description?.startsWith('EVT:') ? draft.description.slice(4) : ''} onValueChange={(v) => set('description', `EVT:${v}`)} options={EVENT_TEMPLATES.map((e) => ({ value: e, label: t(`campaign.event.${e}`) }))} placeholder={t('common.choose')} />
                      </Field>
                    )}
                    <Field label={t('campaign.wizard.group')}>
                      <div className="flex gap-2">
                        <Select className="flex-1" value={draft.groupId ? String(draft.groupId) : ''} onValueChange={(v) => set('groupId', v ? Number(v) : null)} allLabel={t('campaign.wizard.noGroup')} options={(groups.data ?? []).map((g) => ({ value: String(g.id), label: g.name }))} />
                        <Button type="button" variant="outline" size="icon" aria-label={t('campaign.wizard.newGroup')} onClick={() => setGroupDialog(true)}>
                          <Plus />
                        </Button>
                      </div>
                    </Field>
                    <Field label={t('campaign.wizard.language')}>
                      <Select value={draft.language} onValueChange={(v) => set('language', v as CampaignInput['language'])} options={(['LO', 'EN', 'VI'] as const).map((l) => ({ value: l, label: t(`langName.${l}`) }))} />
                    </Field>
                    {draft.type !== 'EVENT' && (
                      <Field label={t('campaign.wizard.description')} className="md:col-span-2">
                        <Textarea value={draft.description ?? ''} onChange={(e) => set('description', e.target.value)} rows={3} />
                      </Field>
                    )}
                  </div>
                </div>
              )}

              {step === 'audience' && (
                <div className="grid gap-5">
                  {segments.isLoading ? (
                    <Skeleton className="h-60" />
                  ) : (
                    <RadioGroup value={draft.segmentId ? String(draft.segmentId) : ''} onValueChange={(v) => set('segmentId', Number(v))} className="grid gap-3 md:grid-cols-2">
                      {segments.data?.items.map((s) => (
                        <RadioCard
                          key={s.id}
                          value={String(s.id)}
                          icon={<Users2 className="size-5" />}
                          title={s.name}
                          description={
                            <span className="flex flex-wrap items-center gap-2">
                              <span className="font-semibold text-foreground tabular">{fmtNumber(s.totalUsers)}</span> {t('campaign.wizard.subs')}
                              <Badge variant="outline">{t(`segmentType.${s.type}`)}</Badge>
                              <SegmentStatusBadge status={s.status} />
                            </span>
                          }
                        />
                      ))}
                    </RadioGroup>
                  )}
                  <p className="text-sm text-muted-foreground">
                    {t('campaign.wizard.noSegment')}{' '}
                    <Link to="/segments?create=1" className="font-medium text-link hover:underline">
                      {t('segment.create')}
                    </Link>
                  </p>
                  {draft.mode === 'TEST' && <div className="rounded-xl border border-info/25 bg-info-soft px-4 py-3 text-sm text-info">{t('campaign.wizard.testLimit')}</div>}
                </div>
              )}

              {step === 'actions' && <ActionsStep groups={draft.actionGroups} defaultSms={defaultSms} onChange={(g) => set('actionGroups', g)} />}

              {step === 'schedule' && (
                <div className="grid gap-6">
                  <RadioGroup value={draft.schedule.type} onValueChange={(v) => setSchedule({ type: v as CampaignSchedule['type'] })} className="grid gap-3 md:grid-cols-3">
                    {(['NOW', 'ONCE', 'RECURRING'] as const).map((k) => (
                      <RadioCard key={k} value={k} title={t(`campaign.wizard.schedule.${k}`)} description={t(`campaign.wizard.schedule.${k}_desc`)} />
                    ))}
                  </RadioGroup>
                  {draft.schedule.type !== 'NOW' && (
                    <div className="grid gap-4 md:grid-cols-2">
                      <Field label={t('campaign.wizard.startAt')} required>
                        <Input type="datetime-local" value={toLocal(draft.schedule.startAt)} onChange={(e) => setSchedule({ startAt: fromLocal(e.target.value) })} />
                      </Field>
                      <Field label={t('campaign.wizard.endAt')}>
                        <Input type="datetime-local" value={toLocal(draft.schedule.endAt)} onChange={(e) => setSchedule({ endAt: fromLocal(e.target.value) })} />
                      </Field>
                    </div>
                  )}
                  {draft.schedule.type === 'RECURRING' && (
                    <div className="grid gap-4 rounded-xl border p-4 md:grid-cols-3">
                      <Field label={t('campaign.wizard.every')}>
                        <div className="flex gap-2">
                          <Input type="number" min={1} step="any" value={draft.schedule.every ?? 1} onChange={(e) => setSchedule({ every: Math.max(1, Number(e.target.value) || 1) })} className="w-20" />
                          <Select value={draft.schedule.unit ?? 'WEEK'} onValueChange={(v) => setSchedule({ unit: v as CampaignSchedule['unit'] })} options={(['DAY', 'WEEK', 'MONTH'] as const).map((u) => ({ value: u, label: t(`campaign.wizard.unit.${u}`) }))} />
                        </div>
                      </Field>
                      <Field label={t('campaign.wizard.sendTime')}>
                        <Input type="time" value={draft.schedule.sendTime ?? '09:00'} onChange={(e) => setSchedule({ sendTime: e.target.value })} />
                      </Field>
                      {draft.schedule.unit === 'WEEK' && (
                        <Field label={t('campaign.wizard.weekdays')} className="md:col-span-3">
                          <div className="flex flex-wrap gap-1.5">
                            {[1, 2, 3, 4, 5, 6, 0].map((d) => {
                              const on = draft.schedule.weekdays?.includes(d)
                              return (
                                <button
                                  key={d}
                                  type="button"
                                  aria-pressed={on}
                                  onClick={() => setSchedule({ weekdays: on ? draft.schedule.weekdays?.filter((x) => x !== d) : [...(draft.schedule.weekdays ?? []), d] })}
                                  className={cn('h-9 min-w-11 cursor-pointer rounded-lg border px-3 text-sm transition', on ? 'border-brand-500 bg-accent font-semibold text-accent-foreground' : 'hover:bg-muted')}
                                >
                                  {t(`weekday.${d}`)}
                                </button>
                              )
                            })}
                          </div>
                        </Field>
                      )}
                    </div>
                  )}
                  <div className="flex flex-wrap items-center gap-4 rounded-xl border p-4">
                    <Switch checked={draft.schedule.silentHours?.enabled ?? false} onCheckedChange={(on) => setSchedule({ silentHours: { from: draft.schedule.silentHours?.from ?? '21:00', to: draft.schedule.silentHours?.to ?? '07:00', enabled: on } })} />
                    <div className="min-w-0 flex-1">
                      <div className="text-sm font-medium">{t('campaign.wizard.silent')}</div>
                      <div className="text-xs text-muted-foreground">{t('campaign.wizard.silentDesc', { from: notif.data?.silentFrom ?? '21:00', to: notif.data?.silentTo ?? '07:00' })}</div>
                    </div>
                    {draft.schedule.silentHours?.enabled && (
                      <div className="flex items-center gap-2">
                        <Input type="time" className="w-28" value={draft.schedule.silentHours.from} onChange={(e) => setSchedule({ silentHours: { ...draft.schedule.silentHours!, from: e.target.value } })} />
                        <span className="text-muted-foreground">→</span>
                        <Input type="time" className="w-28" value={draft.schedule.silentHours.to} onChange={(e) => setSchedule({ silentHours: { ...draft.schedule.silentHours!, to: e.target.value } })} />
                      </div>
                    )}
                  </div>
                </div>
              )}

              {step === 'goal' && (
                <div className="grid gap-6">
                  <RadioGroup value={draft.goal.mode} onValueChange={(v) => set('goal', { ...draft.goal, mode: v as CampaignInput['goal']['mode'] })} className="grid gap-3 md:grid-cols-3">
                    {(['AUTO_OCS', 'MANUAL_FILE', 'NONE'] as const).map((k) => (
                      <RadioCard key={k} value={k} title={t(`campaign.wizard.goal.${k}`)} description={t(`campaign.wizard.goal.${k}_desc`)} />
                    ))}
                  </RadioGroup>
                  {draft.goal.mode !== 'NONE' && (
                    <div className="grid gap-4 md:grid-cols-2">
                      <Field label={t('campaign.wizard.goalDesc')} className="md:col-span-2">
                        <Textarea rows={2} value={draft.goal.description} onChange={(e) => set('goal', { ...draft.goal, description: e.target.value })} placeholder={t('campaign.wizard.goalDescPh')} />
                      </Field>
                      <Field label={t('campaign.wizard.attribution')} hint={t('campaign.wizard.attributionHint')}>
                        <Input type="number" min={1} step="any" value={draft.goal.attributionWindowDays} onChange={(e) => set('goal', { ...draft.goal, attributionWindowDays: Math.max(1, Number(e.target.value) || 1) })} />
                      </Field>
                      <Field label={t('campaign.wizard.maxNoResponse')} hint={t('campaign.wizard.maxNoResponseHint')}>
                        <Input type="number" min={0} step="any" value={draft.goal.maxNoResponse ?? ''} onChange={(e) => set('goal', { ...draft.goal, maxNoResponse: e.target.value ? Number(e.target.value) : null })} />
                      </Field>
                      <label className="flex items-center gap-3 text-sm md:col-span-2">
                        <Switch checked={draft.goal.stopOnGoal} onCheckedChange={(v) => set('goal', { ...draft.goal, stopOnGoal: v })} />
                        {t('campaign.wizard.stopOnGoal')}
                      </label>
                    </div>
                  )}
                </div>
              )}

              {step === 'review' && (
                <div className="grid gap-6">
                  <div className="grid gap-4 md:grid-cols-3">
                    <div className="rounded-xl border bg-muted/40 p-4">
                      <div className="text-xs text-muted-foreground">{t('metric.target')}</div>
                      <div className="mt-1 text-xl font-semibold tabular">{fmtNumber(target)}</div>
                      <div className="mt-0.5 truncate text-xs text-muted-foreground">{segment?.name}</div>
                    </div>
                    <div className="rounded-xl border bg-muted/40 p-4">
                      <div className="text-xs text-muted-foreground">{t('campaign.wizard.expectedCost')}</div>
                      <div className="mt-1 text-xl font-semibold tabular">{fmtKip(expected)}</div>
                      <div className="mt-0.5 truncate text-xs text-muted-foreground">{price.data ? price.data.name : t('campaign.wizard.noPrice')}</div>
                    </div>
                    <Field label={t('campaign.wizard.maxBudget')} hint={t('campaign.wizard.maxBudgetHint')}>
                      <Input type="number" min={0} step="any" value={draft.budget.max ?? ''} onChange={(e) => set('budget', { ...draft.budget, max: e.target.value ? Number(e.target.value) : null })} placeholder={String(expected)} />
                    </Field>
                  </div>
                  <dl className="grid gap-x-8 gap-y-3 rounded-xl border p-5 text-sm md:grid-cols-2">
                    {[
                      [t('campaign.wizard.name'), draft.name],
                      [t('campaign.wizard.steps.basics'), `${t(`campaign.mode.${draft.mode}`)} · ${t(`campaign.type.${draft.type}`)}`],
                      [t('campaign.wizard.steps.schedule'), `${t(`campaign.wizard.schedule.${draft.schedule.type}`)}${draft.schedule.silentHours?.enabled ? ` · ${t('campaign.wizard.silent')} ${draft.schedule.silentHours.from}–${draft.schedule.silentHours.to}` : ''}`],
                      [t('campaign.wizard.steps.goal'), t(`campaign.wizard.goal.${draft.goal.mode}`)],
                    ].map(([k, v]) => (
                      <div key={k}>
                        <dt className="text-xs text-muted-foreground">{k}</dt>
                        <dd className="mt-0.5 font-medium">{v}</dd>
                      </div>
                    ))}
                    <div className="md:col-span-2">
                      <dt className="text-xs text-muted-foreground">{t('campaign.wizard.steps.actions')}</dt>
                      <dd className="mt-1.5 flex flex-wrap gap-2">
                        {draft.actionGroups.map((g) => (
                          <span key={g.id} className="rounded-lg border px-2.5 py-1 text-xs">
                            <b>{g.name}</b> {g.allocationPercent}% · {g.isControl ? t('campaign.wizard.control') : g.actions.map((a) => t(`actionType.${a.type}`)).join(' → ')}
                          </span>
                        ))}
                      </dd>
                      <ChannelChips className="mt-2" channels={[...new Set(draft.actionGroups.flatMap((g) => g.actions.map((a) => ACTION_CHANNEL[a.type]).filter(Boolean)))] as CampaignInput['channels']} />
                    </div>
                  </dl>

                  {/* Bắn tin thử (YC bổ sung 6) */}
                  <div className="rounded-xl border p-5">
                    <div className="flex items-center gap-2 font-medium">
                      <Send className="size-4 text-brand-600" />
                      {t('campaign.wizard.testSend')}
                    </div>
                    <p className="mt-1 text-sm text-muted-foreground">{t('campaign.wizard.testSendDesc')}</p>
                    <div className="mt-3 flex flex-col gap-2 sm:flex-row">
                      <Input value={testList} onChange={(e) => setTestList(e.target.value)} placeholder="8562055510001, 8562055510002" className="font-mono" />
                      <Button
                        variant="outline"
                        loading={testSend.isPending}
                        disabled={!testList.trim()}
                        onClick={() => testSend.mutate({ id: editId ?? undefined, isdns: testList.split(/[\s,;]+/).filter(Boolean).slice(0, 10), actions: actionGroups.find((g) => !g.isControl)?.actions })}
                      >
                        {t('campaign.wizard.sendTest')}
                      </Button>
                    </div>
                    {testSend.data && (
                      <ul className="mt-3 grid gap-1.5 text-[13px]">
                        {testSend.data.map((r, i) => (
                          <li key={i} className="flex items-center gap-2">
                            {r.status === 'SENT' ? <CheckCircle2 className="size-4 text-success" /> : <XCircle className="size-4 text-danger" />}
                            <span className="font-mono">{r.isdn}</span>
                            <Badge variant="outline">{r.channel}</Badge>
                            <span className="text-muted-foreground">{r.detail}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                </div>
              )}

              {error && (
                <p role="alert" className="mt-6 rounded-lg border border-danger/20 bg-danger-soft px-3 py-2.5 text-sm text-danger">
                  {error}
                </p>
              )}
            </CardContent>
            <div className="flex items-center gap-2 border-t px-6 py-4 md:px-8">
              <Button variant="ghost" disabled={step === 'basics'} onClick={() => go(STEPS[STEPS.indexOf(step) - 1])}>
                <ArrowLeft />
                {t('common.back')}
              </Button>
              <div className="ml-auto flex gap-2">
                <Button variant="outline" onClick={() => submit(false)} loading={save.isPending && !save.variables?.submit}>
                  {t('campaign.wizard.saveDraft')}
                </Button>
                {step === 'review' ? (
                  <Button onClick={() => submit(true)} loading={save.isPending && !!save.variables?.submit}>
                    <Send />
                    {t('campaign.wizard.submit')}
                  </Button>
                ) : (
                  <Button onClick={next}>
                    {t('common.next')}
                    <ArrowRight />
                  </Button>
                )}
              </div>
            </div>
          </Card>
        </div>
      </div>
      <FormDialog
        open={groupDialog}
        onOpenChange={(o) => {
          setGroupDialog(o)
          if (!o) setGroupName('')
        }}
        title={t('campaign.wizard.newGroup')}
        submitLabel={t('campaign.wizard.createGroup')}
        loading={createGroup.isPending}
        onSubmit={() => {
          if (!groupName.trim()) return
          createGroup.mutate(groupName.trim(), {
            onSuccess: (g) => {
              set('groupId', g.id)
              setGroupDialog(false)
              setGroupName('')
              toast.success(t('campaign.wizard.groupCreated'))
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }}
      >
        <Field label={t('campaign.wizard.groupName')} required>
          <Input value={groupName} onChange={(e) => setGroupName(e.target.value)} maxLength={255} />
        </Field>
      </FormDialog>
    </>
  )
}
