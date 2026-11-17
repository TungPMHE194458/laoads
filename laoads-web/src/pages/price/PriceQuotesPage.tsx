import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Copy, Eye, Lock, MoreHorizontal, Pencil, Plus, Trash2, Wallet } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { ActionType, PriceItem, PriceQuote } from '@/api/types'
import { NOTIFICATION_ACTIONS, TELECOM_ACTIONS } from '@/api/types'
import { useActivePriceQuote, useDeletePriceQuote, useLockPriceQuote, usePriceQuotes, useSavePriceQuote } from '@/features/price/api'
import { useIsSuperAdmin } from '@/features/auth/permissions'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { ConfirmDialog, Field } from '@/components/common/dialogs'
import { PriceStatusBadge } from '@/components/common/status'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Switch, Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/misc'
import { Dialog, DialogTitle, SheetContent } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { fmtDateTime, fmtKip } from '@/lib/format'
import { actionIcon } from '@/pages/campaigns/wizard/actionMeta'

const ALL: ActionType[] = [...NOTIFICATION_ACTIONS, ...TELECOM_ACTIONS]
const blankItems = (): PriceItem[] => ALL.map((a) => ({ actionType: a, unitPrice: 0, timePrice: 0, timePricePercentage: false }))

interface EditorState {
  /** Bảng nguồn để hiển thị / sao chép giá (null = tạo trống). */
  quote: PriceQuote | null
  /** Có id → cập nhật bảng DRAFT đó; không có → tạo mới (kể cả nhân bản). */
  editId?: number
  readOnly: boolean
  clone?: boolean
}

