import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Ban, CheckCircle2, CircleDashed, Globe, Hash, Info, LayoutGrid, ListChecks, PlugZap, Server, Tag, Trash2, XCircle } from 'lucide-react'
import { ApiError, errorMessage } from '@/api/client'
import type { NotificationSettings, SenderNumber } from '@/api/types'
import { SENDER_CHANNELS } from '@/api/types'
import { useNotificationSettings, useSaveSender, useSenderNumber, useTestSenderConnection } from '@/features/campaign/api'
import { useAdminWorkspaces } from '@/features/workspace/api'
import { Field } from '@/components/common/dialogs'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogTitle, SheetContent } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Checkbox, RadioCard, RadioGroup, Skeleton, Switch, Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/misc'
import { Select } from '@/components/ui/select'
import { fmtDateTime, fmtNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import { formFromSender, formToInput, PROTOCOLS, SILENT_MODES, tabOfField, type SenderForm, type SenderTab, type SilentMode } from './senderForm'

const TABS: SenderTab[] = ['info', 'connection', 'scope', 'policy']

/**
 * Drawer thêm / sửa một đầu số: Thông tin · Kết nối · Phạm vi · Chính sách.
 * `target` = 'new' khi tạo mới. Nội dung chỉ mount khi có target; `seq` đổi mỗi lần mở để form khởi tạo lại.
 */
export function SenderSheet({
  open,
  onOpenChange,
  target,
  seq,
  onCreated,
  onDelete,
}: {
  open: boolean
  onOpenChange: (o: boolean) => void
  target: SenderNumber | 'new' | null
  seq: number
  onCreated: (s: SenderNumber) => void
  onDelete: (s: SenderNumber) => void
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <SheetContent className="max-w-2xl">
        {target && (
          <SenderEditor
            key={`${seq}-${target === 'new' ? 'new' : target.id}`}
            initial={target === 'new' ? null : target}
            onClose={() => onOpenChange(false)}
            onCreated={onCreated}
            onDelete={onDelete}
          />
        )}
      </SheetContent>
    </Dialog>
  )
}

function SenderEditor({
  initial,
  onClose,
  onCreated,
  onDelete,
}: {
  initial: SenderNumber | null
  onClose: () => void
  onCreated: (s: SenderNumber) => void
  onDelete: (s: SenderNumber) => void
}) {
  const { t } = useTranslation()
  const creating = initial == null
  // Dữ liệu mới nhất (kết quả kiểm tra, đã gửi hôm nay…) — form vẫn giữ bản đang sửa
  const live = useSenderNumber(initial?.id).data ?? initial
  const global = useNotificationSettings().data
  const save = useSaveSender()
  const test = useTestSenderConnection()
  const [base, setBase] = React.useState<SenderForm>(() => formFromSender(initial))
  const [form, setForm] = React.useState<SenderForm>(base)
  const [tab, setTab] = React.useState<SenderTab>('info')
  const [errors, setErrors] = React.useState<Record<string, string>>({})
  const dirty = JSON.stringify(form) !== JSON.stringify(base)
  const set = (patch: Partial<SenderForm>) => setForm((f) => ({ ...f, ...patch }))
  const err = (field: string) => errors[field] ?? null
  const tabHasError = (tb: SenderTab) => Object.keys(errors).some((f) => tabOfField(f) === tb)

  const submit = () =>
    save.mutate(
      { id: initial?.id, body: formToInput(form) },
      {
        onSuccess: (s) => {
          toast.success(creating ? t('sending.sheet.created', { code: s.code }) : t('common.saved'))
          setErrors({})
          if (creating) {
            onCreated(s)
            return
          }
          const next = formFromSender(s)
          setBase(next)
          setForm(next)
        },
        onError: (e) => {
          if (e instanceof ApiError && e.errors.length) {
            setErrors(Object.fromEntries(e.errors.map((x) => [x.field, x.message])))
            setTab(tabOfField(e.errors[0].field))
          }
          toast.error(errorMessage(e))
        },
      },
    )

  return (
    <form
      className="flex h-full min-h-0 flex-col"
      onSubmit={(e) => {
        e.preventDefault()
        submit()
      }}
    >
      <header className="flex items-center gap-3 border-b px-6 py-5 pr-14">
        <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-accent text-accent-foreground">
          {form.kind === 'BRANDNAME' ? <Tag className="size-5" /> : <Hash className="size-5" />}
        </span>
        <div className="min-w-0 flex-1">
          <DialogTitle className={cn('truncate', !creating && 'font-mono')}>{creating ? t('sending.sheet.newTitle') : live?.code}</DialogTitle>
          <DialogDescription className="truncate">
            {creating ? t('sending.sheet.newDesc') : live?.description || t(`sending.kind.${live?.kind ?? 'SHORTCODE'}`)}
          </DialogDescription>
        </div>
        {!creating && live && <Badge variant={live.enabled ? 'success' : 'neutral'}>{t(live.enabled ? 'sending.status.enabled' : 'sending.status.disabled')}</Badge>}
      </header>

      <Tabs value={tab} onValueChange={(v) => setTab(v as SenderTab)} className="flex min-h-0 flex-1 flex-col">
        <div className="px-6 pt-3">
          <TabsList variant="underline" className="overflow-x-auto scrollbar-thin">
            {TABS.map((tb) => (
              <TabsTrigger key={tb} value={tb}>
                {t(`sending.sheet.tab.${tb}`)}
                {tabHasError(tb) && <span className="size-1.5 rounded-full bg-danger" aria-hidden />}
              </TabsTrigger>
            ))}
          </TabsList>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5 scrollbar-thin">
          <TabsContent value="info" className="grid gap-5">
            <InfoTab form={form} set={set} err={err} live={live} />
          </TabsContent>
          <TabsContent value="connection" className="grid gap-5">
            <ConnectionTab
              form={form}
              set={set}
              err={err}
              live={live}
              canTest={!creating && !dirty && live?.gateway.protocol !== 'NONE'}
              testHint={creating ? t('sending.conn.testCreateFirst') : dirty ? t('sending.conn.testSaveFirst') : null}
              testing={test.isPending}
              onTest={() =>
                live &&
                test.mutate(live.id, {
                  onSuccess: (r) => {
                    const msg = `${t(`sending.test.${r.code}`)}${r.detail ? ` — ${r.detail}` : ''}`
                    if (r.ok) toast.success(msg)
                    else toast.error(msg)
                  },
                  onError: (e) => toast.error(errorMessage(e)),
                })
              }
            />
          </TabsContent>
          <TabsContent value="scope" className="grid gap-5">
            <ScopeTab form={form} set={set} err={err} />
          </TabsContent>
          <TabsContent value="policy" className="grid gap-5">
            <PolicyTab form={form} set={set} err={err} global={global} />
          </TabsContent>
        </div>
      </Tabs>

      <footer className="flex flex-wrap items-center gap-2 border-t px-6 py-4">
        {!creating && live && (
          <Button type="button" variant="ghost" className="text-danger hover:bg-danger-soft hover:text-danger" onClick={() => onDelete(live)}>
            <Trash2 />
            {t('common.delete')}
          </Button>
        )}
        {!creating && dirty && <span className="text-xs text-muted-foreground">{t('sending.sheet.unsaved')}</span>}
        <div className="ml-auto flex gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={save.isPending}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" loading={save.isPending} disabled={!creating && !dirty}>
            {creating ? t('sending.addSender') : t('common.save')}
          </Button>
        </div>
      </footer>
    </form>
  )
}

type TabProps = { form: SenderForm; set: (p: Partial<SenderForm>) => void; err: (field: string) => string | null }

// ───────────── Thông tin ─────────────
function InfoTab({ form, set, err, live }: TabProps & { live: SenderNumber | null }) {
  const { t } = useTranslation()
  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t('sending.code')} required error={err('code')} hint={t('sending.codeHint')}>
          <Input value={form.code} onChange={(e) => set({ code: e.target.value.toUpperCase() })} className="font-mono" maxLength={32} required autoFocus={!live} />
        </Field>
        <Field label={t('sending.kindLabel')} error={err('kind')}>
          <Select
            value={form.kind}
            onValueChange={(v) => {
              const kind = v as SenderForm['kind']
              set({ kind, channels: kind === 'BRANDNAME' ? ['SMS'] : form.channels })
            }}
            options={(['SHORTCODE', 'BRANDNAME'] as const).map((k) => ({ value: k, label: t(`sending.kind.${k}`) }))}
          />
        </Field>
      </div>
      <Field label={t('sending.description')} error={err('description')}>
        <Input value={form.description} onChange={(e) => set({ description: e.target.value })} maxLength={255} />
      </Field>
      <Field label={t('sending.channels')} error={err('channels')} hint={t('sending.channelsHint')}>
        <div className="flex flex-wrap gap-2">
          {SENDER_CHANNELS.map((c) => {
            const disabled = c === 'USSD' && form.kind === 'BRANDNAME'
            const checked = form.channels.includes(c)
            return (
              <label
                key={c}
                className={cn(
                  'flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm transition has-[[data-state=checked]]:border-brand-500',
                  disabled && 'cursor-not-allowed opacity-50',
                )}
              >
                <Checkbox
                  checked={checked}
                  disabled={disabled}
                  onCheckedChange={(v) => set({ channels: v ? [...form.channels, c] : form.channels.filter((x) => x !== c) })}
                />
                {t(`channel.${c}`)}
              </label>
            )
          })}
        </div>
      </Field>
      <label className="flex items-start gap-3 rounded-xl border p-4">
        <Switch checked={form.enabled} onCheckedChange={(v) => set({ enabled: v })} className="mt-0.5" />
        <span className="grid gap-1">
          <span className="text-sm font-medium">{t('sending.enabledLabel')}</span>
          <span className="text-xs text-muted-foreground">{t('sending.enabledHint')}</span>
        </span>
      </label>
      {live && (
        <dl className="grid grid-cols-[9rem_1fr] gap-x-4 gap-y-2.5 rounded-xl bg-muted/50 p-4 text-sm">
          <dt className="text-muted-foreground">{t('sending.sentToday')}</dt>
          <dd className="tabular">
            {fmtNumber(live.sentToday)} / {live.policy.dailyLimit != null ? fmtNumber(live.policy.dailyLimit) : t('sending.unlimited')}
          </dd>
          <dt className="text-muted-foreground">{t('common.createdAt')}</dt>
          <dd className="tabular">
            {fmtDateTime(live.createdAt)}
            {live.createdBy && <span className="text-muted-foreground"> · {live.createdBy}</span>}
          </dd>
          <dt className="text-muted-foreground">{t('common.updatedAt')}</dt>
          <dd className="tabular">
            {live.updatedAt ? fmtDateTime(live.updatedAt) : '—'}
            {live.updatedBy && <span className="text-muted-foreground"> · {live.updatedBy}</span>}
          </dd>
        </dl>
      )}
    </>
  )
}

