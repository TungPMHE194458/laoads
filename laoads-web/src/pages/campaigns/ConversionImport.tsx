import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Upload } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { useImportConversions } from '@/features/campaign/api'
import { Button } from '@/components/ui/button'

/** Mục tiêu MANUAL_FILE (SRS 3.2.6.2): tải file danh sách số thuê bao đã chuyển đổi để ghi nhận kết quả. */
export function ConversionImport({ campaignId }: { campaignId: number }) {
  const { t } = useTranslation()
  const input = React.useRef<HTMLInputElement>(null)
  const upload = useImportConversions(campaignId)
  const pick = (f: File | undefined) => {
    if (!f) return
    upload.mutate(f, {
      onSuccess: (r) => toast.success(t('campaign.detail.conversionsImported', { matched: r.matched, rows: r.rows })),
      onError: (e) => toast.error(errorMessage(e)),
    })
    if (input.current) input.current.value = ''
  }
  return (
    <div className="flex flex-wrap items-center gap-3 rounded-lg border border-dashed px-3 py-2.5">
      <p className="flex-1 text-[13px] text-muted-foreground">{t('campaign.detail.conversionsHint')}</p>
      <Button size="sm" variant="outline" loading={upload.isPending} onClick={() => input.current?.click()}>
        <Upload />
        {t('campaign.detail.importConversions')}
      </Button>
      <input ref={input} type="file" accept=".xlsx,.xls,.csv" className="sr-only" onChange={(e) => pick(e.target.files?.[0])} />
    </div>
  )
}
