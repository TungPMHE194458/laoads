/*
 * Quy tắc Audience API (OCS Audience / BCCS3 Audience) dùng chung cho dialog tạo tệp KH và mock.
 * Nguồn: tài liệu "Tài liệu API tích hợp" + "Import vào Postman cho OCS Audience và BBCS3 Audience" (docs/requirements).
 */
import type { AudienceColumn, AudienceCondition, AudienceDataType, AudienceOperator, AudienceRequest, AudienceSource, SegmentType } from '@/api/types'

/** Trạng thái form dựng request (CreateSegmentDialog giữ, AudienceBuilder hiển thị). */
export interface AudienceDraft {
  source: AudienceSource | null
  table: string | null
  /** null = mặc định (chỉ cột số thuê bao) */
  selected: string[] | null
  /** null = gợi ý tự động theo tên cột (phone / isdn / msisdn) */
  phone: string | null
  conditions: AudienceCondition[]
}

export const EMPTY_DRAFT: AudienceDraft = { source: null, table: null, selected: null, phone: null, conditions: [] }

const PHONE_HINT = /(phone|isdn|msisdn|sub_?id|so_?tb)/i

/** Cột số thuê bao hiệu lực: chọn tay, hoặc gợi ý cột STRING có tên giống số thuê bao. */
export function effectivePhone(d: AudienceDraft, columns: AudienceColumn[] | undefined) {
  if (d.phone && columns?.some((c) => c.name === d.phone)) return d.phone
  return columns?.find((c) => c.dataType === 'STRING' && PHONE_HINT.test(c.name))?.name ?? null
}

/** Cột được lấy về hiệu lực: luôn gồm cột số thuê bao. */
export function effectiveSelected(d: AudienceDraft, columns: AudienceColumn[] | undefined) {
  const phone = effectivePhone(d, columns)
  const base = (d.selected ?? []).filter((n) => columns?.some((c) => c.name === n))
  return phone && !base.includes(phone) ? [phone, ...base] : base
}

/** Draft → request đúng contract (targetField chữ HOA, đúng 1 cột PHONE, logicalOperator cho mọi điều kiện trừ cuối). */
export function toAudienceRequest(d: AudienceDraft, columns: AudienceColumn[] | undefined): AudienceRequest {
  const phone = effectivePhone(d, columns)
  const selectedColumns = effectiveSelected(d, columns)
    .map((name) => columns?.find((c) => c.name === name))
    .filter((c): c is AudienceColumn => !!c)
    .map((c) => {
      let target = c.name === phone ? PHONE_TARGET : targetFieldOf(c.name)
      if (c.name !== phone && target === PHONE_TARGET) target = `${PHONE_TARGET}_RAW`
      return { columnName: c.name, dataType: c.dataType, targetField: target }
    })
  const conditions = d.conditions.map((c, i) => ({
    ...c,
    value: c.value.trim(),
    logicalOperator: i < d.conditions.length - 1 ? (c.logicalOperator ?? 'AND') : null,
  }))
  return { tableName: d.table ?? '', selectedColumns, conditions }
}

export const AUDIENCE_OPERATORS: AudienceOperator[] = ['IS', 'NOT', 'LIKE', 'NOT_LIKE', 'GREATER_THAN', 'GREATER_EQUAL_THAN', 'LESS_THAN', 'LESS_EQUAL_THAN']

/** Kiểu dữ liệu hợp lệ cho từng operator (bảng operator trong tài liệu). */
const VALID_TYPES: Record<AudienceOperator, AudienceDataType[]> = {
  IS: ['STRING', 'INT', 'LONG', 'DOUBLE', 'DATE', 'BOOLEAN'],
  NOT: ['STRING', 'INT', 'LONG', 'DOUBLE', 'DATE', 'BOOLEAN'],
  LIKE: ['STRING'],
  NOT_LIKE: ['STRING'],
  GREATER_THAN: ['INT', 'LONG', 'DOUBLE', 'DATE', 'STRING'],
  GREATER_EQUAL_THAN: ['INT', 'LONG', 'DOUBLE', 'DATE', 'STRING'],
  LESS_THAN: ['INT', 'LONG', 'DOUBLE', 'DATE', 'STRING'],
  LESS_EQUAL_THAN: ['INT', 'LONG', 'DOUBLE', 'DATE', 'STRING'],
}

/** Ký hiệu SQL tương ứng (hiển thị gọn trong danh sách điều kiện). */
export const OPERATOR_SYMBOL: Record<AudienceOperator, string> = {
  IS: '=',
  NOT: '≠',
  LIKE: '∋',
  NOT_LIKE: '∌',
  GREATER_THAN: '>',
  GREATER_EQUAL_THAN: '≥',
  LESS_THAN: '<',
  LESS_EQUAL_THAN: '≤',
}

export const PHONE_TARGET = 'PHONE'

export function operatorsFor(type: AudienceDataType): AudienceOperator[] {
  return AUDIENCE_OPERATORS.filter((o) => VALID_TYPES[o].includes(type))
}

export function operatorAllowed(op: AudienceOperator, type: AudienceDataType) {
  return VALID_TYPES[op]?.includes(type) ?? false
}

/** Ô nhập giá trị theo kiểu dữ liệu. */
export function inputKindOf(type: AudienceDataType): 'number' | 'date' | 'boolean' | 'text' {
  if (type === 'INT' || type === 'LONG' || type === 'DOUBLE') return 'number'
  if (type === 'DATE') return 'date'
  if (type === 'BOOLEAN') return 'boolean'
  return 'text'
}

/** Giá trị có hợp lệ với kiểu dữ liệu không (kiểm tra phía FE trước khi gọi verify). */
export function valueValid(type: AudienceDataType, value: string) {
  const v = value.trim()
  if (v === '') return false
  switch (type) {
    case 'INT':
    case 'LONG':
      return /^-?\d+$/.test(v)
    case 'DOUBLE':
      return /^-?\d+(\.\d+)?$/.test(v)
    case 'DATE':
      return /^\d{4}-\d{2}-\d{2}$/.test(v)
    case 'BOOLEAN':
      return v === 'true' || v === 'false'
    default:
      return true
  }
}

/** Tên trường đích mặc định: tên cột viết HOA, ký tự lạ thành "_". */
export function targetFieldOf(columnName: string) {
  return columnName.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '_')
}

/**
 * Lỗi kiểm tra cục bộ theo đúng field path của BE (request.selectedColumns, request.conditions[i].operator…).
 * Trả về map field → mã lỗi i18n (segment.aud.err.*).
 */
export function validateAudience(req: AudienceRequest): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!req.tableName) errors['request.tableName'] = 'table'
  const phones = req.selectedColumns.filter((c) => c.targetField === PHONE_TARGET)
  if (phones.length !== 1) errors['request.selectedColumns'] = 'phone'
  req.conditions.forEach((c: AudienceCondition, i) => {
    if (!c.columnName) errors[`request.conditions[${i}].columnName`] = 'column'
    else if (!operatorAllowed(c.operator, c.dataType)) errors[`request.conditions[${i}].operator`] = 'operator'
    else if (!valueValid(c.dataType, c.value)) errors[`request.conditions[${i}].value`] = 'value'
  })
  return errors
}

/** Loại tệp build lại được khi lỗi (POST /segments/{id}/retry): OCS chạy tiếp từ offset, BCCS3 / QUERY build lại. */
export const RETRYABLE_SEGMENT_TYPES: SegmentType[] = ['AUDIENCE', 'QUERY']