function Editor({ quote, editId, readOnly, clone, onClose }: EditorState & { onClose: () => void }) {
  const { t } = useTranslation()
  const save = useSavePriceQuote()
  const [name, setName] = React.useState(quote ? (clone ? `${quote.name} (copy)` : quote.name) : '')
  const [note, setNote] = React.useState(quote?.note ?? '')
  const [items, setItems] = React.useState<PriceItem[]>(() => {
    const base = blankItems()
    return base.map((b) => quote?.items.find((i) => i.actionType === b.actionType) ?? b)
  })
  const upd = (a: ActionType, p: Partial<PriceItem>) => setItems((xs) => xs.map((x) => (x.actionType === a ? { ...x, ...p } : x)))

  const section = (title: string, list: ActionType[]) => (
    <section>
      <h3 className="sr-only">{title}</h3>
      <div className="overflow-hidden rounded-xl border">
        <div className="grid grid-cols-[1fr_130px_150px] gap-3 border-b bg-muted/50 px-4 py-2 text-xs font-medium text-muted-foreground">
          <span>{t('price.action')}</span>
          <span className="text-right">{t('price.unitPrice')}</span>
          <span className="text-right">{t('price.timePrice')}</span>
        </div>
        {list.map((a) => {
          const it = items.find((x) => x.actionType === a)!
          const I = actionIcon(a)
          return (
            <div key={a} className="grid grid-cols-[1fr_130px_150px] items-center gap-3 border-b px-4 py-2.5 last:border-0">
              <span className="flex items-start gap-2 text-sm">
                <I className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                <span className="min-w-0">
                  <span className="block">{t(`actionType.${a}`)}</span>
                  {/* Đơn vị tính (như laoads.com): viễn thông có số lượng tính theo KIP / MB / SMS / phút được cộng */}
                  <span className="block text-xs text-muted-foreground">{t(`price.per.${a}`)}</span>
                </span>
              </span>
              {readOnly ? (
                <span className="text-right text-sm tabular">{fmtKip(it.unitPrice)}</span>
              ) : (
                <Input type="number" min={0} step="any" className="h-8 text-right" value={it.unitPrice} onChange={(e) => upd(a, { unitPrice: Number(e.target.value) })} aria-label={t('price.unitPrice')} />
              )}
              <div className="flex items-center justify-end gap-2">
                {readOnly ? (
                  <span className="text-sm tabular">{it.timePrice ? `${it.timePrice}${it.timePricePercentage ? '%' : ' ₭'}` : '—'}</span>
                ) : (
                  <>
                    <Input type="number" min={0} step="any" className="h-8 w-20 text-right" value={it.timePrice} onChange={(e) => upd(a, { timePrice: Number(e.target.value) })} aria-label={t('price.timePrice')} />
                    <label className="flex items-center gap-1 text-xs text-muted-foreground" title={t('price.percent')}>
                      <Switch checked={it.timePricePercentage} onCheckedChange={(v) => upd(a, { timePricePercentage: v })} />%
                    </label>
                  </>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )

  return (
    <>
      <div className="border-b px-6 py-5 pr-12">
        <DialogTitle>{readOnly ? quote?.name : editId ? t('price.editTitle') : t('price.newTitle')}</DialogTitle>
        <p className="mt-1 text-sm text-muted-foreground">{t('price.editorDesc')}</p>
      </div>
      <div className="grid flex-1 gap-6 overflow-y-auto px-6 py-5 scrollbar-thin">
        {!readOnly && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t('price.name')} required>
              <Input value={name} onChange={(e) => setName(e.target.value)} />
            </Field>
            <Field label={t('price.note')}>
              <Input value={note} onChange={(e) => setNote(e.target.value)} />
            </Field>
          </div>
        )}
        {/* Hai tab như laoads.com: "Thông báo" / "Dịch vụ viễn thông" */}
        <Tabs defaultValue="notify">
          <TabsList>
            <TabsTrigger value="notify">{t('price.notify')}</TabsTrigger>
            <TabsTrigger value="telecom">{t('price.telecom')}</TabsTrigger>
          </TabsList>
          <TabsContent value="notify" className="mt-4">
            {section(t('price.notify'), NOTIFICATION_ACTIONS)}
          </TabsContent>
          <TabsContent value="telecom" className="mt-4">
            <p className="mb-3 text-xs text-muted-foreground">{t('price.telecomHint')}</p>
            {section(t('price.telecom'), TELECOM_ACTIONS)}
          </TabsContent>
        </Tabs>
      </div>
      {!readOnly && (
        <div className="flex justify-end gap-2 border-t px-6 py-4">
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button
            loading={save.isPending}
            disabled={!name.trim()}
            onClick={() =>
              save.mutate(
                { id: editId, name: name.trim(), note, items },
                {
                  onSuccess: () => {
                    toast.success(t('common.saved'))
                    onClose()
                  },
                  onError: (e) => toast.error(errorMessage(e)),
                },
              )
            }
          >
            {t('price.saveDraft')}
          </Button>
        </div>
      )}
    </>
  )
}

export default function PriceQuotesPage() {
  const { t } = useTranslation()
  const isSa = useIsSuperAdmin()
  const [page, setPage] = React.useState(0)
  const list = usePriceQuotes({ page, size: 10 })
  const active = useActivePriceQuote()
  const lock = useLockPriceQuote()
  const del = useDeletePriceQuote()
  const [editor, setEditor] = React.useState<null | EditorState>(null)
  const [confirm, setConfirm] = React.useState<null | { kind: 'lock' | 'delete'; q: PriceQuote }>(null)

  const columns: Column<PriceQuote>[] = [
    { key: 'name', header: t('price.name'), cell: (q) => <div><div className="font-medium">{q.name}</div>{q.note && <div className="text-xs text-muted-foreground">{q.note}</div>}</div> },
    { key: 'status', header: t('common.status'), cell: (q) => <PriceStatusBadge status={q.status} /> },
    { key: 'sms', header: t('actionType.NOTIFICATION_SMS'), align: 'right', cell: (q) => <span className="tabular">{fmtKip(q.items.find((i) => i.actionType === 'NOTIFICATION_SMS')?.unitPrice)}</span> },
    { key: 'wa', header: t('actionType.NOTIFICATION_WHATSAPP'), align: 'right', cell: (q) => <span className="tabular">{fmtKip(q.items.find((i) => i.actionType === 'NOTIFICATION_WHATSAPP')?.unitPrice)}</span> },
    { key: 'locked', header: t('price.lockedAt'), cell: (q) => <span className="text-[13px] tabular">{q.lockedAt ? `${fmtDateTime(q.lockedAt)} · ${q.lockedBy}` : '—'}</span> },
    { key: 'updated', header: t('common.updatedAt'), cell: (q) => <span className="text-[13px] tabular">{fmtDateTime(q.updatedAt ?? q.createdAt)}</span> },
    {
      key: 'menu',
      header: '',
      width: '48px',
      cell: (q) => (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon-sm" aria-label={t('common.actions')} onClick={(e) => e.stopPropagation()}>
              <MoreHorizontal />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" onClick={(e) => e.stopPropagation()}>
            <DropdownMenuItem onSelect={() => setEditor({ quote: q, readOnly: true })}>
              <Eye />
              {t('common.view')}
            </DropdownMenuItem>
            {isSa && q.status === 'DRAFT' && (
              <DropdownMenuItem onSelect={() => setEditor({ quote: q, editId: q.id, readOnly: false })}>
                <Pencil />
                {t('common.edit')}
              </DropdownMenuItem>
            )}
            {isSa && (
              <DropdownMenuItem onSelect={() => setEditor({ quote: q, readOnly: false, clone: true })}>
                <Copy />
                {t('price.clone')}
              </DropdownMenuItem>
            )}
            {isSa && q.status === 'DRAFT' && (
              <>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={() => setConfirm({ kind: 'lock', q })}>
                  <Lock />
                  {t('price.lock')}
                </DropdownMenuItem>
                <DropdownMenuItem variant="destructive" onSelect={() => setConfirm({ kind: 'delete', q })}>
                  <Trash2 />
                  {t('common.delete')}
                </DropdownMenuItem>
              </>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        icon={<Wallet />}
        title={t('nav.priceQuotes')}
        description={t('price.desc')}
        actions={
          isSa && (
            <Button onClick={() => setEditor({ quote: null, readOnly: false })}>
              <Plus />
              {t('price.create')}
            </Button>
          )
        }
      />
      {active.data && (
        <div className="mb-6 flex flex-wrap items-center gap-4 rounded-xl border border-success/25 bg-success-soft/60 px-5 py-4">
          <span className="grid size-10 place-items-center rounded-xl bg-card text-success shadow-xs">
            <Lock className="size-5" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="text-xs text-muted-foreground">{t('price.activeNow')}</div>
            <div className="font-semibold">{active.data.name}</div>
          </div>
          <div className="flex gap-6 text-sm">
            {(['NOTIFICATION_SMS', 'NOTIFICATION_WHATSAPP', 'ADD_DATA'] as ActionType[]).map((a) => (
              <div key={a}>
                <div className="text-xs text-muted-foreground">{t(`actionType.${a}`)}</div>
                <div className="font-semibold tabular">{fmtKip(active.data!.items.find((i) => i.actionType === a)?.unitPrice)}</div>
                <div className="text-[11px] text-muted-foreground">{t(`price.per.${a}`)}</div>
              </div>
            ))}
          </div>
          <Button variant="outline" size="sm" onClick={() => setEditor({ quote: active.data!, readOnly: true })}>
            {t('common.viewAll')}
          </Button>
        </div>
      )}
      <DataTable
        columns={columns}
        rows={list.data?.items}
        rowKey={(q) => q.id}
        loading={list.isLoading}
        meta={list.data?.meta}
        onPageChange={setPage}
        onRowClick={(q) => (isSa && q.status === 'DRAFT' ? setEditor({ quote: q, editId: q.id, readOnly: false }) : setEditor({ quote: q, readOnly: true }))}
      />

      <Dialog open={!!editor} onOpenChange={(o) => !o && setEditor(null)}>
        <SheetContent className="max-w-2xl" aria-describedby={undefined}>
          {editor && <Editor key={`${editor.quote?.id ?? 'new'}-${editor.editId ?? ''}-${editor.clone ? 'c' : ''}`} {...editor} onClose={() => setEditor(null)} />}
        </SheetContent>
      </Dialog>

      <ConfirmDialog
        open={!!confirm}
        onOpenChange={(o) => !o && setConfirm(null)}
        destructive={confirm?.kind === 'delete'}
        title={confirm?.kind === 'lock' ? t('price.lockTitle') : t('price.deleteTitle')}
        description={confirm?.kind === 'lock' ? t('price.lockDesc', { name: confirm.q.name }) : t('price.deleteDesc', { name: confirm?.q.name })}
        confirmLabel={confirm?.kind === 'lock' ? t('price.lock') : t('common.delete')}
        loading={lock.isPending || del.isPending}
        onConfirm={() => {
          if (!confirm) return
          const m = confirm.kind === 'lock' ? lock : del
          m.mutate(confirm.q.id, {
            onSuccess: () => {
              toast.success(confirm.kind === 'lock' ? t('price.locked') : t('common.deleted'))
              setConfirm(null)
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }}
      />
    </>
  )
}
