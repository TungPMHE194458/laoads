import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Calculator, FileSpreadsheet, Filter, Plus, Trash2, UploadCloud } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { SegmentCondition } from '@/api/types'
import { useCreateSegment, useEstimateSegment, useSegmentAttributes } from '@/features/campaign/api'
import { Field } from '@/components/common/dialogs'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/misc'
import { fmtNumber } from '@/lib/format'
import { cn } from '@/lib/utils'

const OPS: SegmentCondition['operator'][] = ['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'BETWEEN', 'IN', 'IS_NULL']

export function CreateSegmentDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const { t } = useTranslation()
  const attrs = useSegmentAttributes()
  const estimate = useEstimateSegment()
  const create = useCreateSegment()
  const [tab, setTab] = React.useState<'QUERY' | 'FILE'>('QUERY')
  const [name, setName] = React.useState('')
  const [desc, setDesc] = React.useState('')
  const [conds, setConds] = React.useState<SegmentCondition[]>([{ field: 'act_status', operator: 'EQ', value: '00', join: 'AND' }])
  const [file, setFile] = React.useState<File | null>(null)
  const [drag, setDrag] = React.useState(false)
  const [err, setErr] = React.useState<string | null>(null)
  // State form được làm mới mỗi lần mở: SegmentsPage chỉ mount dialog khi open=true

  const upd = (i: number, p: Partial<SegmentCondition>) => {
    setConds((c) => c.map((x, j) => (j === i ? { ...x, ...p } : x)))
    estimate.reset()
  }
  const attrOf = (f: string) => attrs.data?.find((a) => a.field === f)

  const pickFile = (f: File | undefined) => {
    if (!f) return
    if (!/\.(xlsx|xls|csv)$/i.test(f.name)) return setErr(t('segment.fileType'))
    if (f.size > 20 * 1024 * 1024) return setErr(t('segment.fileSize'))
    setErr(null)
    setFile(f)
    if (!name) setName(f.name.replace(/\.[^.]+$/, ''))
  }

  const submit = () => {
    if (!name.trim()) return setErr(t('segment.nameRequired'))
    if (tab === 'QUERY' && !conds.every((c) => c.field && (c.operator === 'IS_NULL' || c.value !== ''))) return setErr(t('segment.condInvalid'))
    if (tab === 'FILE' && !file) return setErr(t('segment.fileRequired'))
    create.mutate(
      tab === 'QUERY'
        ? { name: name.trim(), description: desc, type: 'QUERY', conditions: conds }
        : { name: name.trim(), description: desc, type: 'FILE', fileName: file!.name, rowCount: Math.max(1, Math.round(file!.size / 48)) },
      {
        onSuccess: () => {
          toast.success(t('segment.created'))
          onOpenChange(false)
        },
        onError: (e) => setErr(errorMessage(e)),
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>{t('segment.create')}</DialogTitle>
          <DialogDescription>{t('segment.createDesc')}</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 md:grid-cols-2">
          <Field label={t('segment.name')} required>
            <Input value={name} onChange={(e) => setName(e.target.value)} maxLength={120} />
          </Field>
          <Field label={t('segment.description')}>
            <Input value={desc} onChange={(e) => setDesc(e.target.value)} />
          </Field>
        </div>
        <Tabs value={tab} onValueChange={(v) => setTab(v as 'QUERY' | 'FILE')}>
          <TabsList>
            <TabsTrigger value="QUERY">
              <Filter />
              {t('segment.fromAttributes')}
            </TabsTrigger>
            <TabsTrigger value="FILE">
              <FileSpreadsheet />
              {t('segment.fromFile')}
            </TabsTrigger>
          </TabsList>
          <TabsContent value="QUERY" className="mt-4">
            <div className="rounded-xl border bg-muted/30 p-3">
              <div className="mb-2 px-1 text-xs text-muted-foreground">{t('segment.sourceNote')}</div>
              <div className="grid gap-2">
                {conds.map((c, i) => {
                  const a = attrOf(c.field)
                  return (
                    <div key={i} className="grid grid-cols-[72px_1fr_120px_1fr_36px] items-center gap-2">
                      {i === 0 ? (
                        <span className="text-center text-xs font-medium text-muted-foreground">{t('segment.where')}</span>
                      ) : (
                        <Select size="sm" value={c.join} onValueChange={(v) => upd(i, { join: v as 'AND' | 'OR' })} options={[{ value: 'AND', label: 'AND' }, { value: 'OR', label: 'OR' }]} />
                      )}
                      <Select size="sm" value={c.field} onValueChange={(v) => upd(i, { field: v, value: '' })} options={(attrs.data ?? []).map((x) => ({ value: x.field, label: t(`attr.${x.field}`, { defaultValue: x.field }) }))} />
                      <Select size="sm" value={c.operator} onValueChange={(v) => upd(i, { operator: v as SegmentCondition['operator'] })} options={OPS.map((o) => ({ value: o, label: t(`op.${o}`) }))} />
                      {c.operator === 'IS_NULL' ? (
                        <span />
                      ) : a?.options || a?.dataType === 'BOOLEAN' ? (
                        <Select size="sm" value={c.value} onValueChange={(v) => upd(i, { value: v })} placeholder={t('common.choose')} options={(a.dataType === 'BOOLEAN' ? ['true', 'false'] : a.options!).map((o) => ({ value: o, label: o }))} />
                      ) : c.operator === 'BETWEEN' ? (
                        <div className="flex gap-1.5">
                          <Input className="h-8" type="number" step="any" value={c.value} onChange={(e) => upd(i, { value: e.target.value })} />
                          <Input className="h-8" type="number" step="any" value={c.valueTo ?? ''} onChange={(e) => upd(i, { valueTo: e.target.value })} />
                        </div>
                      ) : (
                        <Input className="h-8" type={a?.dataType === 'NUMBER' ? 'number' : 'text'} step="any" value={c.value} onChange={(e) => upd(i, { value: e.target.value })} />
                      )}
                      <Button variant="ghost" size="icon-sm" disabled={conds.length === 1} onClick={() => setConds((x) => x.filter((_, j) => j !== i))} aria-label={t('common.delete')}>
                        <Trash2 />
                      </Button>
                    </div>
                  )
                })}
              </div>
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <Button variant="ghost" size="sm" onClick={() => setConds((c) => [...c, { field: 'g_volume', operator: 'GT', value: '', join: 'AND' }])}>
                  <Plus />
                  {t('segment.addCondition')}
                </Button>
                <div className="ml-auto flex items-center gap-3">
                  {estimate.data && (
                    <span className="text-sm">
                      {t('segment.estimated')}: <b className="tabular">{fmtNumber(estimate.data.estimatedCount)}</b>
                    </span>
                  )}
                  <Button variant="outline" size="sm" loading={estimate.isPending} onClick={() => estimate.mutate(conds)}>
                    <Calculator />
                    {t('segment.estimate')}
                  </Button>
                </div>
              </div>
            </div>
          </TabsContent>
          <TabsContent value="FILE" className="mt-4">
            <label
              onDragOver={(e) => {
                e.preventDefault()
                setDrag(true)
              }}
              onDragLeave={() => setDrag(false)}
              onDrop={(e) => {
                e.preventDefault()
                setDrag(false)
                pickFile(e.dataTransfer.files[0])
              }}
              className={cn('flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed px-6 py-10 text-center transition', drag ? 'border-brand-500 bg-accent' : 'hover:border-brand-300 hover:bg-muted/40')}
            >
              <UploadCloud className="size-8 text-brand-500" />
              <span className="text-sm font-medium">{file ? file.name : t('segment.dropFile')}</span>
              <span className="text-xs text-muted-foreground">{t('segment.fileRule')}</span>
              <input type="file" accept=".xlsx,.xls,.csv" className="sr-only" onChange={(e) => pickFile(e.target.files?.[0])} />
            </label>
          </TabsContent>
        </Tabs>
        {err && <p className="rounded-lg border border-danger/20 bg-danger-soft px-3 py-2 text-sm text-danger">{err}</p>}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} loading={create.isPending}>
            {t('segment.createBtn')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