// ───────────── Kết nối ─────────────
const PROTOCOL_ICON = { NONE: Ban, SMPP: Server, HTTP: Globe } as const

function ConnectionTab({
  form,
  set,
  err,
  live,
  canTest,
  testHint,
  testing,
  onTest,
}: TabProps & { live: SenderNumber | null; canTest: boolean; testHint: string | null; testing: boolean; onTest: () => void }) {
  const { t } = useTranslation()
  const hasPassword = !!live?.gateway.hasPassword
  const last = live?.gateway.lastTest ?? null
  return (
    <>
      <RadioGroup value={form.protocol} onValueChange={(v) => set({ protocol: v as SenderForm['protocol'] })} className="grid gap-3 sm:grid-cols-3">
        {PROTOCOLS.map((p) => (
          <RadioCard key={p} value={p} icon={React.createElement(PROTOCOL_ICON[p], { className: 'size-5' })} title={t(`sending.protocol.${p}`)} description={t(`sending.protocol.${p}_desc`)} className="p-3" />
        ))}
      </RadioGroup>
      {err('gateway.protocol') && <p className="text-xs text-danger">{err('gateway.protocol')}</p>}

      {form.protocol === 'SMPP' && (
        <div className="grid gap-4 sm:grid-cols-[1fr_8rem]">
          <Field label={t('sending.conn.host')} required error={err('gateway.host')}>
            <Input value={form.host} onChange={(e) => set({ host: e.target.value })} placeholder="smsc.unitel.com.la" className="font-mono" maxLength={255} />
          </Field>
          <Field label={t('sending.conn.port')} required error={err('gateway.port')}>
            <Input type="number" inputMode="numeric" min={1} max={65535} value={form.port} onChange={(e) => set({ port: e.target.value })} className="tabular" />
          </Field>
          <Field label={t('sending.conn.systemId')} required error={err('gateway.username')} hint={t('sending.conn.systemIdHint')}>
            <Input value={form.username} onChange={(e) => set({ username: e.target.value })} className="font-mono" maxLength={15} autoComplete="off" />
          </Field>
          <Field label={t('sending.conn.systemType')} error={err('gateway.systemType')}>
            <Input value={form.systemType} onChange={(e) => set({ systemType: e.target.value })} className="font-mono" maxLength={12} />
          </Field>
          <PasswordField form={form} set={set} err={err} hasPassword={hasPassword} label={t('sending.conn.password')} hint={t('sending.conn.smppPasswordHint')} maxLength={8} />
          <TpsField form={form} set={set} err={err} />
        </div>
      )}
      {form.protocol === 'HTTP' && (
        <div className="grid gap-4 sm:grid-cols-[1fr_8rem]">
          <Field label={t('sending.conn.url')} required error={err('gateway.url')} className="sm:col-span-2">
            <Input type="url" value={form.url} onChange={(e) => set({ url: e.target.value })} placeholder="https://sms-api.example.com/send" className="font-mono" maxLength={1000} />
          </Field>
          <Field label={t('sending.conn.username')} error={err('gateway.username')} className="sm:col-span-2">
            <Input value={form.username} onChange={(e) => set({ username: e.target.value })} className="font-mono" maxLength={128} autoComplete="off" />
          </Field>
          <PasswordField form={form} set={set} err={err} hasPassword={hasPassword} label={t('sending.conn.token')} maxLength={256} />
          <TpsField form={form} set={set} err={err} />
        </div>
      )}
      {form.protocol === 'NONE' && (
        <p className="rounded-xl bg-muted/50 p-4 text-sm text-muted-foreground">{t('sending.conn.noneNote')}</p>
      )}

      {(form.protocol !== 'NONE' || last) && (
        <section className="grid gap-3 rounded-xl border p-4">
          <div className="flex flex-wrap items-center gap-3">
            <div className="min-w-0 flex-1">
              <h4 className="text-sm font-semibold">{t('sending.conn.lastTest')}</h4>
              {last ? (
                <p className={cn('mt-1 flex items-start gap-1.5 text-sm', last.ok ? 'text-success' : 'text-danger')}>
                  {last.ok ? <CheckCircle2 className="mt-0.5 size-4 shrink-0" /> : <XCircle className="mt-0.5 size-4 shrink-0" />}
                  <span>
                    {t(`sending.test.${last.code}`)}
                    {last.detail && <span className="text-muted-foreground"> — {last.detail}</span>}
                    <span className="block text-xs text-muted-foreground tabular">
                      {fmtDateTime(last.testedAt)}
                      {last.latencyMs != null && ` · ${t('sending.conn.latency', { ms: last.latencyMs })}`}
                    </span>
                  </span>
                </p>
              ) : (
                <p className="mt-1 flex items-center gap-1.5 text-sm text-muted-foreground">
                  <CircleDashed className="size-4" />
                  {t('sending.conn.neverTested')}
                </p>
              )}
            </div>
            <Button type="button" variant="outline" size="sm" onClick={onTest} disabled={!canTest} loading={testing}>
              <PlugZap />
              {t('sending.conn.test')}
            </Button>
          </div>
          {testHint && <p className="text-xs text-muted-foreground">{testHint}</p>}
        </section>
      )}
      <p className="flex items-start gap-2 text-xs text-muted-foreground">
        <Info className="mt-0.5 size-3.5 shrink-0" />
        {t('sending.conn.simulatedNote')}
      </p>
    </>
  )
}

