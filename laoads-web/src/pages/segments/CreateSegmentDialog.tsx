import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Calculator, Download, FileSpreadsheet, Filter, ServerCog, UploadCloud } from 'lucide-react'
import { ApiError, errorMessage } from '@/api/client'
import type { Segment, SegmentCondition } from '@/api/types'
import {
  downloadSegmentTemplate,
  useAudienceColumns,
  useCreateAudienceSegment,
  useCreateSegment,
  useEstimateSegment,
  useVerifyAudience,
} from '@/features/campaign/api'
import { type AudienceDraft, EMPTY_DRAFT, toAudienceRequest, validateAudience } from '@/features/campaign/audience'
import { Field } from '@/components/common/dialogs'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/misc'
import { fmtNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import { AudienceBuilder } from './AudienceBuilder'
import { ConditionEditor } from './ConditionEditor'
import { conditionComplete } from './conditionOps'

type CreateTab = 'QUERY' | 'FILE' | 'AUDIENCE'

/** `onCreated`: gọi sau khi tạo thành công (wizard chiến dịch dùng để chọn ngay tệp vừa tạo — SRS 3.5.1). */
export function CreateSegmentDialog({ open, onOpenChange, onCreated }: { open: boolean; onOpenChange: (o: boolean) => void; onCreated?: (s: Segment) => void }) {
  const { t } = useTranslation()
  const estimate = useEstimateSegment()
  const create = useCreateSegment()
  const createAudience = useCreateAudienceSegment()
  const verify = useVerifyAudience()
  const [tab, setTab] = React.useState<CreateTab>('QUERY')
  const [aud, setAud] = React.useState<AudienceDraft>(EMPTY_DRAFT)
  // field path của BE → thông điệp đã dịch
  const [audErrors, setAudErrors] = React.useState<Record<string, string>>({})
  const audColumns = useAudienceColumns(aud.source, aud.table)
  const [name, setName] = React.useState('')
  const [desc, setDesc] = React.useState('')
  const [conds, setConds] = React.useState<SegmentCondition[]>([{ field: 'act_status', operator: 'EQ', value: '00', join: 'AND' }])
  const [file, setFile] = React.useState<File | null>(null)
  const [drag, setDrag] = React.useState(false)
  const [err, setErr] = React.useState<string | null>(null)
  // SRS 3.5.1.2 TH4: tệp ước tính 0 khách hàng → cảnh báo, bấm Tạo lần nữa để vẫn lưu
  const [emptyWarned, setEmptyWarned] = React.useState(false)
  // State form được làm mới mỗi lần mở: SegmentsPage chỉ mount dialog khi open=true


  const pickFile = (f: File | undefined) => {
    if (!f) return
    if (!/\.(xlsx|xls|csv)$/i.test(f.name)) return setErr(t('segment.fileType'))
    if (f.size > 20 * 1024 * 1024) return setErr(t('segment.fileSize'))
    setErr(null)
    setFile(f)
    if (!name) setName(f.name.replace(/\.[^.]+$/, ''))
  }

  const [downloading, setDownloading] = React.useState(false)
  const downloadTemplate = async () => {
    setDownloading(true)
    try {
      await downloadSegmentTemplate()
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setDownloading(false)
    }
  }

  const changeAud = (d: AudienceDraft) => {
    setAud(d)
    setAudErrors({})
    verify.reset()
    setErr(null)
  }
  const serverErrors = (e: unknown) =>
    e instanceof ApiError && e.errors.length ? Object.fromEntries(e.errors.map((x) => [x.field, x.message])) : null
  /** Kiểm tra cục bộ trước khi gọi BE; trả request nếu hợp lệ. */
  const audienceRequest = () => {
    if (!aud.source) {
      setErr(t('segment.aud.err.source'))
      return null
    }
    const req = toAudienceRequest(aud, audColumns.data)
    const local = validateAudience(req)
    if (Object.keys(local).length) {
      setAudErrors(Object.fromEntries(Object.entries(local).map(([f, code]) => [f, t(`segment.aud.err.${code}`)])))
      setErr(t('segment.aud.fixErrors'))
      return null
    }
    return { source: aud.source, request: req }
  }
  const runVerify = () => {
    setErr(null)
    const r = audienceRequest()
    if (!r) return
    verify.mutate(r, {
      onError: (e) => {
        const fe = serverErrors(e)
        if (fe) setAudErrors(fe)
        setErr(errorMessage(e))
      },
    })
  }

  const submit = () => {
    if (!name.trim()) return setErr(t('segment.nameRequired'))
    if (tab === 'AUDIENCE') {
      const r = audienceRequest()
      if (!r) return
      return createAudience.mutate(
        { name: name.trim(), description: desc.trim() || null, ...r },
        {
          onSuccess: (s) => {
            toast.success(t('segment.aud.created'))
            onCreated?.(s)
            onOpenChange(false)
          },
          onError: (e) => {
            const fe = serverErrors(e)
            if (fe) setAudErrors(fe)
            setErr(errorMessage(e))
          },
        },
      )
    }
    if (tab === 'QUERY' && !conds.every(conditionComplete)) return setErr(t('segment.condInvalid'))
    if (tab === 'QUERY' && !emptyWarned) {
      return estimate.mutate(conds, {
        onSuccess: (r) => {
          if (r.estimatedCount === 0) {
            setEmptyWarned(true)
            setErr(t('segment.emptyWarning'))
          } else doCreate()
        },
        onError: (e) => setErr(errorMessage(e)),
      })
    }
    doCreate()
  }

  const doCreate = () => {
    if (tab === 'FILE' && !file) return setErr(t('segment.fileRequired'))
    create.mutate(
      tab === 'QUERY'
        ? { name: name.trim(), description: desc, type: 'QUERY', conditions: conds }
        : { name: name.trim(), description: desc, type: 'FILE', file: file! },
      {
        onSuccess: (s) => {
          toast.success(t('segment.created'))
          if (s.invalidRows) toast.warning(t('segment.invalidRows', { n: s.invalidRows }))
          onCreated?.(s)
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
        <Tabs
          value={tab}
          onValueChange={(v) => {
            setTab(v as CreateTab)
            setErr(null)
          }}
        >
          <TabsList>
            <TabsTrigger value="QUERY">
              <Filter />
              {t('segment.fromAttributes')}
            </TabsTrigger>
            <TabsTrigger value="FILE">
              <FileSpreadsheet />
              {t('segment.fromFile')}
            </TabsTrigger>
            <TabsTrigger value="AUDIENCE">
              <ServerCog />
              {t('segment.aud.tab')}
            </TabsTrigger>
          </TabsList>
          <TabsContent value="QUERY" className="mt-4">
            <div className="rounded-xl border bg-muted/30 p-3">
              <div className="mb-2 px-1 text-xs text-muted-foreground">{t('segment.sourceNote')}</div>
              <ConditionEditor
                conditions={conds}
                onChange={(c) => {
                  setEmptyWarned(false)
                  setConds(c)
                  estimate.reset()
                }}
                footer={
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
                }
              />
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
            {/* Đặt ngoài <label>: bấm nút không được mở hộp chọn file */}
            <div className="mt-2 flex items-center justify-between gap-3 text-xs text-muted-foreground">
              <span>{t('segment.templateHint')}</span>
              <Button variant="outline" size="sm" loading={downloading} onClick={downloadTemplate}>
                <Download />
                {t('segment.downloadTemplate')}
              </Button>
            </div>
          </TabsContent>
          <TabsContent value="AUDIENCE" className="mt-4">
            <p className="mb-3 text-xs text-muted-foreground">{t('segment.aud.note')}</p>
            <AudienceBuilder draft={aud} onChange={changeAud} errors={audErrors} verifyResult={verify.data ?? null} verifying={verify.isPending} onVerify={runVerify} />
          </TabsContent>
        </Tabs>
        {err && <p className="rounded-lg border border-danger/20 bg-danger-soft px-3 py-2 text-sm text-danger">{err}</p>}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} loading={create.isPending || createAudience.isPending || (tab === 'QUERY' && estimate.isPending)}>
            {emptyWarned && tab === 'QUERY' ? t('segment.createAnyway') : t('segment.createBtn')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
