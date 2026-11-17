import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { ArrowLeft, ArrowRight, Check, CheckCircle2, Download, FlaskConical, Info, ListChecks, Plus, Rocket, Send, Users2, XCircle, Zap } from 'lucide-react'
import { ApiError, errorMessage } from '@/api/client'
import type { CampaignDetail, CampaignInput, CampaignSchedule } from '@/api/types'
import { useAvailableSenders, useCampaign, useCampaignGroups, useCreateCampaignGroup, downloadConversionTemplate, useEventTemplates, useMessageParams, useNotificationSettings, useSaveCampaign, useSegment, useSegments, useTestSend } from '@/features/campaign/api'
import { useActivePriceQuote } from '@/features/price/api'
import { PageHeader } from '@/components/common/PageHeader'
import { SearchInput } from '@/components/common/SearchInput'
import { Pagination } from '@/components/common/DataTable'
import { useCan } from '@/features/auth/permissions'
import { CreateSegmentDialog } from '@/pages/segments/CreateSegmentDialog'
import { ConditionEditor } from '@/pages/segments/ConditionEditor'
import { conditionComplete } from '@/pages/segments/conditionOps'
import { Field, FormDialog } from '@/components/common/dialogs'
import { SegmentStatusBadge, ChannelChips } from '@/components/common/status'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input, Textarea } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Checkbox, RadioCard, RadioGroup, Skeleton, Switch } from '@/components/ui/misc'
import { Badge } from '@/components/ui/badge'
import { fmtKip, fmtNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import { ActionsStep, newGroup, validateActions } from './ActionsStep'
import { ACTION_CHANNEL, DESCRIPTION_MAX, withDefaultSender } from './actionMeta'

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
    goal: { mode: 'AUTO_OCS', description: '', attributionWindowDays: 7, stopOnGoal: true, maxNoResponse: null, reentryDays: null, conditions: [{ field: 'g_volume', operator: 'GT', value: '', join: 'AND' }] },
    budget: { expected: 0, max: null },
    eventTemplate: null,
    testLimit: mode === 'TEST' ? TEST_LIMIT_DEFAULT : null,
  }
}