function PasswordField({
  form,
  set,
  err,
  hasPassword,
  label,
  hint,
  maxLength,
}: TabProps & { hasPassword: boolean; label: string; hint?: string; maxLength: number }) {
  const { t } = useTranslation()
  return (
    <Field label={label} error={err('gateway.password')} hint={hint}>
      <Input
        type="password"
        autoComplete="new-password"
        value={form.password}
        maxLength={maxLength}
        disabled={form.clearPassword}
        placeholder={hasPassword && !form.clearPassword ? t('sending.conn.passwordSaved') : undefined}
        onChange={(e) => set({ password: e.target.value })}
      />
      {hasPassword && (
        <span className="text-xs">
          {form.clearPassword ? (
            <>
              <span className="text-warning">{t('sending.conn.passwordWillClear')}</span>{' '}
              <button type="button" className="font-medium text-brand-600 hover:underline" onClick={() => set({ clearPassword: false })}>
                {t('sending.conn.undo')}
              </button>
            </>
          ) : (
            <button type="button" className="font-medium text-danger hover:underline" onClick={() => set({ clearPassword: true, password: '' })}>
              {t('sending.conn.clearPassword')}
            </button>
          )}
        </span>
      )}
    </Field>
  )
}

function TpsField({ form, set, err }: TabProps) {
  const { t } = useTranslation()
  return (
    <Field label={t('sending.conn.tps')} error={err('gateway.tps')} hint={t('sending.conn.tpsHint')}>
      <Input type="number" inputMode="numeric" min={1} max={10000} value={form.tps} onChange={(e) => set({ tps: e.target.value })} placeholder="∞" className="tabular" />
    </Field>
  )
}

