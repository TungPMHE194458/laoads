import { useTranslation } from 'react-i18next'
import { Plus, Trash2 } from 'lucide-react'
import type { SegmentCondition } from '@/api/types'
import { useSegmentAttributes } from '@/features/campaign/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { OPS_BY_TYPE } from './conditionOps'

/**
 * Soạn danh sách điều kiện trên thuộc tính thuê bao (dữ liệu BI, LADS_SUBSCRIBER): AND / OR giữa các dòng,
 * toán tử theo kiểu thuộc tính, giá trị chọn từ danh mục hoặc nhập; "Rỗng" (IS_NULL) thay cho giá trị.
 * Dùng cho tệp khách hàng từ thuộc tính (SRS 3.5.1.2) và mục tiêu chuyển đổi từ nguồn dữ liệu ngoài (SRS 3.2.6.1).
 */
export function ConditionEditor({
  conditions,
  onChange,
  footer,
  allowEmpty = false,
}: {
  conditions: SegmentCondition[]
  onChange: (c: SegmentCondition[]) => void
  footer?: React.ReactNode
  /** cho phép xoá hết điều kiện (mục tiêu: chưa cấu hình) */
  allowEmpty?: boolean
}) {
  const { t } = useTranslation()
  const attrs = useSegmentAttributes()
  const attrOf = (f: string) => attrs.data?.find((a) => a.field === f)
  const upd = (i: number, p: Partial<SegmentCondition>) => onChange(conditions.map((c, j) => (j === i ? { ...c, ...p } : c)))
  return (
    <>
      <div className="grid gap-2">
        {conditions.map((c, i) => {
          const a = attrOf(c.field)
          return (
            <div key={i} className="grid grid-cols-[72px_1fr_120px_1fr_36px] items-center gap-2">
              {i === 0 ? (
                <span className="text-center text-xs font-medium text-muted-foreground">{t('segment.where')}</span>
              ) : (
                <Select size="sm" value={c.join} onValueChange={(v) => upd(i, { join: v as 'AND' | 'OR' })} options={[{ value: 'AND', label: 'AND' }, { value: 'OR', label: 'OR' }]} />
              )}
              <Select
                size="sm"
                value={c.field}
                onValueChange={(v) => {
                  const ops = OPS_BY_TYPE[attrOf(v)?.dataType ?? 'NUMBER']
                  upd(i, { field: v, value: '', operator: ops.includes(c.operator) ? c.operator : ops[0] })
                }}
                options={(attrs.data ?? []).map((x) => ({ value: x.field, label: t(`attr.${x.field}`, { defaultValue: x.field }) }))}
              />
              <Select size="sm" value={c.operator} onValueChange={(v) => upd(i, { operator: v as SegmentCondition['operator'] })} options={OPS_BY_TYPE[a?.dataType ?? 'NUMBER'].map((o) => ({ value: o, label: t(`op.${o}`) }))} />
              {c.operator === 'IS_NULL' ? (
                <span />
              ) : c.operator !== 'CONTAINS' && (a?.options || a?.dataType === 'BOOLEAN') ? (
                <Select size="sm" value={c.value} onValueChange={(v) => upd(i, { value: v })} placeholder={t('common.choose')} options={(a.dataType === 'BOOLEAN' ? ['true', 'false'] : a.options!).map((o) => ({ value: o, label: o }))} />
              ) : c.operator === 'BETWEEN' ? (
                <div className="flex gap-1.5">
                  <Input className="h-8" type="number" step="any" value={c.value} onChange={(e) => upd(i, { value: e.target.value })} />
                  <Input className="h-8" type="number" step="any" value={c.valueTo ?? ''} onChange={(e) => upd(i, { valueTo: e.target.value })} />
                </div>
              ) : (
                <Input className="h-8" type={a?.dataType === 'NUMBER' ? 'number' : 'text'} step="any" value={c.value} onChange={(e) => upd(i, { value: e.target.value })} />
              )}
              <Button variant="ghost" size="icon-sm" disabled={!allowEmpty && conditions.length === 1} onClick={() => onChange(conditions.filter((_, j) => j !== i))} aria-label={t('common.delete')}>
                <Trash2 />
              </Button>
            </div>
          )
        })}
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <Button variant="ghost" size="sm" onClick={() => onChange([...conditions, { field: 'g_volume', operator: 'GT', value: '', join: 'AND' }])}>
          <Plus />
          {t('segment.addCondition')}
        </Button>
        {footer}
      </div>
    </>
  )
}