/** SRS 3.2.1.4: giới hạn số khách hàng chạy thử, mặc định 10 (BE CampaignDefinition.DEFAULT_TEST_LIMIT / MAX_TEST_LIMIT). */
const TEST_LIMIT_DEFAULT = 10
const TEST_LIMIT_MAX = 1000
const parseIsdns = (v: string) => v.split(/[\s,;]+/).filter(Boolean)
/** Gọi trong event handler (kiểm tra bước), không phải lúc render. Dung sai 1 phút như BE. */
const isPast = (iso: string) => new Date(iso).getTime() < Date.now() - 60_000

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
  // SRS 3.2.2.1: chọn tệp có sẵn — tìm kiếm + phân trang; tệp đang chọn lấy riêng để luôn có số khách hàng
  const [segQuery, setSegQuery] = React.useState('')
  const [segPage, setSegPage] = React.useState(0)
  const segments = useSegments({ page: segPage, size: 8, query: segQuery, status: 'READY' })
  const selectedSegment = useSegment(draft.segmentId)
  const [segDialog, setSegDialog] = React.useState(false)
  const canManageSegments = useCan()('SEGMENT_MANAGE')
  const eventTemplates = useEventTemplates()
  // Danh sách số thử (chiến dịch TEST): giữ nguyên chuỗi người dùng đang gõ, null = lấy từ draft
  const [isdnText, setIsdnText] = React.useState<string | null>(null)
  const price = useActivePriceQuote()
  const notif = useNotificationSettings()
  const save = useSaveCampaign()
  const testSend = useTestSend()
  const messageParams = useMessageParams()
  const [testList, setTestList] = React.useState('')
  // Hành động SMS chưa chọn đầu số dùng đầu số SMS đầu tiên workspace được dùng (điền khi kiểm tra / lưu / bắn thử)
  const smsSenders = useAvailableSenders('SMS')
  const defaultSms = smsSenders.data?.[0]?.code ?? null
  const actionGroups = withDefaultSender(draft.actionGroups, defaultSms)

  const set = <K extends keyof CampaignInput>(k: K, v: CampaignInput[K]) => setDraft((d) => ({ ...d, [k]: v }))
  const setSchedule = (p: Partial<CampaignSchedule>) => setDraft((d) => ({ ...d, schedule: { ...d.schedule, ...p } }))
  const segment = selectedSegment.data ?? segments.data?.items.find((s) => s.id === draft.segmentId)

  // Ước tính chi phí theo bảng giá đang khoá
  const unit = (type: string) => price.data?.items.find((i) => i.actionType === type)?.unitPrice ?? 0
  const testLimit = draft.testLimit ?? TEST_LIMIT_DEFAULT
  const target = draft.type === 'EVENT' ? 0 : draft.mode === 'TEST' ? Math.min(draft.testIsdns.length || (segment?.totalUsers ?? 0), testLimit) : (segment?.totalUsers ?? 0)
  const expected = Math.round(draft.actionGroups.reduce((s, g) => s + (g.isControl ? 0 : ((target * g.allocationPercent) / 100) * g.actions.reduce((x, a) => x + unit(a.type), 0)), 0))

  const validate = (s: Step): string | null => {
    if (s === 'basics') {
      if (!draft.name.trim()) return 'campaign.wizard.err.name'
      if (draft.type === 'EVENT' && !draft.eventTemplate) return 'campaign.wizard.err.eventTemplate'
      if ((draft.description ?? '').length > DESCRIPTION_MAX) return 'campaign.wizard.err.description'
      if (draft.mode === 'TEST' && !(testLimit >= 1 && testLimit <= TEST_LIMIT_MAX)) return 'campaign.wizard.err.testLimit'
      return null
    }
    // Chiến dịch sự kiện ghi danh thuê bao khi nhận sự kiện; chiến dịch thử có thể dùng danh sách số thử thay cho tệp
    if (s === 'audience') return draft.type === 'EVENT' || (draft.mode === 'TEST' && draft.testIsdns.length) || draft.segmentId ? null : 'campaign.wizard.err.segment'
    if (s === 'actions') return validateActions(actionGroups, messageParams.data)
    if (s === 'schedule' && draft.type !== 'EVENT') {
      const sc = draft.schedule
      if (sc.type !== 'NOW' && !sc.startAt) return 'campaign.wizard.err.start'
      if (sc.type !== 'NOW' && sc.startAt && isPast(sc.startAt)) return 'campaign.wizard.err.startInPast'
      if (sc.type === 'RECURRING' && !sc.endAt) return 'campaign.wizard.err.endRequired'
      if (sc.endAt && sc.startAt && sc.endAt <= sc.startAt) return 'campaign.wizard.err.endBeforeStart'
    }
    // SRS 3.2.6.1: mục tiêu từ nguồn dữ liệu ngoài cần ít nhất một điều kiện đầy đủ
    if (s === 'goal' && draft.goal.mode === 'AUTO_OCS' && !(draft.goal.conditions?.length && draft.goal.conditions.every(conditionComplete))) return 'campaign.wizard.err.goalConditions'
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
      // Lưu nháp: BE chưa bắt chọn tệp khách hàng / điều kiện mục tiêu / ngân sách (chỉ kiểm tra khi gửi duyệt)
      if (!submitForApproval && (s === 'audience' || s === 'goal' || s === 'review')) continue
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
        onError: (e) => {
          // SRS 3.2.1.4–6: tên trùng → quay về bước thông tin chung và báo lỗi tại đó
          if (e instanceof ApiError && e.statusCode === 'CAMPAIGN_NAME_DUPLICATE') {
            setStep('basics')
            setError(errorMessage(e))
          } else toast.error(errorMessage(e))
        },
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
                  <RadioGroup
                    value={draft.mode}
                    onValueChange={(v) =>
                      // SRS 3.2.1.4: chiến dịch thử nghiệm chỉ chạy theo danh sách cố định
                      setDraft((d) => (v === 'TEST' ? { ...d, mode: 'TEST', type: 'STATIC', testLimit: d.testLimit ?? TEST_LIMIT_DEFAULT } : { ...d, mode: 'OFFICIAL' }))
                    }
                    className="grid gap-3 sm:grid-cols-2"
                  >
                    <RadioCard value="OFFICIAL" icon={<Rocket className="size-5" />} title={t('campaign.mode.OFFICIAL')} description={t('campaign.mode.OFFICIAL_desc')} />
                    <RadioCard value="TEST" icon={<FlaskConical className="size-5" />} title={t('campaign.mode.TEST')} description={t('campaign.mode.TEST_desc')} />
                  </RadioGroup>
                  {draft.mode === 'OFFICIAL' && (
                    <RadioGroup value={draft.type} onValueChange={(v) => set('type', v as CampaignInput['type'])} className="grid gap-3 sm:grid-cols-2">
                      <RadioCard value="STATIC" icon={<ListChecks className="size-5" />} title={t('campaign.type.STATIC')} description={t('campaign.type.STATIC_desc')} />
                      <RadioCard value="EVENT" icon={<Zap className="size-5" />} title={t('campaign.type.EVENT')} description={t('campaign.type.EVENT_desc')} />
                    </RadioGroup>
                  )}
                  <div className="grid gap-4 md:grid-cols-2">
                    <Field label={t('campaign.wizard.name')} required className="md:col-span-2">
                      <Input value={draft.name} onChange={(e) => set('name', e.target.value)} placeholder={t('campaign.wizard.namePh')} maxLength={255} />
                    </Field>
                    {draft.type === 'EVENT' && (
                      <Field label={t('campaign.wizard.eventTemplate')} required hint={t('campaign.wizard.eventTemplateHint')}>
                        <Select
                          value={draft.eventTemplate ?? ''}
                          onValueChange={(v) => set('eventTemplate', v || null)}
                          options={(eventTemplates.data ?? []).map((e) => ({ value: e.code, label: t(`campaign.event.${e.code}`, { defaultValue: e.code }) }))}
                          placeholder={t('common.choose')}
                        />
                      </Field>
                    )}
                    {draft.mode === 'TEST' && (
                      <Field label={t('campaign.wizard.testLimitLabel')} required hint={t('campaign.wizard.testLimitHint', { max: fmtNumber(TEST_LIMIT_MAX) })}>
                        <Input type="number" min={1} max={TEST_LIMIT_MAX} step={1} value={draft.testLimit ?? ''} onChange={(e) => set('testLimit', e.target.value ? Math.floor(Number(e.target.value)) : null)} className="max-w-40" />
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
                    <Field
                      label={t('campaign.wizard.description')}
                      className="md:col-span-2"
                      hint={<span className="tabular">{t('campaign.wizard.charCounter', { n: (draft.description ?? '').length, max: DESCRIPTION_MAX })}</span>}
                    >
                      <Textarea value={draft.description ?? ''} onChange={(e) => set('description', e.target.value)} rows={3} maxLength={DESCRIPTION_MAX} />
                    </Field>
                  </div>
                </div>
              )}

              {step === 'audience' && draft.type === 'EVENT' && (
                <div className="rounded-xl border border-info/25 bg-info-soft px-4 py-3 text-sm text-info">
                  {t('campaign.wizard.eventAudience', { event: draft.eventTemplate ? t(`campaign.event.${draft.eventTemplate}`, { defaultValue: draft.eventTemplate }) : '—' })}
                </div>
              )}

              {step === 'audience' && draft.type !== 'EVENT' && (
                <div className="grid gap-5">
                  {draft.mode === 'TEST' && (
                    <Field label={t('campaign.wizard.testIsdns')} hint={t('campaign.wizard.testIsdnsHint', { n: testLimit })}>
                      <Textarea
                        rows={3}
                        className="font-mono"
                        value={isdnText ?? draft.testIsdns.join('\n')}
                        onChange={(e) => {
                          setIsdnText(e.target.value)
                          set('testIsdns', parseIsdns(e.target.value))
                        }}
                        placeholder={'8562055510001\n8562055510002'}
                      />
                    </Field>
                  )}
                  <div className="flex flex-wrap items-center gap-2">
                    <SearchInput
                      value={segQuery}
                      onChange={(v) => {
                        setSegQuery(v)
                        setSegPage(0)
                      }}
                      placeholder={t('segment.searchPh')}
                    />
                    {canManageSegments && (
                      <Button type="button" variant="outline" className="ml-auto" onClick={() => setSegDialog(true)}>
                        <Plus />
                        {t('segment.create')}
                      </Button>
                    )}
                  </div>
                  {segment && !segments.data?.items.some((s) => s.id === segment.id) && (
                    // tệp đang chọn không nằm ở trang hiện tại (hoặc vừa tạo, đang nạp) — vẫn hiển thị để người dùng thấy
                    <div className="flex flex-wrap items-center gap-2 rounded-xl border border-brand-300 bg-accent/60 px-4 py-3 text-sm">
                      <Check className="size-4 text-brand-600" />
                      <span className="font-medium">{segment.name}</span>
                      <span className="tabular text-muted-foreground">
                        {fmtNumber(segment.totalUsers)} {t('campaign.wizard.subs')}
                      </span>
                      <SegmentStatusBadge status={segment.status} />
                      {segment.status === 'BUILDING' && <span className="text-xs text-muted-foreground">{t('campaign.wizard.segmentBuilding')}</span>}
                    </div>
                  )}
                  {segments.isLoading ? (
                    <Skeleton className="h-60" />
                  ) : !segments.data?.items.length ? (
                    <p className="rounded-xl border border-dashed px-4 py-8 text-center text-sm text-muted-foreground">{segQuery ? t('common.noResult') : t('campaign.wizard.noReadySegment')}</p>
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
                  {segments.data && segments.data.meta.totalPages > 1 && (
                    <div className="overflow-hidden rounded-xl border">
                      <Pagination meta={segments.data.meta} onPageChange={setSegPage} />
                    </div>
                  )}
                  {draft.mode === 'TEST' && <div className="rounded-xl border border-info/25 bg-info-soft px-4 py-3 text-sm text-info">{t('campaign.wizard.testLimit', { n: testLimit })}</div>}
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
                      <Field label={t('campaign.wizard.endAt')} required={draft.schedule.type === 'RECURRING'}>
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
                      {draft.schedule.unit === 'MONTH' && (
                        <Field label={t('campaign.wizard.monthDays')} hint={t('campaign.wizard.monthDaysHint')} className="md:col-span-3">
                          <div className="flex flex-wrap gap-1.5">
                            {Array.from({ length: 31 }, (_, i) => i + 1).map((d) => {
                              const on = draft.schedule.monthDays?.includes(d)
                              return (
                                <button
                                  key={d}
                                  type="button"
                                  aria-pressed={on}
                                  onClick={() => setSchedule({ monthDays: on ? draft.schedule.monthDays?.filter((x) => x !== d) : [...(draft.schedule.monthDays ?? []), d].sort((a, b) => a - b) })}
                                  className={cn('h-9 w-10 cursor-pointer rounded-lg border text-sm tabular transition', on ? 'border-brand-500 bg-accent font-semibold text-accent-foreground' : 'hover:bg-muted')}
                                >
                                  {d}
                                </button>
                              )
                            })}
                          </div>
                        </Field>
                      )}
                      <Field label={t('campaign.wizard.maxOccurrences')} hint={t('campaign.wizard.maxOccurrencesHint')} className="md:col-span-3">
                        <div className="flex items-center gap-3 text-sm">
                          <Checkbox
                            id="sched-max"
                            checked={draft.schedule.maxOccurrences != null}
                            onCheckedChange={(v) => setSchedule({ maxOccurrences: v === true ? 10 : null })}
                          />
                          <label htmlFor="sched-max">{t('campaign.wizard.finishAfter')}</label>
                          <Input
                            type="number"
                            min={1}
                            max={1000}
                            className="h-8 w-24"
                            aria-label={t('campaign.wizard.maxOccurrences')}
                            disabled={draft.schedule.maxOccurrences == null}
                            value={draft.schedule.maxOccurrences ?? 10}
                            onChange={(e) => setSchedule({ maxOccurrences: Math.min(1000, Math.max(1, Math.floor(Number(e.target.value) || 1))) })}
                          />
                          <span className="text-muted-foreground">{t('campaign.wizard.occurrences')}</span>
                        </div>
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
                  {draft.goal.mode === 'AUTO_OCS' && (
                    // SRS 3.2.6.1 #5–#13: nguồn → bảng → điều kiện (thuộc tính, toán tử, giá trị / Rỗng, AND / OR)
                    <section className="grid gap-4 rounded-xl border p-4">
                      <div className="grid gap-4 md:grid-cols-2">
                        <Field label={t('campaign.wizard.goalSource')} required>
                          <Select value="OCS" onValueChange={() => undefined} options={[{ value: 'OCS', label: t('campaign.wizard.goalSourceOcs') }]} />
                        </Field>
                        <Field label={t('campaign.wizard.goalTable')} required>
                          <Select value="all_report_stl_new" onValueChange={() => undefined} options={[{ value: 'all_report_stl_new', label: 'all_report_stl_new' }]} />
                        </Field>
                      </div>
                      <div>
                        <div className="mb-2 text-sm font-medium">{t('campaign.wizard.goalConditions')}</div>
                        <ConditionEditor allowEmpty conditions={draft.goal.conditions ?? []} onChange={(c) => set('goal', { ...draft.goal, conditions: c })} />
                        <p className="mt-2 text-xs text-muted-foreground">{t('campaign.wizard.goalConditionsHint')}</p>
                      </div>
                      <Field label={t('campaign.wizard.goalDesc')}>
                        <Textarea rows={2} value={draft.goal.description} onChange={(e) => set('goal', { ...draft.goal, description: e.target.value })} placeholder={t('campaign.wizard.goalDescPh')} />
                      </Field>
                    </section>
                  )}
                  {draft.goal.mode === 'MANUAL_FILE' && (
                    // SRS 3.2.6.2: thông báo tải danh sách sau khi chạy + nút tải file mẫu
                    <div className="flex flex-col gap-3 rounded-xl border border-info/25 bg-info-soft px-4 py-3 text-sm text-info sm:flex-row sm:items-center">
                      <Info className="size-4 shrink-0" />
                      <p className="flex-1">{t('campaign.wizard.manualGoalNote')}</p>
                      <Button type="button" variant="outline" size="sm" onClick={() => downloadConversionTemplate().catch((e) => toast.error(errorMessage(e)))}>
                        <Download />
                        {t('campaign.wizard.downloadTemplate')}
                      </Button>
                    </div>
                  )}
                  {draft.goal.mode !== 'NONE' && (
                    // SRS 3.2.6.1 #14: thời gian ghi nhận chuyển đổi, kể từ khi chiến dịch chạy
                    <section className="rounded-xl border p-4">
                      <h3 className="text-sm font-semibold">{t('campaign.wizard.attribution')}</h3>
                      <p className="mt-0.5 text-xs text-muted-foreground">{t('campaign.wizard.attributionDesc')}</p>
                      <div className="mt-3 flex flex-wrap items-center gap-3 text-sm">
                        <Select
                          className="w-32"
                          value={String(draft.goal.attributionWindowDays)}
                          onValueChange={(v) => set('goal', { ...draft.goal, attributionWindowDays: Number(v) })}
                          options={[...new Set([1, 3, 7, 14, 30, 60, 90, draft.goal.attributionWindowDays])].sort((a, b) => a - b).map((n) => ({ value: String(n), label: t('campaign.wizard.nDays', { n }) }))}
                        />
                        <span className="text-muted-foreground">{t('campaign.wizard.attributionHint')}</span>
                      </div>
                    </section>
                  )}
                  {/* SRS 3.2.6.1 #15: điều kiện dừng — loại khách hàng khỏi các chu kỳ / bước tiếp theo */}
                  <section className="rounded-xl border p-4">
                    <h3 className="text-sm font-semibold">{t('campaign.wizard.stopTitle')}</h3>
                    <p className="mt-0.5 text-xs text-muted-foreground">{t('campaign.wizard.stopDesc')}</p>
                    <div className="mt-3 grid gap-3 text-sm">
                      <label className="flex items-center gap-3">
                        <Checkbox checked={draft.goal.mode !== 'NONE' && draft.goal.stopOnGoal} disabled={draft.goal.mode === 'NONE'} onCheckedChange={(v) => set('goal', { ...draft.goal, stopOnGoal: v === true })} />
                        {t('campaign.wizard.stopOnGoal')}
                      </label>
                      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
                        <Checkbox
                          id="stop-noresp"
                          checked={draft.goal.maxNoResponse != null}
                          onCheckedChange={(v) => set('goal', { ...draft.goal, maxNoResponse: v === true ? 3 : null })}
                        />
                        <label htmlFor="stop-noresp">{t('campaign.wizard.noResponseBefore')}</label>
                        <Input
                          type="number"
                          min={1}
                          max={100}
                          className="h-8 w-20"
                          aria-label={t('campaign.wizard.maxNoResponse')}
                          disabled={draft.goal.maxNoResponse == null}
                          value={draft.goal.maxNoResponse ?? 3}
                          onChange={(e) => set('goal', { ...draft.goal, maxNoResponse: Math.min(100, Math.max(1, Math.floor(Number(e.target.value) || 1))) })}
                        />
                        <span className="text-muted-foreground">{t('campaign.wizard.noResponseAfter')}</span>
                      </div>
                      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
                        <Checkbox id="stop-reentry" checked={draft.goal.reentryDays != null} onCheckedChange={(v) => set('goal', { ...draft.goal, reentryDays: v === true ? 30 : null })} />
                        <label htmlFor="stop-reentry">{t('campaign.wizard.reentryBefore')}</label>
                        <Input
                          type="number"
                          min={1}
                          max={365}
                          className="h-8 w-20"
                          aria-label={t('campaign.wizard.reentryDays')}
                          disabled={draft.goal.reentryDays == null}
                          value={draft.goal.reentryDays ?? 30}
                          onChange={(e) => set('goal', { ...draft.goal, reentryDays: Math.min(365, Math.max(1, Math.floor(Number(e.target.value) || 1))) })}
                        />
                        <span className="text-muted-foreground">{t('campaign.wizard.daysUnit')}</span>
                      </div>
                    </div>
                  </section>
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
      {segDialog && (
        // SRS 3.5.1: tạo tệp ngay trong bước "Tệp khách hàng mục tiêu", tạo xong chọn luôn — không rời wizard
        <CreateSegmentDialog
          open
          onOpenChange={setSegDialog}
          onCreated={(s) => {
            set('segmentId', s.id)
            setDone((d) => new Set(d).add('audience'))
          }}
        />
      )}
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