// ───────────── Phạm vi ─────────────
function ScopeTab({ form, set, err }: TabProps) {
  const { t } = useTranslation()
  const workspaces = useAdminWorkspaces({ page: 0, size: 200 })
  const items = workspaces.data?.items ?? []
  return (
    <>
      <RadioGroup value={form.allWorkspaces ? 'all' : 'selected'} onValueChange={(v) => set({ allWorkspaces: v === 'all' })} className="grid gap-3 sm:grid-cols-2">
        <RadioCard value="all" icon={<LayoutGrid className="size-5" />} title={t('sending.scope.all')} description={t('sending.scope.all_desc')} />
        <RadioCard value="selected" icon={<ListChecks className="size-5" />} title={t('sending.scope.selected')} description={t('sending.scope.selected_desc')} />
      </RadioGroup>
      {!form.allWorkspaces && (
        <Field
          label={
            <span className="flex w-full items-center justify-between gap-2">
              {t('sending.scope.pick')}
              <span className="text-xs font-normal text-muted-foreground">{t('sending.scope.selectedCount', { count: form.workspaceIds.length })}</span>
            </span>
          }
          error={err('workspaceIds')}
        >
          {workspaces.isLoading ? (
            <Skeleton className="h-40" />
          ) : items.length === 0 ? (
            <p className="rounded-xl border p-4 text-sm text-muted-foreground">{t('sending.scope.empty')}</p>
          ) : (
            <ul className="max-h-80 divide-y overflow-y-auto rounded-xl border scrollbar-thin">
              {items.map((w) => (
                <li key={w.id}>
                  <label className="flex cursor-pointer items-center gap-3 px-4 py-2.5 hover:bg-muted/50">
                    <Checkbox
                      checked={form.workspaceIds.includes(w.id)}
                      onCheckedChange={(v) => set({ workspaceIds: v ? [...form.workspaceIds, w.id] : form.workspaceIds.filter((x) => x !== w.id) })}
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{w.name}</span>
                      <span className="block font-mono text-xs text-muted-foreground">{w.code}</span>
                    </span>
                    {w.status !== 'ACTIVE' && <Badge variant="warning">{t(`activeStatus.${w.status}`)}</Badge>}
                  </label>
                </li>
              ))}
            </ul>
          )}
        </Field>
      )}
    </>
  )
}

