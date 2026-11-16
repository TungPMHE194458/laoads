/*
 * Dựng request Audience API (OCS / BCCS3) cho tệp KH loại AUDIENCE:
 * nguồn → bảng → cột lấy về (đúng 1 cột PHONE) → điều kiện lọc → kiểm tra (verify).
 * Component được điều khiển (state ở CreateSegmentDialog) để dialog gửi đúng request khi tạo tệp.
 */
import { useTranslation } from 'react-i18next'
import { CheckCircle2, CircleAlert, Database, Plus, ServerCog, ShieldCheck, Trash2 } from 'lucide-react'
import { RadioGroup as RadioPrimitive } from 'radix-ui'
import type { AudienceCondition, AudienceSource, AudienceVerifyResult } from '@/api/types'
import { AUDIENCE_SOURCES } from '@/api/types'
import { useAudienceColumns, useAudienceSources, useAudienceTables } from '@/features/campaign/api'
import { type AudienceDraft, effectivePhone, effectiveSelected, EMPTY_DRAFT, inputKindOf, operatorAllowed, operatorsFor } from '@/features/campaign/audience'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Checkbox, RadioCard, RadioGroup, Skeleton } from '@/components/ui/misc'
import { cn } from '@/lib/utils'

export function AudienceBuilder({
  draft,
  onChange,
  errors,
  verifyResult,
  verifying,
  onVerify,
}: {
  draft: AudienceDraft
  onChange: (d: AudienceDraft) => void
  errors: Record<string, string>
  verifyResult: AudienceVerifyResult | null
  verifying: boolean
  onVerify: () => void
}) {
  const { t } = useTranslation()
  const sources = useAudienceSources()
  const tables = useAudienceTables(draft.source)
  const columns = useAudienceColumns(draft.source, draft.table)
  const cols = columns.data
  const phone = effectivePhone(draft, cols)
  const selected = effectiveSelected(draft, cols)
  const set = (p: Partial<AudienceDraft>) => onChange({ ...draft, ...p })
  const colOf = (name: string) => cols?.find((c) => c.name === name)
  // errors: field path của BE → thông điệp đã dịch (lỗi cục bộ dịch sẵn ở dialog, lỗi BE dùng message của BE)
  const err = (field: string) => errors[field] ?? null
  const condErr = (i: number) =>
    ['columnName', 'operator', 'value', 'dataType', 'logicalOperator'].map((f) => err(`request.conditions[${i}].${f}`)).find(Boolean) ?? err(`request.conditions[${i}]`)

  const toggleColumn = (name: string, on: boolean) => {
    const next = on ? [...selected, name] : selected.filter((n) => n !== name)
    set({ selected: next })
  }
  const setCond = (i: number, p: Partial<AudienceCondition>) => set({ conditions: draft.conditions.map((c, j) => (j === i ? { ...c, ...p } : c)) })
  const changeCondColumn = (i: number, name: string) => {
    const col = colOf(name)
    if (!col) return
    const cur = draft.conditions[i]
    const op = operatorAllowed(cur.operator, col.dataType) ? cur.operator : operatorsFor(col.dataType)[0]
    setCond(i, { columnName: name, dataType: col.dataType, operator: op, value: '' })
  }
  const addCond = () => {
    const col = cols?.find((c) => c.name !== phone) ?? cols?.[0]
    if (!col) return
    set({ conditions: [...draft.conditions, { columnName: col.name, dataType: col.dataType, operator: operatorsFor(col.dataType)[0], logicalOperator: 'AND', value: '' }] })
  }

  return (
    <div className="grid gap-4">
      {/* 1. Nguồn */}
      <section className="grid gap-2">
        <h4 className="text-sm font-semibold">{t('segment.aud.step.source')}</h4>
        {sources.isLoading ? (
          <Skeleton className="h-20" />
        ) : (
          <RadioGroup
            value={draft.source ?? ''}
            onValueChange={(v) => onChange({ ...EMPTY_DRAFT, source: v as AudienceSource })}
            className="grid gap-3 sm:grid-cols-2"
          >
            {AUDIENCE_SOURCES.map((s) => {
              const info = sources.data?.find((x) => x.source === s)
              const configured = info?.configured ?? false
              return (
                <RadioCard
                  key={s}
                  value={s}
                  disabled={!configured}
                  icon={s === 'OCS' ? <ServerCog className="size-5" /> : <Database className="size-5" />}
                  title={
                    <span className="flex flex-wrap items-center gap-2">
                      {t(`segmentSource.${s}`)}
                      {configured && info?.mode === 'simulated' && <Badge variant="warning">{t('segment.aud.simulated')}</Badge>}
                      {!configured && <Badge variant="neutral">{t('segment.aud.notConfigured')}</Badge>}
                    </span>
                  }
                  description={t(`segment.aud.sourceDesc.${s}`)}
                />
              )
            })}
          </RadioGroup>
        )}
      </section>

      {draft.source && (
        /* 2. Bảng */
        <section className="grid gap-2">
          <h4 className="text-sm font-semibold">{t('segment.aud.step.table')}</h4>
          <Select
            value={draft.table ?? ''}
            onValueChange={(v) => onChange({ ...draft, table: v, selected: null, phone: null, conditions: [] })}
            placeholder={tables.isLoading ? t('common.loading') : t('segment.aud.tablePh')}
            disabled={tables.isLoading || !tables.data?.length}
            options={(tables.data ?? []).map((x) => ({ value: x, label: <span className="font-mono text-[13px]">{x}</span> }))}
          />
          {tables.isError && <p className="text-xs text-danger">{t('segment.aud.loadFailed')}</p>}
          {err('request.tableName') && <p className="text-xs text-danger">{err('request.tableName')}</p>}
        </section>
      )}

      {draft.table && (
        /* 3. Cột lấy về */
        <section className="grid gap-2">
          <div className="flex items-baseline justify-between gap-2">
            <h4 className="text-sm font-semibold">{t('segment.aud.step.columns')}</h4>
            <span className="text-xs text-muted-foreground">{t('segment.aud.columnsHint')}</span>
          </div>
          {columns.isLoading ? (
            <Skeleton className="h-32" />
          ) : columns.isError ? (
            <p className="text-xs text-danger">{t('segment.aud.loadFailed')}</p>
          ) : (
            <div className="max-h-56 overflow-y-auto rounded-xl border scrollbar-thin">
              <RadioGroup value={phone ?? ''} onValueChange={(v) => set({ phone: v, selected: selected.includes(v) ? selected : [...selected, v] })}>
                <table className="w-full text-sm">
                  <thead className="sticky top-0 bg-muted/80 text-xs text-muted-foreground backdrop-blur">
                    <tr>
                      <th className="w-10 px-3 py-2 text-left font-medium">{t('segment.aud.colTake')}</th>
                      <th className="px-3 py-2 text-left font-medium">{t('segment.aud.colName')}</th>
                      <th className="px-3 py-2 text-left font-medium">{t('segment.aud.colType')}</th>
                      <th className="px-3 py-2 text-left font-medium">{t('segment.aud.colPhone')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(cols ?? []).map((c) => {
                      const isPhone = c.name === phone
                      return (
                        <tr key={c.name} className={cn('border-t', isPhone && 'bg-accent/50')}>
                          <td className="px-3 py-1.5">
                            <Checkbox
                              checked={selected.includes(c.name)}
                              disabled={isPhone}
                              onCheckedChange={(v) => toggleColumn(c.name, v === true)}
                              aria-label={t('segment.aud.colTake') + ' ' + c.name}
                            />
                          </td>
                          <td className="px-3 py-1.5 font-mono text-[13px]">{c.name}</td>
                          <td className="px-3 py-1.5">
                            <Badge variant="outline" className="font-mono text-[11px]">
                              {c.dataType}
                            </Badge>
                          </td>
                          <td className="px-3 py-1.5">
                            <PhoneRadio value={c.name} label={t('segment.aud.colPhone') + ' ' + c.name} />
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </RadioGroup>
            </div>
          )}
          {err('request.selectedColumns') && <p className="text-xs text-danger">{err('request.selectedColumns')}</p>}
        </section>
      )}

      {draft.table && cols && cols.length > 0 && (
        /* 4. Điều kiện lọc */
        <section className="grid gap-2">
          <div className="flex items-baseline justify-between gap-2">
            <h4 className="text-sm font-semibold">{t('segment.aud.step.conditions')}</h4>
            <span className="text-xs text-muted-foreground">{t('segment.aud.conditionsHint')}</span>
          </div>
          <div className="rounded-xl border bg-muted/30 p-3">
            {draft.conditions.length === 0 && <p className="px-1 pb-2 text-xs text-muted-foreground">{t('segment.aud.noConditions')}</p>}
            <div className="grid gap-2">
              {draft.conditions.map((c, i) => {
                const kind = inputKindOf(c.dataType)
                const e = condErr(i)
                return (
                  <div key={i} className="grid gap-1">
                    <div className="grid grid-cols-[72px_minmax(0,1fr)_minmax(0,150px)_minmax(0,1fr)_36px] items-center gap-2">
                      {i === 0 ? (
                        <span className="text-center text-xs font-medium text-muted-foreground">{t('segment.where')}</span>
                      ) : (
                        <Select
                          size="sm"
                          value={draft.conditions[i - 1].logicalOperator ?? 'AND'}
                          onValueChange={(v) => setCond(i - 1, { logicalOperator: v as 'AND' | 'OR' })}
                          options={[{ value: 'AND', label: 'AND' }, { value: 'OR', label: 'OR' }]}
                          aria-label={t('segment.aud.join')}
                        />
                      )}
                      <Select
                        size="sm"
                        value={c.columnName}
                        onValueChange={(v) => changeCondColumn(i, v)}
                        options={cols.map((x) => ({ value: x.name, label: <span className="font-mono text-[12px]">{x.name}</span> }))}
                        aria-label={t('segment.aud.colName')}
                      />
                      <Select
                        size="sm"
                        value={c.operator}
                        onValueChange={(v) => setCond(i, { operator: v as AudienceCondition['operator'] })}
                        options={operatorsFor(c.dataType).map((o) => ({ value: o, label: t(`audienceOp.${o}`) }))}
                        aria-label={t('segment.aud.operator')}
                      />
                      {kind === 'boolean' ? (
                        <Select size="sm" value={c.value} onValueChange={(v) => setCond(i, { value: v })} placeholder={t('common.choose')} options={[{ value: 'true', label: 'true' }, { value: 'false', label: 'false' }]} />
                      ) : (
                        <Input
                          className="h-8"
                          type={kind === 'number' ? 'number' : kind === 'date' ? 'date' : 'text'}
                          step={c.dataType === 'DOUBLE' ? 'any' : kind === 'number' ? '1' : undefined}
                          value={c.value}
                          aria-invalid={!!e}
                          onChange={(ev) => setCond(i, { value: ev.target.value })}
                          placeholder={t('segment.aud.valuePh')}
                        />
                      )}
                      <Button variant="ghost" size="icon-sm" onClick={() => set({ conditions: draft.conditions.filter((_, j) => j !== i) })} aria-label={t('common.delete')}>
                        <Trash2 />
                      </Button>
                    </div>
                    {e && <p className="pl-[80px] text-xs text-danger">{e}</p>}
                  </div>
                )
              })}
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <Button variant="ghost" size="sm" onClick={addCond}>
                <Plus />
                {t('segment.addCondition')}
              </Button>
              <Button variant="outline" size="sm" className="ml-auto" loading={verifying} onClick={onVerify}>
                <ShieldCheck />
                {t('segment.aud.verify')}
              </Button>
            </div>
          </div>
          {verifyResult && (
            <div
              role="status"
              className={cn(
                'flex items-start gap-2 rounded-lg border px-3 py-2 text-sm',
                verifyResult.valid ? 'border-success/25 bg-success-soft text-success' : 'border-danger/20 bg-danger-soft text-danger',
              )}
            >
              {verifyResult.valid ? <CheckCircle2 className="mt-0.5 size-4 shrink-0" /> : <CircleAlert className="mt-0.5 size-4 shrink-0" />}
              <span>
                {verifyResult.valid ? t('segment.aud.valid') : t('segment.aud.invalid')}
                {verifyResult.message && <span className="block text-xs opacity-90">{verifyResult.message}</span>}
              </span>
            </div>
          )}
          {err('request') && <p className="text-xs text-danger">{err('request')}</p>}
        </section>
      )}
    </div>
  )
}

/** Nút radio nhỏ chọn cột số thuê bao (dùng chung RadioGroup bao quanh bảng cột). */
function PhoneRadio({ value, label }: { value: string; label: string }) {
  return (
    <RadioPrimitive.Item
      value={value}
      aria-label={label}
      className="grid size-4 cursor-pointer place-items-center rounded-full border border-input bg-card outline-none transition focus-visible:ring-[3px] focus-visible:ring-ring data-[state=checked]:border-brand-500"
    >
      <RadioPrimitive.Indicator className="size-2 rounded-full bg-brand-500" />
    </RadioPrimitive.Item>
  )
}
