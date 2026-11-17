import { useTranslation } from 'react-i18next'
import { CheckCircle2, XCircle } from 'lucide-react'
import { Badge } from '@/components/ui/badge'

/** Trạng thái Thành công / Thất bại (SRS 3.1.8) — kèm icon, không chỉ dựa vào màu. */
export function AuditResultBadge({ result }: { result: string | null | undefined }) {
  const { t } = useTranslation()
  const failed = result === 'FAILURE'
  return (
    <Badge variant={failed ? 'danger' : 'success'}>
      {failed ? <XCircle className="size-3" /> : <CheckCircle2 className="size-3" />}
      {t(`auditResult.${failed ? 'FAILURE' : 'SUCCESS'}`)}
    </Badge>
  )
}
