import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Hash, MoonStar, Pencil, Plus, RefreshCcw, Send, Tag, Trash2 } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { NotificationSettings, SenderChannel, SenderNumber } from '@/api/types'
import { SENDER_CHANNELS } from '@/api/types'
import {
  useDeleteSender,
  useNotificationSettings,
  useSaveNotificationSettings,
  useSenderNumbers,
  useToggleSender,
} from '@/features/campaign/api'
import { useAdminWorkspaces } from '@/features/workspace/api'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { SearchInput } from '@/components/common/SearchInput'
import { ConfirmDialog, Field } from '@/components/common/dialogs'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Progress, Skeleton, Switch, Tabs, TabsContent, TabsList, TabsTrigger, Tooltip } from '@/components/ui/misc'
import { fmtDateTime, fmtNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import { SenderSheet } from './SenderSheet'
import { hasCustomPolicy } from './senderForm'

export default function SendingSettingsPage() {
  const { t } = useTranslation()
  return (
    <>
      <PageHeader icon={<Send />} title={t('nav.adminSending')} description={t('sending.desc')} />
      <Tabs defaultValue="senders">
        <TabsList className="mb-5">
          <TabsTrigger value="senders">
            <Hash />
            {t('sending.tabs.senders')}
          </TabsTrigger>
          <TabsTrigger value="policy">
            <RefreshCcw />
            {t('sending.tabs.policy')}
          </TabsTrigger>
        </TabsList>
        <TabsContent value="senders">
          <SendersTab />
        </TabsContent>
        <TabsContent value="policy">
          <GlobalPolicyCard />
        </TabsContent>
      </Tabs>
    </>
  )
}

// ───────────── Tab Đầu số: bảng + drawer ─────────────
function SendersTab() {
  const { t } = useTranslation()
  const senders = useSenderNumbers()
  const toggle = useToggleSender()
  const del = useDeleteSender()
  const workspaces = useAdminWorkspaces({ page: 0, size: 200 })
  const wsName = React.useMemo(() => new Map((workspaces.data?.items ?? []).map((w) => [w.id, w.code])), [workspaces.data])

  const [query, setQuery] = React.useState('')
  const [kind, setKind] = React.useState('')
  const [channel, setChannel] = React.useState('')
  const [status, setStatus] = React.useState('')

  // Drawer: target giữ lại khi đóng để nội dung không biến mất giữa animation; seq đổi mỗi lần mở để form khởi tạo lại
  const [sheetOpen, setSheetOpen] = React.useState(false)
  const [target, setTarget] = React.useState<SenderNumber | 'new' | null>(null)
  const [seq, setSeq] = React.useState(0)
  const openSheet = (s: SenderNumber | 'new') => {
    setTarget(s)
    setSeq((n) => n + 1)
    setSheetOpen(true)
  }

  const [toDelete, setToDelete] = React.useState<SenderNumber | null>(null)
  const [deleteOpen, setDeleteOpen] = React.useState(false)
  const askDelete = (s: SenderNumber) => {
    setToDelete(s)
    setDeleteOpen(true)
  }

  const q = query.trim().toLowerCase()
  const rows = (senders.data ?? []).filter(
    (s) =>
      (!q || s.code.toLowerCase().includes(q) || (s.description ?? '').toLowerCase().includes(q)) &&
      (!kind || s.kind === kind) &&
      (!channel || s.channels.includes(channel as SenderChannel)) &&
      (!status || (status === 'ENABLED') === s.enabled),
  )

  const stop = (e: React.SyntheticEvent) => e.stopPropagation()
  const columns: Column<SenderNumber>[] = [
    {
      key: 'code',
      header: t('sending.col.code'),
      cell: (s) => (
        <div className="flex items-center gap-3">
          <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-muted text-muted-foreground">
            {s.kind === 'SHORTCODE' ? <Hash className="size-4" /> : <Tag className="size-4" />}
          </span>
          <div className="min-w-0">
            <div className="font-mono text-sm font-semibold">{s.code}</div>
            <div className="text-xs text-muted-foreground">{t(`sending.kind.${s.kind}`)}</div>
          </div>
        </div>
      ),
    },
    {
      key: 'description',
      header: t('sending.col.description'),
      cell: (s) => <span className="line-clamp-2 max-w-56 text-sm text-muted-foreground">{s.description ?? '—'}</span>,
    },
    {
      key: 'channels',
      header: t('sending.col.channels'),
      cell: (s) => (
        <div className="flex flex-wrap gap-1">
          {s.channels.map((c) => (
            <Badge key={c} variant="outline">
              {t(`channel.${c}`)}
            </Badge>
          ))}
        </div>
      ),
    },
    { key: 'connection', header: t('sending.col.connection'), cell: (s) => <ConnectionCell sender={s} /> },
    {
      key: 'scope',
      header: t('sending.col.scope'),
      cell: (s) => {
        if (s.allWorkspaces) return <span className="text-sm">{t('sending.scope.allShort')}</span>
        const names = s.workspaceIds.map((id) => wsName.get(id) ?? `#${id}`)
        return (
          <Tooltip content={names.join(', ')}>
            <span className="text-sm underline decoration-dotted underline-offset-4">{t('sending.scope.count', { count: s.workspaceIds.length })}</span>
          </Tooltip>
        )
      },
    },
    {
      key: 'today',
      header: t('sending.col.today'),
      align: 'right',
      cell: (s) => (
        <div className="ml-auto grid w-28 gap-1 text-right">
          <span className="text-sm tabular">
            {fmtNumber(s.sentToday)}
            <span className="text-muted-foreground"> / {s.policy.dailyLimit != null ? fmtNumber(s.policy.dailyLimit) : '∞'}</span>
          </span>
          {s.policy.dailyLimit != null && (
            <Progress
              value={(s.sentToday / s.policy.dailyLimit) * 100}
              indicatorClassName={s.sentToday >= s.policy.dailyLimit ? 'bg-danger' : undefined}
            />
          )}
        </div>
      ),
    },
    {
      key: 'policy',
      header: t('sending.col.policy'),
      cell: (s) =>
        hasCustomPolicy(s.policy) ? <Badge variant="brand">{t('sending.policyCustom')}</Badge> : <Badge variant="neutral">{t('sending.policyDefault')}</Badge>,
    },
    {
      key: 'enabled',
      header: t('sending.col.enabled'),
      align: 'center',
      cell: (s) => (
        <span onClick={stop} onKeyDown={stop} className="inline-flex">
          <Switch
            checked={s.enabled}
            disabled={toggle.isPending && toggle.variables?.id === s.id}
            onCheckedChange={(v) =>
              toggle.mutate(
                { id: s.id, enabled: v },
                { onSuccess: (r) => toast.success(t(r.enabled ? 'sending.toggledOn' : 'sending.toggledOff', { code: r.code })), onError: (e) => toast.error(errorMessage(e)) },
              )
            }
            aria-label={`${t('sending.enabled')} ${s.code}`}
          />
        </span>
      ),
    },
    {
      key: 'actions',
      header: <span className="sr-only">{t('common.actions')}</span>,
      align: 'right',
      cell: (s) => (
        <div className="flex justify-end gap-1" onClick={stop} onKeyDown={stop}>
          <Button size="icon-sm" variant="ghost" onClick={() => openSheet(s)} aria-label={`${t('common.edit')} ${s.code}`} title={t('common.edit')}>
            <Pencil />
          </Button>
          <Button
            size="icon-sm"
            variant="ghost"
            className="text-muted-foreground hover:text-danger"
            onClick={() => askDelete(s)}
            aria-label={`${t('sending.deleteSender')} ${s.code}`}
            title={t('sending.deleteSender')}
          >
            <Trash2 />
          </Button>
        </div>
      ),
    },
  ]

  return (
    <>
      <DataTable
        toolbar={
          <>
            <SearchInput value={query} onChange={setQuery} placeholder={t('sending.searchPh')} className="w-full sm:w-64" />
            <div className="flex flex-wrap gap-2 sm:ml-auto">
              <Select className="w-36" value={kind} onValueChange={setKind} allLabel={t('sending.allKinds')} options={(['SHORTCODE', 'BRANDNAME'] as const).map((k) => ({ value: k, label: t(`sending.kind.${k}`) }))} />
              <Select className="w-32" value={channel} onValueChange={setChannel} allLabel={t('sending.allChannels')} options={SENDER_CHANNELS.map((c) => ({ value: c, label: t(`channel.${c}`) }))} />
              <Select className="w-36" value={status} onValueChange={setStatus} allLabel={t('common.allStatus')} options={(['ENABLED', 'DISABLED'] as const).map((v) => ({ value: v, label: t(`sending.status.${v === 'ENABLED' ? 'enabled' : 'disabled'}`) }))} />
              <Button onClick={() => openSheet('new')}>
                <Plus />
                {t('sending.addSender')}
              </Button>
            </div>
          </>
        }
        columns={columns}
        rows={rows}
        rowKey={(s) => s.id}
        loading={senders.isLoading}
        error={senders.isError ? errorMessage(senders.error) : undefined}
        onRowClick={openSheet}
      />
      <p className="mt-3 text-xs text-muted-foreground">{t('sending.sendersDesc')}</p>

      <SenderSheet
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        target={target}
        seq={seq}
        onCreated={(s) => openSheet(s)}
        onDelete={askDelete}
      />

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        destructive
        title={t('sending.deleteTitle', { code: toDelete?.code ?? '' })}
        description={t('sending.deleteDesc')}
        confirmLabel={t('common.delete')}
        loading={del.isPending}
        onConfirm={() =>
          toDelete &&
          del.mutate(toDelete.id, {
            onSuccess: () => {
              toast.success(t('common.deleted'))
              setDeleteOpen(false)
              setSheetOpen(false)
            },
            onError: (e) => {
              toast.error(errorMessage(e))
              setDeleteOpen(false)
            },
          })
        }
      />
    </>
  )
}

/** Giao thức + chấm trạng thái lần kiểm tra cuối. */
function ConnectionCell({ sender }: { sender: SenderNumber }) {
  const { t } = useTranslation()
  const g = sender.gateway
  if (g.protocol === 'NONE') return <span className="text-sm text-muted-foreground">{t('sending.notConfigured')}</span>
  const last = g.lastTest
  const tip = last ? `${t(`sending.test.${last.code}`)}${last.detail ? ` — ${last.detail}` : ''} · ${fmtDateTime(last.testedAt)}` : t('sending.conn.neverTested')
  return (
    <Tooltip content={tip}>
      <span className="inline-flex items-center gap-2 text-sm">
        <span className={cn('size-2 shrink-0 rounded-full', !last ? 'bg-muted-foreground/40' : last.ok ? 'bg-success' : 'bg-danger')} aria-hidden />
        <span className="font-medium">{t(`sending.protocol.${g.protocol}`)}</span>
        <span className="max-w-40 truncate font-mono text-xs text-muted-foreground">{g.protocol === 'SMPP' ? `${g.host ?? ''}:${g.port ?? ''}` : g.url}</span>
      </span>
    </Tooltip>
  )
}

// ───────────── Tab Chính sách gửi chung ─────────────
function GlobalPolicyCard() {
  const { t } = useTranslation()
  const settings = useNotificationSettings()
  const saveSettings = useSaveNotificationSettings()
  // Bản chỉnh sửa cục bộ; null = đang hiển thị đúng dữ liệu server
  const [edit, setForm] = React.useState<NotificationSettings | null>(null)
  const form = edit ?? settings.data ?? null
  return (
    <Card className="max-w-3xl">
      <CardHeader>
        <div>
          <CardTitle>{t('sending.policy')}</CardTitle>
          <CardDescription>{t('sending.policyDesc')}</CardDescription>
        </div>
      </CardHeader>
      <CardContent>
        {!form ? (
          <Skeleton className="h-64" />
        ) : (
          <form
            className="grid gap-6"
            onSubmit={(e) => {
              e.preventDefault()
              saveSettings.mutate(form, {
                onSuccess: () => {
                  setForm(null)
                  toast.success(t('common.saved'))
                },
                onError: (e) => toast.error(errorMessage(e)),
              })
            }}
          >
            <section className="grid gap-4">
              <h4 className="flex items-center gap-2 text-sm font-semibold">
                <RefreshCcw className="size-4 text-brand-600" />
                {t('sending.retry')}
              </h4>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label={t('sending.retryMax')}>
                  <Input type="number" min={0} max={10} step="any" value={form.retryMaxAttempts} onChange={(e) => setForm({ ...form, retryMaxAttempts: Number(e.target.value) })} />
                </Field>
                <Field label={t('sending.retryInterval')}>
                  <Input type="number" min={1} step="any" value={form.retryIntervalMinutes} onChange={(e) => setForm({ ...form, retryIntervalMinutes: Number(e.target.value) })} />
                </Field>
              </div>
              <label className="flex items-center gap-3 text-sm">
                <Switch checked={form.refundOnFailure} onCheckedChange={(v) => setForm({ ...form, refundOnFailure: v })} />
                {t('sending.refund')}
              </label>
            </section>
            <section className="grid gap-4 border-t pt-5">
              <h4 className="flex items-center gap-2 text-sm font-semibold">
                <MoonStar className="size-4 text-brand-600" />
                {t('campaign.wizard.silent')}
              </h4>
              <label className="flex items-center gap-3 text-sm">
                <Switch checked={form.silentHoursEnabled} onCheckedChange={(v) => setForm({ ...form, silentHoursEnabled: v })} />
                {t('sending.silentDefault')}
              </label>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label={t('common.from')}>
                  <Input type="time" value={form.silentFrom} disabled={!form.silentHoursEnabled} onChange={(e) => setForm({ ...form, silentFrom: e.target.value })} />
                </Field>
                <Field label={t('common.to')}>
                  <Input type="time" value={form.silentTo} disabled={!form.silentHoursEnabled} onChange={(e) => setForm({ ...form, silentTo: e.target.value })} />
                </Field>
              </div>
            </section>
            <section className="grid gap-4 border-t pt-5">
              <Field label={t('sending.defaultLang')}>
                <Select value={form.defaultLanguage} onValueChange={(v) => setForm({ ...form, defaultLanguage: v as NotificationSettings['defaultLanguage'] })} options={(['LO', 'EN', 'VI'] as const).map((l) => ({ value: l, label: t(`langName.${l}`) }))} />
              </Field>
            </section>
            <p className="text-xs text-muted-foreground">{t('sending.policyOverrideNote')}</p>
            <div>
              <Button type="submit" loading={saveSettings.isPending}>
                {t('common.saveChanges')}
              </Button>
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  )
}