// ───────────── Chính sách ─────────────
function PolicyTab({ form, set, err, global }: TabProps & { global: NotificationSettings | undefined }) {
  const { t } = useTranslation()
  const globalSilent = !global ? '…' : global.silentHoursEnabled ? `${global.silentFrom}–${global.silentTo}` : t('sending.pol.globalSilentOff')
  return (
    <>
      <p className="flex items-start gap-2 rounded-xl bg-info-soft p-4 text-sm text-info">
        <Info className="mt-0.5 size-4 shrink-0" />
        {t('sending.pol.note')}
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        <OverrideNumber
          label={t('sending.retryMax')}
          on={form.retryOn}
          onToggle={(v) => set({ retryOn: v, retryMax: v && form.retryMax === '' ? String(global?.retryMaxAttempts ?? 3) : form.retryMax })}
          value={form.retryMax}
          onChange={(v) => set({ retryMax: v })}
          inherited={global?.retryMaxAttempts}
          min={0}
          max={10}
          error={err('policy.retryMaxAttempts')}
        />
        <OverrideNumber
          label={t('sending.retryInterval')}
          on={form.intervalOn}
          onToggle={(v) => set({ intervalOn: v, retryInterval: v && form.retryInterval === '' ? String(global?.retryIntervalMinutes ?? 30) : form.retryInterval })}
          value={form.retryInterval}
          onChange={(v) => set({ retryInterval: v })}
          inherited={global?.retryIntervalMinutes}
          min={1}
          max={1440}
          error={err('policy.retryIntervalMinutes')}
        />
      </div>
      <section className="grid gap-4 border-t pt-5">
        <Field label={t('campaign.wizard.silent')}>
          <Select
            value={form.silentMode}
            onValueChange={(v) => set({ silentMode: v as SilentMode })}
            options={SILENT_MODES.map((m) => ({ value: m, label: m === 'INHERIT' ? `${t('sending.pol.silentMode.INHERIT')} (${globalSilent})` : t(`sending.pol.silentMode.${m}`) }))}
          />
        </Field>
        {form.silentMode === 'CUSTOM' && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t('common.from')} error={err('policy.silentFrom')}>
              <Input type="time" value={form.silentFrom} onChange={(e) => set({ silentFrom: e.target.value })} />
            </Field>
            <Field label={t('common.to')} error={err('policy.silentTo')}>
              <Input type="time" value={form.silentTo} onChange={(e) => set({ silentTo: e.target.value })} />
            </Field>
          </div>
        )}
        {form.silentMode === 'OFF' && <p className="text-xs text-warning">{t('sending.pol.silentOffWarn')}</p>}
      </section>
      <section className="grid gap-4 border-t pt-5">
        <Field label={t('sending.pol.dailyLimit')} error={err('policy.dailyLimit')} hint={t('sending.pol.dailyLimitHint')}>
          <Input
            type="number"
            inputMode="numeric"
            min={1}
            value={form.dailyLimit}
            onChange={(e) => set({ dailyLimit: e.target.value })}
            placeholder={t('sending.unlimited')}
            className="tabular sm:max-w-60"
          />
        </Field>
      </section>
    </>
  )
}

function OverrideNumber({
  label,
  on,
  onToggle,
  value,
  onChange,
  inherited,
  min,
  max,
  error,
}: {
  label: string
  on: boolean
  onToggle: (v: boolean) => void
  value: string
  onChange: (v: string) => void
  inherited: number | undefined
  min: number
  max: number
  error: string | null
}) {
  const { t } = useTranslation()
  return (
    <Field label={label} error={error} hint={on ? null : t('sending.pol.inherit', { value: inherited ?? '…' })}>
      <div className="flex items-center gap-3">
        <label className="flex shrink-0 items-center gap-2 text-sm">
          <Switch checked={on} onCheckedChange={onToggle} />
          {t('sending.pol.custom')}
        </label>
        <Input
          type="number"
          inputMode="numeric"
          min={min}
          max={max}
          disabled={!on}
          value={on ? value : ''}
          placeholder={inherited != null ? String(inherited) : undefined}
          onChange={(e) => onChange(e.target.value)}
          className="tabular"
        />
      </div>
    </Field>
  )
}
