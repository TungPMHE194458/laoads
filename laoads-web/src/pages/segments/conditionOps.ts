import type { SegmentCondition } from '@/api/types'

/** Toán tử theo kiểu thuộc tính — khớp BE SegmentQueryBuilder (CONTAINS = "chứa", chỉ cho chuỗi; SRS 3.5.1.2). */
export const OPS_BY_TYPE: Record<string, SegmentCondition['operator'][]> = {
  NUMBER: ['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'BETWEEN', 'IN', 'IS_NULL'],
  STRING: ['EQ', 'NEQ', 'IN', 'CONTAINS', 'IS_NULL'],
  BOOLEAN: ['EQ', 'NEQ', 'IS_NULL'],
}

/** Điều kiện đủ để gửi lên BE (có thuộc tính; có giá trị trừ toán tử Rỗng). */
export const conditionComplete = (c: SegmentCondition) => !!c.field && (c.operator === 'IS_NULL' || c.value !== '')
